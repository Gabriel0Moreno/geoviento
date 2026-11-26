package com.geovision.geoviento.ui

import com.geovision.geoviento.data.SpeedUnit
import com.geovision.geoviento.data.TempUnit

// Formato de valores según las unidades elegidas en Preferencias.
// La app siempre guarda y calcula en m/s y °C; solo la presentación cambia.

fun formatSpeed(ms: Double, unit: SpeedUnit): String = when (unit) {
  SpeedUnit.MS -> "%.1f m/s".format(ms)
  SpeedUnit.KMH -> "%.0f km/h".format(ms * 3.6)
  SpeedUnit.MPH -> "%.0f mph".format(ms * 2.23694)
}

fun formatTemp(c: Double, unit: TempUnit): String = when (unit) {
  TempUnit.C -> "%.0f °C".format(c)
  TempUnit.F -> "%.0f °F".format(c * 9.0 / 5.0 + 32.0)
}

fun formatVisibility(m: Double?): String = when {
  m == null || !m.isFinite() -> "—"
  m < 1000 -> "%.0f m".format(m)
  else -> "%.1f km".format(m / 1000.0)
}

fun hourLabel(time: String): String {
  val h = time.substringAfter("T").substringBefore(":")
  return if (h.length == 2) "$h:00" else time
}

fun hourInt(time: String): Int = time.substringAfter("T").substringBefore(":").toIntOrNull() ?: 0
