package com.geovision.geoviento.data
// Respaldo sin ráfagas: api.met.no locationforecast 2.0, gratis sin key.
// Solo se usa si Open-Meteo devuelve 429 / error. Requiere header User-Agent propio.
data class MetWind(val speed: Double, val dir: Double, val temp: Double)
