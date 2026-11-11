package com.geovision.geoviento.data
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Estima la ráfaga a una altura a partir de la ráfaga a 10 m. */
fun estimateGust(gust10: Double, speed10: Double, speedAtHeight: Double?): Double? {
  if (speedAtHeight == null || speed10 <= 0.0) return gust10
  return gust10 * (speedAtHeight / speed10)
}

class WindRepository {
  private val api = Retrofit.Builder().baseUrl("https://api.open-meteo.com/")
    .addConverterFactory(GsonConverterFactory.create()).build().create(OpenMeteoApi::class.java)
  private val metNo = Retrofit.Builder().baseUrl("https://api.met.no/")
    .addConverterFactory(GsonConverterFactory.create()).build().create(MetNoApi::class.java)

  // Rejilla 5x5 para las corrientes del mapa: puntos cada 0.05° alrededor de tu ubicación.
  suspend fun grid(lat: Double, lon: Double): List<Triple<Double, Double, Pair<Double, Double>>> {
    val out = mutableListOf<Triple<Double, Double, Pair<Double, Double>>>()
    for (di in -2..2) for (dj in -2..2) {
      val la = lat + di * 0.05; val lo = lon + dj * 0.05
      try {
        val r = api.forecast(la, lo); val c = r.current ?: continue
        out.add(Triple(la, lo, Pair(c.wind_speed_10m ?: 0.0, c.wind_direction_10m ?: 0.0)))
      } catch (e: Exception) { /* punto sin datos: se salta, el mapa interpola el resto */ }
    }
    return out
  }

  suspend fun now(lat: Double, lon: Double): WindReport {
    try {
      return openMeteoNow(lat, lon)
    } catch (e: Exception) {
      // Respaldo real con met.no antes de mostrar error.
      try {
        return metNoNow(lat, lon)
      } catch (e2: Exception) {
        throw IllegalStateException("Open-Meteo falló (${e.message}); el respaldo met.no tampoco respondió (${e2.message})")
      }
    }
  }

  private suspend fun openMeteoNow(lat: Double, lon: Double): WindReport {
    val r = api.forecast(lat, lon)
    val c = r.current ?: throw IllegalStateException("Open-Meteo sin datos actuales")
    val h = r.hourly
    val hourly = if (h != null) h.time.indices.take(24).map {
      val s120 = h.wind_speed_120m?.getOrNull(it)
      HourlyPoint(h.time[it], h.wind_speed_10m[it], h.wind_gusts_10m[it], s120, estimateGust(h.wind_gusts_10m[it], h.wind_speed_10m[it], s120))
    } else emptyList()
    val speed10 = c.wind_speed_10m ?: 0.0
    val gust10 = c.wind_gusts_10m ?: 0.0
    return WindReport(
      WindNow(speed10, c.wind_direction_10m ?: 0.0, gust10, c.wind_speed_80m, c.wind_direction_80m, c.wind_speed_120m, c.wind_direction_120m, estimateGust(gust10, speed10, c.wind_speed_120m), c.temperature_2m, c.time),
      hourly, "Open-Meteo", lat, lon
    )
  }

  private suspend fun metNoNow(lat: Double, lon: Double): WindReport {
    val series = metNo.forecast(lat, lon).properties?.timeseries
      ?: throw IllegalStateException("met.no sin datos")
    val first = series.firstOrNull()?.data?.instant?.details
      ?: throw IllegalStateException("met.no sin datos")
    val speed = first.wind_speed ?: 0.0
    val dir = first.wind_from_direction ?: 0.0
    // met.no no da ráfagas: estimación conservadora viento * 1.4, marcada "est." en la UI.
    val gustEst = speed * 1.4
    val hourly = series.take(24).map {
      val d = it.data?.instant?.details
      val s = d?.wind_speed ?: 0.0
      HourlyPoint(it.time, s, s * 1.4, null, null)
    }
    return WindReport(
      WindNow(speed, dir, gustEst, null, null, null, null, null, first.air_temperature, series.firstOrNull()?.time),
      hourly, "met.no (respaldo, ráfaga est.)", lat, lon
    )
  }
}
