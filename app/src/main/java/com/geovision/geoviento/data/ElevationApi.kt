package com.geovision.geoviento.data
import retrofit2.http.GET
import retrofit2.http.Query

// API de elevación de Open-Meteo (gratis, sin clave): devuelve la altura
// sobre el nivel del mar de cada coordenada pedida. Verificado 2026-11-12
// con un corte norte-sur por Tuxtla: meseta de Copoya ~850 m, valle ~530 m,
// cerro del norte ~1270 m y fondo del cañón 415 m. Se usa para dibujar el
// perfil REAL del relieve del lugar elegido (v1.2.3).
// Las coordenadas van como texto ya unido por comas (encoded=true) para que
// las comas lleguen tal cual a la API.
data class OmElevationResponse(val elevation: List<Double>?)
interface ElevationApi {
  @GET("v1/elevation")
  suspend fun elevation(
    @Query(value = "latitude", encoded = true) lat: String,
    @Query(value = "longitude", encoded = true) lon: String
  ): OmElevationResponse
}
