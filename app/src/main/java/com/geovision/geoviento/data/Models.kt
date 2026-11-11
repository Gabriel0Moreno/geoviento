package com.geovision.geoviento.data

// gust120Est es una ESTIMACIÓN (Open-Meteo no publica ráfagas a 120 m):
// gust10 * (speed120 / speed10). Se muestra como "est." en la interfaz.
data class WindNow(val speed10: Double, val dir10: Double, val gust10: Double, val speed80: Double?, val dir80: Double?, val speed120: Double?, val dir120: Double?, val gust120Est: Double?, val temp: Double?, val time: String?)
data class HourlyPoint(val time: String, val speed10: Double, val gust10: Double, val speed120: Double?, val gust120Est: Double?, val dir10: Double = 0.0)
data class WindReport(val now: WindNow, val hourly: List<HourlyPoint>, val source: String, val lat: Double, val lon: Double)
