package com.geovision.geoviento.data

import android.content.Context
import com.google.gson.Gson

// Guarda el último reporte con datos reales en el teléfono, para que la app
// nunca abra vacía: al abrir muestra esos datos (marcados como GUARDADOS)
// mientras intenta actualizar en vivo.
object ReportCache {
  private const val PREFS = "geoviento"
  private const val KEY = "last_report"
  private val gson = Gson()

  fun save(context: Context, report: WindReport) {
    try {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY, gson.toJson(report)).apply()
    } catch (_: Exception) { /* si no se puede guardar, la app sigue con el ejemplo */ }
  }

  fun load(context: Context): WindReport? {
    return try {
      val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        ?: return null
      gson.fromJson(json, WindReport::class.java)
    } catch (_: Exception) {
      null
    }
  }
}
