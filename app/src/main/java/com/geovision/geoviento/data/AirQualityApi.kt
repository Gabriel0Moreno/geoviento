package com.geovision.geoviento.data
import retrofit2.http.GET
import retrofit2.http.Query

// Calidad del aire Open-Meteo (gratis, sin key): AQI de EE. UU. y PM2.5.
data class AqCurrent(val time: String?, val us_aqi: Double?, val pm2_5: Double?)
data class AqResponse(val current: AqCurrent?)
interface AirQualityApi {
  @GET("v1/air-quality")
  suspend fun airQuality(
    @Query("latitude") lat: Double, @Query("longitude") lon: Double,
    @Query("current") current: String = "us_aqi,pm2_5",
    @Query("timezone") tz: String = "America/Mexico_City"
  ): AqResponse
}
