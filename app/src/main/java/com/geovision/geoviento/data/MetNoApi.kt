package com.geovision.geoviento.data
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

// Respaldo real: api.met.no locationforecast 2.0, gratis sin key.
// met.no NO da ráfagas: la ráfaga se estima como viento * 1.4 y se marca "est.".
// Requiere header User-Agent propio (lo exige met.no).
data class MetDetails(val air_temperature: Double?, val wind_speed: Double?, val wind_from_direction: Double?)
data class MetInstant(val details: MetDetails?)
data class MetData(val instant: MetInstant?)
data class MetSeries(val time: String, val data: MetData?)
data class MetProperties(val timeseries: List<MetSeries>?)
data class MetResponse(val properties: MetProperties?)

interface MetNoApi {
  @Headers("User-Agent: GeovientoMx/1.0.1 (github.com/Gabriel0Moreno/geoviento)")
  @GET("weatherapi/locationforecast/2.0/compact")
  suspend fun forecast(@Query("lat") lat: Double, @Query("lon") lon: Double): MetResponse
}
