package com.geovision.geoviento.data

import android.content.Context

// Preferencias de vuelo (v1.3.0): las elige el usuario en Más >
// Preferencias de vuelo y se guardan en el teléfono. El margen de
// viento se aplica a los umbrales del semáforo: con margen m, el límite
// efectivo es limite * (1 - m/100). Con 0% (por defecto) el semáforo es
// exactamente el de siempre (verde < 75% del límite, rojo >= límite).
enum class SpeedUnit(val label: String) { MS("m/s"), KMH("km/h"), MPH("mph") }
enum class TempUnit(val label: String) { C("°C"), F("°F") }
enum class OperationType(val label: String) { RECREATIONAL("Recreativa"), PROFESSIONAL("Profesional") }

data class FlightPrefs(
  val flightMinutes: Int = 15,
  val windMarginPct: Int = 0,
  val operation: OperationType = OperationType.RECREATIONAL,
  val speedUnit: SpeedUnit = SpeedUnit.MS,
  val tempUnit: TempUnit = TempUnit.C,
  val plannedAltitudeM: Int = 120
) {
  companion object {
    val DEFAULT = FlightPrefs()
  }
}

object PrefsStore {
  private const val PREFS = "geoviento_prefs"
  private const val K_MINUTES = "flight_minutes"
  private const val K_MARGIN = "wind_margin_pct"
  private const val K_OPERATION = "operation"
  private const val K_SPEED_UNIT = "speed_unit"
  private const val K_TEMP_UNIT = "temp_unit"
  private const val K_ALTITUDE = "planned_altitude_m"

  fun load(context: Context): FlightPrefs {
    return try {
      val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      FlightPrefs(
        flightMinutes = p.getInt(K_MINUTES, 15).coerceIn(5, 60),
        windMarginPct = p.getInt(K_MARGIN, 0).coerceIn(0, 50),
        operation = runCatching { OperationType.valueOf(p.getString(K_OPERATION, null) ?: "RECREATIONAL") }.getOrDefault(OperationType.RECREATIONAL),
        speedUnit = runCatching { SpeedUnit.valueOf(p.getString(K_SPEED_UNIT, null) ?: "MS") }.getOrDefault(SpeedUnit.MS),
        tempUnit = runCatching { TempUnit.valueOf(p.getString(K_TEMP_UNIT, null) ?: "C") }.getOrDefault(TempUnit.C),
        plannedAltitudeM = p.getInt(K_ALTITUDE, 120).coerceIn(10, 120)
      )
    } catch (_: Exception) {
      FlightPrefs.DEFAULT
    }
  }

  fun save(context: Context, prefs: FlightPrefs) {
    try {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putInt(K_MINUTES, prefs.flightMinutes)
        .putInt(K_MARGIN, prefs.windMarginPct)
        .putString(K_OPERATION, prefs.operation.name)
        .putString(K_SPEED_UNIT, prefs.speedUnit.name)
        .putString(K_TEMP_UNIT, prefs.tempUnit.name)
        .putInt(K_ALTITUDE, prefs.plannedAltitudeM)
        .apply()
    } catch (_: Exception) { /* sin preferencias guardadas, la app sigue con las de por defecto */ }
  }
}
