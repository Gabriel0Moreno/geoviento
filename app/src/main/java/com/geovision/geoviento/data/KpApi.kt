package com.geovision.geoviento.data

import retrofit2.http.GET

// Índice Kp de NOAA SWPC (https://services.swpc.noaa.gov/json/planetary_k_index_1m.json).
// El feed es un arreglo con una entrada por minuto; la última con
// estimated_kp es el valor más reciente. Si la consulta falla, el Kp
// queda en null y la app lo muestra como "No disponible" sin bloquear
// nada: el Kp es un factor más del diagnóstico, no un requisito.
// Verificado 2026-11-26: el JSON trae time_tag, kp_index, estimated_kp y kp.
data class KpEntry(val time_tag: String?, val estimated_kp: Double?, val kp_index: Int?)

interface KpApi {
  @GET("json/planetary_k_index_1m.json")
  suspend fun kp(): List<KpEntry>
}
