package com.geovision.geoviento.data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

  // Toda la red corre en Dispatchers.IO, nunca en el hilo principal.
  suspend fun now(lat: Double, lon: Double): WindReport = withContext(Dispatchers.IO) {
    try {
      openMeteoNow(lat, lon)
    } catch (e: Exception) {
      // Respaldo real con met.no antes de mostrar error.
      try {
        metNoNow(lat, lon)
      } catch (e2: Exception) {
        throw IllegalStateException("Open-Meteo falló (${e.message}); el respaldo met.no tampoco respondió (${e2.message})")
      }
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
