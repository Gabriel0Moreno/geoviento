package com.geovision.geoviento.data
import retrofit2.http.GET
import retrofit2.http.Query

// OJO: Open-Meteo NO tiene ráfagas a 80 ni a 120 m (wind_gusts_80m / wind_gusts_120m
// no existen y la API devuelve HTTP 400). Las ráfagas solo existen a 10 m.
// La ráfaga a 120 m se ESTIMA en WindRepository como gust10 * (speed120 / speed10).
// Las listas horarias son nulables: si la API devuelve una más corta o en null,
// el repositorio combina solo el índice mínimo común en vez de romperse.
data class OmCurrent(val time: String?, val temperature_2m: Double?, val relative_humidity_2m: Double?, val surface_pressure: Double?, val uv_index: Double?, val wind_speed_10m: Double?, val wind_direction_10m: Double?, val wind_gusts_10m: Double?, val wind_speed_80m: Double?, val wind_direction_80m: Double?, val wind_speed_120m: Double?, val wind_direction_120m: Double?)
data class OmHourly(val time: List<String>?, val temperature_2m: List<Double>?, val precipitation_probability: List<Double>?, val wind_speed_10m: List<Double>?, val wind_direction_10m: List<Double>?, val wind_gusts_10m: List<Double>?, val wind_speed_80m: List<Double>?, val wind_direction_80m: List<Double>?, val wind_speed_120m: List<Double>?, val wind_direction_120m: List<Double>?)
data class OmDaily(val time: List<String>?, val sunrise: List<String>?, val sunset: List<String>?, val temperature_2m_max: List<Double>?, val wind_gusts_10m_max: List<Double>?)
data class OmResponse(val elevation: Double?, val current: OmCurrent?, val hourly: OmHourly?, val daily: OmDaily?)
interface OpenMeteoApi {
  @GET("v1/forecast")
  suspend fun forecast(
    @Query("latitude") lat: Double, @Query("longitude") lon: Double,
    @Query("current") current: String = "temperature_2m,relative_humidity_2m,surface_pressure,uv_index,wind_speed_10m,wind_direction_10m,wind_gusts_10m,wind_speed_80m,wind_direction_80m,wind_speed_120m,wind_direction_120m",
    @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,wind_speed_10m,wind_direction_10m,wind_gusts_10m,wind_speed_80m,wind_direction_80m,wind_speed_120m,wind_direction_120m",
    @Query("daily") daily: String = "sunrise,sunset,temperature_2m_max,wind_gusts_10m_max",
    @Query("wind_speed_unit") unit: String = "ms", @Query("forecast_days") days: Int = 3,
    @Query("timezone") tz: String = "America/Mexico_City"
  ): OmResponse
}

// Archivo histórico ERA5 (días pasados): solo variables válidas a 10 m.
data class OmArchiveHourly(val time: List<String>?, val temperature_2m: List<Double>?, val wind_speed_10m: List<Double>?, val wind_direction_10m: List<Double>?, val wind_gusts_10m: List<Double>?)
data class OmArchiveDaily(val time: List<String>?, val wind_gusts_10m_max: List<Double>?, val wind_direction_10m_dominant: List<Double>?)
data class OmArchiveResponse(val hourly: OmArchiveHourly?, val daily: OmArchiveDaily?)
interface OpenMeteoArchiveApi {
  @GET("v1/archive")
  suspend fun archive(
    @Query("latitude") lat: Double, @Query("longitude") lon: Double,
    @Query("start_date") start: String, @Query("end_date") end: String,
    @Query("hourly") hourly: String = "temperature_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
    @Query("daily") daily: String = "wind_gusts_10m_max,wind_direction_10m_dominant",
    @Query("wind_speed_unit") unit: String = "ms",
    @Query("timezone") tz: String = "America/Mexico_City"
  ): OmArchiveResponse
}
