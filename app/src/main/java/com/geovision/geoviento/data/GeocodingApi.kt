package com.geovision.geoviento.data
import retrofit2.http.GET
import retrofit2.http.Query

// Búsqueda de lugares por nombre, Open-Meteo Geocoding (gratis, sin key).
data class GeoResult(val name: String?, val latitude: Double?, val longitude: Double?, val admin1: String?, val country: String?)
data class GeoResponse(val results: List<GeoResult>?)
interface GeocodingApi {
  @GET("v1/search")
  suspend fun search(
    @Query("name") name: String,
    @Query("count") count: Int = 5,
    @Query("language") language: String = "es",
    @Query("format") format: String = "json"
  ): GeoResponse
}
