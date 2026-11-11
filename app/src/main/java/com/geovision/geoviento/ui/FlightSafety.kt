package com.geovision.geoviento.ui
enum class FlightLevel(val label: String, val advice: String) { GREEN("VERDE — SE PUEDE VOLAR", "Ráfaga cómoda para dron <249 g."), YELLOW("AMARILLO — PRECAUCIÓN", "Vuela bajo y cerca, vigila batería y regreso."), RED("ROJO — NO VOLAR", "La ráfaga supera el límite de 10.7 m/s.") }
fun flightLevel(gustMs: Double): FlightLevel = when { gustMs < 8.0 -> FlightLevel.GREEN; gustMs < 10.7 -> FlightLevel.YELLOW; else -> FlightLevel.RED }
fun compass(deg: Double): String { val dirs = listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSO","SO","OSO","O","ONO","NO","NNO"); return dirs[((deg + 11.25) / 22.5).toInt() % 16] }
fun kmh(ms: Double) = ms * 3.6
