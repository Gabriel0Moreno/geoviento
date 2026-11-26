package com.geovision.geoviento.data

import retrofit2.http.GET
import retrofit2.http.Headers

// Radar de lluvia (RainViewer, API pública sin llave, v1.3.0).
// La tesela de un frame es https://tilecache.rainviewer.com{path}/
// 256/{z}/{x}/{y}/2/1_1.png (verificado 2026-11-26: HTTP 200, PNG).
// Solo se usa radar.past (frames pasados, hasta 13, cada 10 min); el
// resto de la respuesta se ignora. Si la consulta falla o no hay
// internet, el radar simplemente no se muestra y la app sigue normal.
data class RainViewerFrame(val path: String?)
data class RainViewerRadar(val past: List<RainViewerFrame>?)
data class RainViewerMaps(val radar: RainViewerRadar?)

interface RainViewerApi {
  @GET("public/weather-maps.json")
  @Headers("User-Agent: GeovientoMx/1.3.0 (github.com/Gabriel0Moreno/geoviento)")
  suspend fun weatherMaps(): RainViewerMaps
}
