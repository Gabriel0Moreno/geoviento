package com.geovision.geoviento.data
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
class WindRepository {
  private val api = Retrofit.Builder().baseUrl("https://api.open-meteo.com/")
    .addConverterFactory(GsonConverterFactory.create()).build().create(OpenMeteoApi::class.java)
  // Rejilla 5x5 para las corrientes del mapa: puntos cada 0.05° alrededor de tu ubicación.
  suspend fun grid(lat: Double, lon: Double): List<Triple<Double,Double,Pair<Double,Double>>> {
    val out = mutableListOf<Triple<Double,Double,Pair<Double,Double>>>()
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
    val r = api.forecast(lat, lon) // si aquí da 429, la UI muestra el aviso y usa met.no como respaldo
    val c = r.current ?: throw IllegalStateException("Open-Meteo sin datos actuales")
    val h = r.hourly
    val hourly = if (h != null) h.time.indices.take(24).map {
      HourlyPoint(h.time[it], h.wind_speed_10m[it], h.wind_gusts_10m[it], h.wind_gusts_120m?.getOrNull(it))
    } else emptyList()
    return WindReport(WindNow(c.wind_speed_10m ?: 0.0, c.wind_direction_10m ?: 0.0, c.wind_gusts_10m ?: 0.0, c.wind_speed_120m, c.wind_direction_120m, c.wind_gusts_120m, c.temperature_2m, c.time), hourly, "Open-Meteo (ECMWF/GFS)", lat, lon)
  }
}
