package com.geovision.geoviento.ui
import kotlin.math.ln

// Semáforo de la demo, ahora según el límite del dron elegido:
// verde < 75% del límite, amarillo 75%-100%, rojo >= límite.
// Con el DJI Lito X1 (10.7 m/s) eso da verde < 8.0, amarillo 8-10.7, rojo >= 10.7.
enum class FlightLevel(val label: String, val advice: String) { GREEN("VERDE — SE PUEDE VOLAR", "Ráfaga cómoda para tu dron."), YELLOW("AMARILLO — PRECAUCIÓN", "Vuela bajo y cerca, vigila batería y regreso."), RED("ROJO — NO VOLAR", "La ráfaga supera el límite de tu dron.") }
fun flightLevel(gustMs: Double, limit: Double = 10.7): FlightLevel = when {
  gustMs < limit * 0.75 -> FlightLevel.GREEN
  gustMs < limit -> FlightLevel.YELLOW
  else -> FlightLevel.RED
}
fun compass(deg: Double): String { val dirs = listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSO","SO","OSO","O","ONO","NO","NNO"); return dirs[((deg + 11.25) / 22.5).toInt() % 16] }
fun kmh(ms: Double) = ms * 3.6

// Perfil logarítmico de la demo (terreno con obstáculos, z0 = 0.3 m):
// factor de crecimiento del viento entre 10 m y la altura h.
// A 105 m da ×1.67, igual que la demo (×1.68).
fun logWindFactor(heightM: Double): Double {
  if (heightM <= 10.0) return 1.0
  val f = ln(heightM / 0.3) / ln(10.0 / 0.3)
  return if (f.isFinite() && f > 0) f else 1.0
}
