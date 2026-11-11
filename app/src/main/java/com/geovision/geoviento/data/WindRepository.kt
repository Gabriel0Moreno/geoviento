package com.geovision.geoviento.data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Estima la ráfaga a una altura a partir de la ráfaga a 10 m. */
fun estimateGust(gust10: Double, speed10: Double, speedAtHeight: Double?): Double? {
  if (speedAtHeight == null || speed10 <= 0.0) return gust10
  val g = gust10 * (speedAtHeight / speed10)
  return if (g.isFinite()) g else gust10
}

private fun safe(v: Double?, fallback: Double = 0.0): Double = if (v == null || !v.isFinite()) fallback else v

class WindRepository {
  // Timeout explícito: un cuelgue de red termina en error visible (15 s),
  // nunca en "Cargando…" para siempre.
  private val http = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()
  private fun retrofit(base: String) = Retrofit.Builder().baseUrl(base)
    .client(http).addConverterFactory(GsonConverterFactory.create()).build()
  private val api = retrofit("https://api.open-meteo.com/").create(OpenMeteoApi::class.java)
  private val archiveApi = retrofit("https://archive-api.open-meteo.com/").create(OpenMeteoArchiveApi::class.java)
  private val airApi = retrofit("https://air-quality-api.open-meteo.com/").create(AirQualityApi::class.java)
  private val geoApi = retrofit("https://geocoding-api.open-meteo.com/").create(GeocodingApi::class.java)
  private val metNo = retrofit("https://api.met.no/").create(MetNoApi::class.java)

  // Diagnóstico de la última carga / prueba, para mostrarlo en la app.
  private val diagLock = Any()
  private val _diag = mutableListOf<DiagEntry>()
  val diag: List<DiagEntry> get() = synchronized(diagLock) { _diag.toList() }
  private fun addDiag(e: DiagEntry) = synchronized(diagLock) { _diag.add(e) }
  private fun clearDiag() = synchronized(diagLock) { _diag.clear() }

  // Open-Meteo (principal, datos completos) y met.no (respaldo) se prueban EN
  // PARALELO y se espera a los dos: si Open-Meteo responde se usa él (trae
  // viento a 80/120 m y ráfagas medidas); solo si falla se usa met.no.
  // La calidad del aire se pide en paralelo y se anexa si responde.
  // Toda la red corre en Dispatchers.IO, nunca en el hilo principal.
  suspend fun now(lat: Double, lon: Double): WindReport = withContext(Dispatchers.IO) {
    clearDiag()
    coroutineScope {
      val omD = async {
        val t0 = System.currentTimeMillis()
        val r = runCatching { openMeteoNow(lat, lon) }
        val ms = System.currentTimeMillis() - t0
        r.onSuccess { addDiag(DiagEntry("Open-Meteo", true, "respondió bien", ms)) }
          .onFailure { addDiag(DiagEntry("Open-Meteo", false, it.message ?: "error sin mensaje", ms)) }
        r
      }
      val metD = async {
        val t0 = System.currentTimeMillis()
        val r = runCatching { metNoNow(lat, lon) }
        val ms = System.currentTimeMillis() - t0
        r.onSuccess { addDiag(DiagEntry("met.no", true, "respondió bien (respaldo)", ms)) }
          .onFailure { addDiag(DiagEntry("met.no", false, it.message ?: "error sin mensaje", ms)) }
        r
      }
      val aqD = async {
        runCatching {
          val a = airApi.airQuality(lat, lon).current
          AirQualityNow(a?.us_aqi, a?.pm2_5)
        }.getOrNull()
      }
      val om = omD.await()
      val met = metD.await()
      val air = aqD.await()
      val base = if (om.isSuccess) om.getOrThrow()
        else if (met.isSuccess) met.getOrThrow()
        else throw IllegalStateException(
          "Open-Meteo falló (${om.exceptionOrNull()?.message}); " +
            "el respaldo met.no tampoco respondió (${met.exceptionOrNull()?.message})"
        )
      base.copy(air = air)
    }
  }

