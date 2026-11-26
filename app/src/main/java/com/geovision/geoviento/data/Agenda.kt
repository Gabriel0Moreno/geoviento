package com.geovision.geoviento.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// Una fecha de vuelo agendada (v1.5.0): día, hora, lugar y nota. El
// aviso suena `remindMinutesBefore` minutos antes (60 por defecto).
// La fecha se guarda como texto "yyyy-MM-dd" y la hora "HH:mm" para que
// ordenar por fecha+hora sea ordenar el texto.
data class AgendaItem(
  val id: String = UUID.randomUUID().toString(),
  val note: String = "",
  val date: String, // yyyy-MM-dd
  val time: String, // HH:mm
  val lat: Double,
  val lon: Double,
  val placeName: String,
  val remindMinutesBefore: Int = 60
)

// Guardado de la agenda en el teléfono: SharedPreferences con un JSON
// (org.json, sin dependencias nuevas), mismo patrón que PrefsStore.
// Si el JSON se corrompe, se vuelve a la lista vacía y la app sigue.
object AgendaStore {
  private const val PREFS = "geoviento_agenda"
  private const val KEY = "items_json"

  fun load(context: Context): List<AgendaItem> {
    return try {
      val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        ?: return emptyList()
      val arr = JSONArray(raw)
      (0 until arr.length()).mapNotNull { i ->
        runCatching {
          val o = arr.getJSONObject(i)
          AgendaItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            note = o.optString("note", ""),
            date = o.getString("date"),
            time = o.getString("time"),
            lat = o.getDouble("lat"),
            lon = o.getDouble("lon"),
            placeName = o.optString("placeName", "Sin lugar"),
            remindMinutesBefore = o.optInt("remindMinutesBefore", 60)
          )
        }.getOrNull()
      }.sortedBy { it.date + "T" + it.time }
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun save(context: Context, items: List<AgendaItem>) {
    try {
      val arr = JSONArray().apply {
        items.forEach { item ->
          put(JSONObject().apply {
            put("id", item.id)
            put("note", item.note)
            put("date", item.date)
            put("time", item.time)
            put("lat", item.lat)
            put("lon", item.lon)
            put("placeName", item.placeName)
            put("remindMinutesBefore", item.remindMinutesBefore)
          })
        }
      }
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY, arr.toString()).apply()
    } catch (_: Exception) { /* sin agenda guardada, la app sigue con la lista en memoria */ }
  }
}
