package com.geovision.geoviento.data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.URL
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
  private val api = Retrofit.Builder().baseUrl("https://api.open-meteo.com/")
    .client(http).addConverterFactory(GsonConverterFactory.create()).build().create(OpenMeteoApi::class.java)
  private val metNo = Retrofit.Builder().baseUrl("https://api.met.no/")
    .client(http).addConverterFactory(GsonConverterFactory.create()).build().create(MetNoApi::class.java)

  // Diagnóstico de la última carga / prueba, para mostrarlo en la app.
  private val diagLock = Any()
  private val _diag = mutableListOf<DiagEntry>()
  val diag: List<DiagEntry> get() = synchronized(diagLock) { _diag.toList() }
  private fun addDiag(e: DiagEntry) = synchronized(diagLock) { _diag.add(e) }
  private fun clearDiag() = synchronized(diagLock) { _diag.clear() }

  // Rejilla 5x5 para las corrientes del mapa: puntos cada 0.05° alrededor de tu ubicación.
  suspend fun grid(lat: Double, lon: Double): List<Triple<Double, Double, Pair<Double, Double>>> {
    val out = mutableListOf<Triple<Double, Double, Pair<Double, Double>>>()
    for (di in -2..2) for (dj in -2..2) {
      val la = lat + di * 0.05; val lo = lon + dj * 0.05
      try {
        val r = api.forecast(la, lo); val c = r.current ?: continue
        out.add(Triple(la, lo, Pair(safe(c.wind_speed_10m), safe(c.wind_direction_10m))))
      } catch (e: Exception) { /* punto sin datos: se salta, el mapa interpola el resto */ }
    }
    return out
  }

  // Open-Meteo y met.no se prueban EN PARALELO y gana la primera que responde
  // bien. Toda la red corre en Dispatchers.IO, nunca en el hilo principal.
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
      // Gana el primer resultado que llegue; si falla, se espera al otro.
      val first: Result<WindReport> = select {
        omD.onAwait { it }
        metD.onAwait { it }
      }
      if (first.isSuccess) {
        omD.cancel(); metD.cancel()
        return@coroutineScope first.getOrThrow()
      }
      val second = select<Result<WindReport>> {
        omD.onAwait { it }
        metD.onAwait { it }
      }
      if (second.isSuccess) return@coroutineScope second.getOrThrow()
      throw IllegalStateException(
        "Open-Meteo falló (${first.exceptionOrNull()?.message}); " +
          "el respaldo met.no tampoco respondió (${second.exceptionOrNull()?.message})"
      )
    }
  }

  // Prueba directa de conexión a las dos fuentes (sin Retrofit), para el
  // botón "Probar conexión" del diagnóstico.
  suspend fun testConnection(lat: Double, lon: Double): List<DiagEntry> = withContext(Dispatchers.IO) {
    val out = mutableListOf<DiagEntry>()
    out.add(probe("Open-Meteo", "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m&wind_speed_unit=ms", null))
    out.add(probe("met.no", "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$lat&lon=$lon", "GeovientoMx/1.0.3 (github.com/Gabriel0Moreno/geoviento)"))
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

  private suspend fun openMeteoNow(lat: Double, lon: Double): WindReport {
    val r = api.forecast(lat, lon)
    val c = r.current ?: throw IllegalStateException("Open-Meteo sin datos actuales")
    val h = r.hourly
    // Las listas horarias se combinan solo hasta el índice mínimo común:
    // si alguna llega más corta o en null, no se indexa fuera de rango.
    val hourly = if (h != null) {
      val times = h.time ?: emptyList()
      val n = minOf(times.size, h.wind_speed_10m?.size ?: 0, h.wind_gusts_10m?.size ?: 0)
      (0 until minOf(n, 24)).map {
        val s120 = h.wind_speed_120m?.getOrNull(it)
        val sp10 = safe(h.wind_speed_10m?.getOrNull(it))
        val gu10 = safe(h.wind_gusts_10m?.getOrNull(it))
        HourlyPoint(times[it], sp10, gu10, s120, estimateGust(gu10, sp10, s120), safe(h.wind_direction_10m?.getOrNull(it)))
      }
    } else emptyList()
    val speed10 = safe(c.wind_speed_10m)
    val gust10 = safe(c.wind_gusts_10m)
    return WindReport(
      WindNow(speed10, safe(c.wind_direction_10m), gust10, c.wind_speed_80m, c.wind_direction_80m, c.wind_speed_120m, c.wind_direction_120m, estimateGust(gust10, speed10, c.wind_speed_120m), c.temperature_2m, c.time),
      hourly, "Open-Meteo", lat, lon
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
