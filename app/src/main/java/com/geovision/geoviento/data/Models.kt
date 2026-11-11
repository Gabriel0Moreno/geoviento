package com.geovision.geoviento.data
data class WindNow(val speed10: Double, val dir10: Double, val gust10: Double, val speed120: Double?, val dir120: Double?, val gust120: Double?, val temp: Double?, val time: String?)
data class HourlyPoint(val time: String, val speed10: Double, val gust10: Double, val gust120: Double?)
data class WindReport(val now: WindNow, val hourly: List<HourlyPoint>, val source: String, val lat: Double, val lon: Double)