  // Prueba directa de conexión a las dos fuentes (sin Retrofit), para el
  // botón "Probar conexión" del diagnóstico.
  suspend fun testConnection(lat: Double, lon: Double): List<DiagEntry> = withContext(Dispatchers.IO) {
    val out = mutableListOf<DiagEntry>()
    out.add(probe("Open-Meteo", "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m&wind_speed_unit=ms", null))
    out.add(probe("met.no", "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$lat&lon=$lon", "GeovientoMx/1.1.0 (github.com/Gabriel0Moreno/geoviento)"))
    out
  }

  private fun probe(source: String, urlStr: String, userAgent: String?): DiagEntry {
    val t0 = System.currentTimeMillis()
    return try {
      val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000; readTimeout = 10_000
        requestMethod = "GET"
        if (userAgent != null) setRequestProperty("User-Agent", userAgent)
      }
      val code = conn.responseCode
      conn.disconnect()
      val ms = System.currentTimeMillis() - t0
      if (code in 200..299) DiagEntry(source, true, "HTTP $code", ms)
      else DiagEntry(source, false, "HTTP $code", ms)
    } catch (e: Exception) {
      DiagEntry(source, false, e.javaClass.simpleName + ": " + (e.message ?: "sin mensaje"), System.currentTimeMillis() - t0)
    }
  }

  // Informe de un día: hoy con el pronóstico, días pasados con el archivo ERA5.
  suspend fun dayReport(lat: Double, lon: Double, date: LocalDate): DayReport = withContext(Dispatchers.IO) {
    val today = LocalDate.now(ZoneId.of("America/Mexico_City"))
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    if (date == today) {
      val r = api.forecast(lat, lon)
      val h = r.hourly
      val times = h?.time ?: emptyList()
      val dayStr = date.format(fmt)
      val idx = times.indices.filter { times[it].startsWith(dayStr) }
      val hourly = if (h != null) idx.map { buildHourly(h, it) } else emptyList()
      val max = hourly.maxByOrNull { it.gust10 }
      DayReport(dayStr, hourly, max?.gust10 ?: 0.0, max?.time, hourly.firstOrNull()?.dir10, "Open-Meteo (pronóstico)")
    } else {
      val ds = date.format(fmt)
      val a = archiveApi.archive(lat, lon, ds, ds)
      val h = a.hourly
      val times = h?.time ?: emptyList()
      val hourly = if (h != null) times.indices.map { buildArchiveHourly(h, it) } else emptyList()
      val max = hourly.maxByOrNull { it.gust10 }
      val dom = a.daily?.wind_direction_10m_dominant?.firstOrNull()
      DayReport(ds, hourly, max?.gust10 ?: 0.0, max?.time, dom, "Open-Meteo (archivo ERA5)")
    }
  }

  // Referencia rápida del archivo: ráfaga máxima diaria de los últimos 7 días
  // (mín, máx y rango de dirección dominante). Devuelve (min, max, dirMin, dirMax).
  suspend fun weekReference(lat: Double, lon: Double): List<Double> = withContext(Dispatchers.IO) {
    val today = LocalDate.now(ZoneId.of("America/Mexico_City"))
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val end = today.minusDays(1).format(fmt)
    val start = today.minusDays(7).format(fmt)
    return@withContext try {
      val a = archiveApi.archive(lat, lon, start, end)
      val gusts = a.daily?.wind_gusts_10m_max?.filterNotNull() ?: emptyList()
      val dirs = a.daily?.wind_direction_10m_dominant?.filterNotNull() ?: emptyList()
      if (gusts.isEmpty()) emptyList()
      else listOf(gusts.min(), gusts.max(), dirs.minOrNull() ?: 0.0, dirs.maxOrNull() ?: 0.0)
    } catch (_: Exception) {
      emptyList()
    }
  }

  // Buscar un lugar por nombre (geocodificación Open-Meteo).
  suspend fun searchPlace(query: String): List<PlaceResult> = withContext(Dispatchers.IO) {
    try {
      val res = geoApi.search(query).results ?: emptyList()
      res.mapNotNull {
        val la = it.latitude ?: return@mapNotNull null
        val lo = it.longitude ?: return@mapNotNull null
        val detail = listOfNotNull(it.admin1, it.country).joinToString(", ")
        PlaceResult(it.name ?: "Lugar", detail, la, lo)
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun buildHourly(h: OmHourly, i: Int): HourlyPoint {
    val times = h.time ?: emptyList()
    val s120 = h.wind_speed_120m?.getOrNull(i)
    val sp10 = safe(h.wind_speed_10m?.getOrNull(i))
    val gu10 = safe(h.wind_gusts_10m?.getOrNull(i))
    return HourlyPoint(times.getOrNull(i) ?: "", sp10, gu10, s120, estimateGust(gu10, sp10, s120), safe(h.wind_direction_10m?.getOrNull(i)), h.temperature_2m?.getOrNull(i), h.precipitation_probability?.getOrNull(i))
  }

  private fun buildArchiveHourly(h: OmArchiveHourly, i: Int): HourlyPoint {
    val times = h.time ?: emptyList()
    val sp10 = safe(h.wind_speed_10m?.getOrNull(i))
    val gu10 = safe(h.wind_gusts_10m?.getOrNull(i))
    return HourlyPoint(times.getOrNull(i) ?: "", sp10, gu10, null, null, safe(h.wind_direction_10m?.getOrNull(i)), h.temperature_2m?.getOrNull(i), null)
  }

  private suspend fun openMeteoNow(lat: Double, lon: Double): WindReport {
    val r = api.forecast(lat, lon)
    val c = r.current ?: throw IllegalStateException("Open-Meteo sin datos actuales")
    val h = r.hourly
    val d = r.daily
    // La ventana de 24 h empieza en la hora actual, no a medianoche.
    val times = h?.time ?: emptyList()
    val nowStr = c.time ?: ""
    val startIdx = times.indexOfFirst { it >= nowStr }.takeIf { it >= 0 } ?: 0
    val n = minOf(times.size, h?.wind_speed_10m?.size ?: 0, h?.wind_gusts_10m?.size ?: 0)
    val hourly = (startIdx until minOf(n, startIdx + 24)).map { buildHourly(h!!, it) }
    val speed10 = safe(c.wind_speed_10m)
    val gust10 = safe(c.wind_gusts_10m)
    val gustMaxToday = d?.wind_gusts_10m_max?.firstOrNull()
    val gustMaxHour = hourly.maxByOrNull { it.gust10 }?.time
    val precipNow = h?.precipitation_probability?.getOrNull(startIdx)
    val surface = SurfaceNow(
      c.relative_humidity_2m, c.surface_pressure, c.uv_index,
      d?.sunrise?.firstOrNull(), d?.sunset?.firstOrNull(),
      d?.temperature_2m_max?.firstOrNull(), gustMaxToday, gustMaxHour,
      precipNow, r.elevation
    )
    return WindReport(
      WindNow(speed10, safe(c.wind_direction_10m), gust10, c.wind_speed_80m, c.wind_direction_80m, c.wind_speed_120m, c.wind_direction_120m, estimateGust(gust10, speed10, c.wind_speed_120m), c.temperature_2m, c.time),
      hourly, "Open-Meteo", lat, lon, surface = surface
    )
  }

  private suspend fun metNoNow(lat: Double, lon: Double): WindReport {
    val series = metNo.forecast(lat, lon).properties?.timeseries
      ?: throw IllegalStateException("met.no sin datos")
    val first = series.firstOrNull()?.data?.instant?.details
      ?: throw IllegalStateException("met.no sin datos")
    val speed = safe(first.wind_speed)
    val dir = safe(first.wind_from_direction)
    // met.no no da ráfagas: estimación conservadora viento * 1.4, marcada "est." en la UI.
    val gustEst = speed * 1.4
    val hourly = series.take(24).map {
      val d = it.data?.instant?.details
      val s = safe(d?.wind_speed)
      HourlyPoint(it.time, s, s * 1.4, null, null, safe(d?.wind_from_direction))
    }
    return WindReport(
      WindNow(speed, dir, gustEst, null, null, null, null, null, first.air_temperature, series.firstOrNull()?.time),
      hourly, "met.no (respaldo, ráfaga est.)", lat, lon
    )
  }
}
