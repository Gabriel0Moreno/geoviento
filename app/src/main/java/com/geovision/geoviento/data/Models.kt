package com.geovision.geoviento.data

// Una línea del diagnóstico de conexión que se muestra en la app.
data class DiagEntry(val source: String, val ok: Boolean, val detail: String, val ms: Long)

// gust120Est es una ESTIMACIÓN (Open-Meteo no publica ráfagas a 120 m):
// gust10 * (speed120 / speed10). Se muestra como "est." en la interfaz.
data class WindNow(val speed10: Double, val dir10: Double, val gust10: Double, val speed80: Double?, val dir80: Double?, val speed120: Double?, val dir120: Double?, val gust120Est: Double?, val temp: Double?, val time: String?)
data class HourlyPoint(val time: String, val speed10: Double, val gust10: Double, val speed120: Double?, val gust120Est: Double?, val dir10: Double = 0.0, val temp10: Double? = null, val precipProb: Double? = null)

// Condiciones en superficie (demo): humedad, presión, UV, amanecer/atardecer,
// temperatura máxima y ráfaga máxima del día, altitud del lugar.
data class SurfaceNow(val humidity: Double?, val pressure: Double?, val uvIndex: Double?, val sunrise: String?, val sunset: String?, val tempMaxToday: Double?, val gustMaxToday: Double?, val gustMaxHour: String?, val precipProbNow: Double?, val elevation: Double?)

// Calidad del aire (Open-Meteo Air Quality API): AQI de EE. UU. y PM2.5.
data class AirQualityNow(val aqiUs: Double?, val pm25: Double?)

data class WindReport(val now: WindNow, val hourly: List<HourlyPoint>, val source: String, val lat: Double, val lon: Double, val surface: SurfaceNow? = null, val air: AirQualityNow? = null, val placeName: String? = null)

// Opciones de dron para el informe y el semáforo (la demo trae 3).
// Verde < 75% del límite, amarillo 75%-100%, rojo >= límite.
data class DroneOption(val name: String, val limit: Double)
val DRONES = listOf(
  DroneOption("DJI Lito X1 / clase <249 g", 10.7),
  DroneOption("Clase ligera ampliada", 12.0),
  DroneOption("Clase media", 15.0)
)

// Zonas de vuelo de la demo (coordenadas reales alrededor de Tuxtla).
data class ZoneInfo(val name: String, val lat: Double, val lon: Double, val elevM: Int, val tag: String, val note: String)
val ZONES = listOf(
  ZoneInfo("Centro de Tuxtla", 16.7552, -93.1154, 535, "REFERENCIA URBANA", "Valle plano y abierto. Con el viento de hoy el flujo llega limpio; entre edificios altos se forman remolinos locales, vuela por encima de las azoteas."),
  ZoneInfo("Real del Bosque", 16.7167, -93.0667, 560, "EXPUESTA POR LA TARDE", "Zona de terrenos y casas al oriente, trabajo típico de foto aérea para venta. Sin obstáculos altos: el viento de la tarde se siente completo, mejor en la mañana."),
  ZoneInfo("Acceso al Cañón del Sumidero", 16.8100, -93.1000, 620, "CANALIZACIÓN FUERTE", "El cañón actúa como embudo: con viento del norte las corrientes se aceleran y se encajonan. Acércate solo con aire en calma y nunca pierdas señal dentro de la garganta."),
  ZoneInfo("Copoya y la meseta", 16.7000, -93.1333, 780, "TURBULENCIA A SOTAVENTO", "Terreno alto al sur de la ciudad. El aire sube por la ladera (ascenso orográfico, útil para ganar altura) y detrás del filo se forma turbulencia a sotavento."),
  ZoneInfo("Chiapa de Corzo", 16.7083, -93.0167, 420, "VALLE ABIERTO", "Valle del Grijalva, más abierto. La brisa del río puede sumar flujo local por la tarde: revisa el viento en sitio antes de despegar sobre el malecón.")
)

// Informe de un día (hoy con pronóstico, días pasados con archivo ERA5).
data class DayReport(val date: String, val hourly: List<HourlyPoint>, val maxGust: Double, val maxGustHour: String?, val dominantDir: Double?, val source: String)

// Lugar encontrado por nombre (geocodificación).
data class PlaceResult(val name: String, val detail: String, val lat: Double, val lon: Double)
