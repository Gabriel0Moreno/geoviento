package com.geovision.geoviento.data
import retrofit2.http.GET
import retrofit2.http.Query
data class OmCurrent(val time: String?, val temperature_2m: Double?, val wind_speed_10m: Double?, val wind_direction_10m: Double?, val wind_gusts_10m: Double?, val wind_speed_120m: Double?, val wind_direction_120m: Double?, val wind_gusts_120m: Double?)
data class OmHourly(val time: List<String>, val wind_speed_10m: List<Double>, val wind_direction_10m: List<Double>, val wind_gusts_10m: List<Double>, val wind_speed_120m: List<Double>?, val wind_gusts_120m: List<Double>?)
data class OmResponse(val current: OmCurrent?, val hourly: OmHourly?)
interface OpenMeteoApi {
  @GET("v1/forecast")
  suspend fun forecast(
    @Query("latitude") lat: Double, @Query("longitude") lon: Double,
    @Query("current") current: String = "temperature_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m,wind_speed_80m,wind_speed_120m,wind_direction_120m,wind_gusts_120m",
    @Query("hourly") hourly: String = "wind_speed_10m,wind_direction_10m,wind_gusts_10m,wind_speed_120m,wind_gusts_120m",
    @Query("wind_speed_unit") unit: String = "ms", @Query("forecast_days") days: Int = 3,
    @Query("timezone") tz: String = "America/Mexico_City"
  ): OmResponse
}
