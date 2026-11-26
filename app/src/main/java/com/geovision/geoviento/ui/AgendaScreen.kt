// Pestaña Agenda (v1.5.0): agendar fechas de vuelo con recordatorio
// 1 hora antes y ver los lugares agendados en un mapa general.
// - Nueva fecha: fecha (DatePickerDialog), hora (TimePickerDialog),
//   lugar (búsqueda por nombre, reutilizando repo.searchPlace, o "Mi
//   ubicación" = el lugar principal actual de la app) y nota opcional.
// - Cada fila muestra fecha, hora, lugar y nota; si la fecha cae dentro
//   del pronóstico de 72 h que ya trae el ViewModel, también el viento
//   y las ráfagas esperados a esa hora con el color del semáforo.
// - Tocar una fila abre el formulario prellenado para editarla; la ✕
//   la borra (y cancela su recordatorio). Las pasadas van abajo en gris.
// - El permiso de notificaciones (Android 13+) se pide solo al agendar
//   la primera fecha; si lo niega, la fecha se guarda igual y se avisa.
package com.geovision.geoviento.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.geovision.geoviento.data.AgendaItem
import com.geovision.geoviento.data.FlightPrefs
import com.geovision.geoviento.data.PlaceResult
import com.geovision.geoviento.data.WindReport
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val AGENDA_ZONE = ZoneId.of("America/Mexico_City")
private val AGENDA_LOCALE = Locale("es", "MX")
private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy", AGENDA_LOCALE)
private val DATE_FMT_SHORT = DateTimeFormatter.ofPattern("EEE d MMM", AGENDA_LOCALE)

private fun agendaDateLabel(dateStr: String): String = runCatching {
  LocalDate.parse(dateStr).format(DATE_FMT)
}.getOrDefault(dateStr)

private fun agendaDateShort(dateStr: String): String = runCatching {
  LocalDate.parse(dateStr).format(DATE_FMT_SHORT)
}.getOrDefault(dateStr)

// Una fecha ya pasó si su día es anterior a hoy, o si es hoy y su hora
// ya pasó (zona America/Mexico_City, como todo el pronóstico).
private fun isPast(item: AgendaItem): Boolean {
  val today = LocalDate.now(AGENDA_ZONE).toString()
  if (item.date < today) return true
  if (item.date > today) return false
  val nowMin = LocalTime.now(AGENDA_ZONE).hour * 60 + LocalTime.now(AGENDA_ZONE).minute
  val itemMin = (item.time.substringBefore(":").toIntOrNull() ?: 0) * 60 +
    (item.time.substringAfter(":").toIntOrNull() ?: 0)
  return itemMin < nowMin
}

@Composable
fun AgendaScreen(
  report: WindReport,
  limit: Double,
  prefs: FlightPrefs,
  items: List<AgendaItem>,
  currentLat: Double,
  currentLon: Double,
  currentPlaceName: String,
  onUseGps: () -> Unit,
  searchResults: List<PlaceResult>,
  onSearchPlaces: (String) -> Unit,
  onClearSearch: () -> Unit,
  onSave: (AgendaItem) -> Unit,
  onDelete: (String) -> Unit,
  requestNotifPermission: ((Boolean) -> Unit) -> Unit
) {
  val context = LocalContext.current
  var showForm by remember { mutableStateOf(false) }
  var editingId by remember { mutableStateOf<String?>(null) }
  var formDate by remember { mutableStateOf(LocalDate.now(AGENDA_ZONE)) }
  var formHour by remember { mutableStateOf(8) }
  var formMinute by remember { mutableStateOf(0) }
  var formPlaceName by remember { mutableStateOf("") }
  var formLat by remember { mutableStateOf(currentLat) }
  var formLon by remember { mutableStateOf(currentLon) }
  var formNote by remember { mutableStateOf("") }
  var notifWarning by remember { mutableStateOf(false) }
  var selectedPinId by remember { mutableStateOf<String?>(null) }
  var mapStatus by remember { mutableStateOf("Mapa de la agenda: cargando…") }

  val upcoming = items.filter { !isPast(it) }
  val past = items.filter { isPast(it) }.sortedByDescending { it.date + "T" + it.time }
  val selectedPin = items.firstOrNull { it.id == selectedPinId }

  fun openNew() {
    editingId = null
    formDate = LocalDate.now(AGENDA_ZONE)
    formHour = 8
    formMinute = 0
    formPlaceName = currentPlaceName
    formLat = currentLat
    formLon = currentLon
    formNote = ""
    onClearSearch()
    showForm = true
  }

  fun openEdit(item: AgendaItem) {
    editingId = item.id
    formDate = runCatching { LocalDate.parse(item.date) }.getOrDefault(LocalDate.now(AGENDA_ZONE))
    formHour = item.time.substringBefore(":").toIntOrNull() ?: 8
    formMinute = item.time.substringAfter(":").toIntOrNull() ?: 0
    formPlaceName = item.placeName
    formLat = item.lat
    formLon = item.lon
    formNote = item.note
    onClearSearch()
    showForm = true
  }

  fun saveForm() {
    val wasFirst = items.isEmpty()
    val item = AgendaItem(
      id = editingId ?: UUID.randomUUID().toString(),
      note = formNote.trim(),
      date = formDate.toString(),
      time = "%02d:%02d".format(formHour, formMinute),
      lat = formLat,
      lon = formLon,
      placeName = formPlaceName.trim().ifBlank { "Sin lugar" },
      remindMinutesBefore = 60
    )
    onSave(item)
    showForm = false
    editingId = null
    // Permiso de notificaciones: solo se pide al agendar la primera
    // fecha (Android 13+). Si lo niega, la fecha queda guardada igual
    // y se avisa que el recordatorio no llegará.
    if (wasFirst && Build.VERSION.SDK_INT >= 33) {
      val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
      if (!granted) requestNotifPermission { ok -> if (!ok) notifWarning = true }
    }
  }

  fun showDatePicker() {
    DatePickerDialog(
      context,
      { _, y, m, d -> formDate = LocalDate.of(y, m + 1, d) },
      formDate.year, formDate.monthValue - 1, formDate.dayOfMonth
    ).show()
  }

  fun showTimePicker() {
    TimePickerDialog(
      context,
      { _, h, min -> formHour = h; formMinute = min },
      formHour, formMinute, true
    ).show()
  }

  Text("Agenda", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Text("Tus vuelos agendados, con recordatorio 1 hora antes.", style = MaterialTheme.typography.bodyMedium, color = Geo.Muted)
  Spacer(Modifier.height(12.dp))

  if (notifWarning) {
    GeoCard(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(12.dp)) {
        Text("Sin permiso de notificaciones", fontWeight = FontWeight.Bold, color = Geo.WarnText)
        Text(
          "La fecha quedó guardada, pero el recordatorio no llegará sin el permiso. Actívalo en Ajustes > Apps > Geoviento Mx > Notificaciones.",
          style = MaterialTheme.typography.bodySmall, color = Geo.WarnText
        )
      }
    }
    Spacer(Modifier.height(12.dp))
  }

  Button(
    onClick = { if (showForm) showForm = false else openNew() },
    colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent)
  ) {
    Text(if (showForm) "Cerrar formulario" else "+ Nueva fecha", color = Geo.Text, fontWeight = FontWeight.Bold)
  }

  if (showForm) {
    Spacer(Modifier.height(12.dp))
    GeoCard(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp)) {
        Text(
          if (editingId == null) "Nueva fecha de vuelo" else "Editar fecha",
          style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Geo.Text
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { showDatePicker() }, modifier = Modifier.weight(1f)) {
            Text("Fecha: " + agendaDateShort(formDate.toString()), color = Geo.Text)
          }
          OutlinedButton(onClick = { showTimePicker() }, modifier = Modifier.weight(1f)) {
            Text("Hora: " + "%02d:%02d".format(formHour, formMinute), color = Geo.Text)
          }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = formPlaceName,
          onValueChange = { formPlaceName = it },
          label = { Text("Lugar") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Geo.Text, unfocusedTextColor = Geo.Text,
            focusedLabelColor = Geo.Muted, unfocusedLabelColor = Geo.Muted
          )
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { if (formPlaceName.length >= 3) onSearchPlaces(formPlaceName) },
            modifier = Modifier.weight(1f)
          ) { Text("Buscar lugar", color = Geo.Text) }
          OutlinedButton(
            onClick = {
              // El lugar principal de la app ya es el GPS (se pide al
              // abrir la app); se copia tal cual y se refresca en fondo.
              formLat = currentLat
              formLon = currentLon
              formPlaceName = currentPlaceName
              onUseGps()
            },
            modifier = Modifier.weight(1f)
          ) { Text("Mi ubicación", color = Geo.Text) }
        }
        if (searchResults.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          Column(Modifier.fillMaxWidth()) {
            searchResults.take(5).forEach { p ->
              Text(
                if (p.detail.isBlank()) p.name else p.name + ", " + p.detail,
                modifier = Modifier.fillMaxWidth().clickable {
                  formPlaceName = if (p.detail.isBlank()) p.name else p.name + ", " + p.detail
                  formLat = p.lat
                  formLon = p.lon
                  onClearSearch()
                }.padding(vertical = 8.dp),
                color = Geo.Accent
              )
            }
          }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = formNote,
          onValueChange = { formNote = it },
          label = { Text("Nota (opcional: qué vas a grabar)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Geo.Text, unfocusedTextColor = Geo.Text,
            focusedLabelColor = Geo.Muted, unfocusedLabelColor = Geo.Muted
          )
        )
        Spacer(Modifier.height(8.dp))
        Text("Recordatorio: 1 hora antes de la fecha.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
        Spacer(Modifier.height(12.dp))
        Button(
          onClick = { saveForm() },
          colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Guardar fecha", color = Geo.Text, fontWeight = FontWeight.Bold)
        }
      }
    }
  }

  Spacer(Modifier.height(16.dp))
  SectionTitle("Mapa de la agenda")
  Spacer(Modifier.height(8.dp))
  if (items.isEmpty()) {
    Text("Sin lugares todavía: el mapa se llena al agendar fechas.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  } else {
    Box(Modifier.fillMaxWidth().height(380.dp)) {
      AgendaMap(
        items = items,
        selectedId = selectedPinId,
        onPin = { selectedPinId = it },
        modifier = Modifier.fillMaxSize(),
        onStatus = { mapStatus = it }
      )
    }
    Spacer(Modifier.height(4.dp))
    Text(mapStatus, style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
    if (selectedPin != null) {
      Spacer(Modifier.height(8.dp))
      GeoCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
          Text(
            agendaDateLabel(selectedPin.date) + " · " + selectedPin.time,
            fontWeight = FontWeight.Bold, color = Geo.Text
          )
          Text(selectedPin.placeName, color = Geo.Muted)
          if (selectedPin.note.isNotBlank()) {
            Text(selectedPin.note, style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
          }
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { openEdit(selectedPin) }) { Text("Editar", color = Geo.Accent) }
            TextButton(onClick = { selectedPinId = null }) { Text("Cerrar", color = Geo.Muted) }
          }
        }
      }
    }
  }

  Spacer(Modifier.height(16.dp))
  SectionTitle("Próximas fechas")
  Spacer(Modifier.height(8.dp))
  if (upcoming.isEmpty()) {
    Text("Sin fechas próximas. Toca «+ Nueva fecha» para agendar tu primer vuelo.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  } else {
    upcoming.forEach { item ->
      AgendaRow(item, report, limit, prefs, onEdit = { openEdit(item) }, onDelete = { onDelete(item.id) })
      Spacer(Modifier.height(8.dp))
    }
  }
  if (past.isNotEmpty()) {
    Spacer(Modifier.height(8.dp))
    SectionTitle("Pasadas")
    Spacer(Modifier.height(8.dp))
    past.forEach { item ->
      AgendaRow(item, report, limit, prefs, past = true, onEdit = { openEdit(item) }, onDelete = { onDelete(item.id) })
      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
private fun AgendaRow(
  item: AgendaItem,
  report: WindReport,
  limit: Double,
  prefs: FlightPrefs,
  past: Boolean = false,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  // La hora del pronóstico viene "yyyy-MM-ddTHH:mm" en Open-Meteo y
  // "yyyy-MM-ddTHH:mm:ssZ" en el respaldo met.no: se compara por
  // prefijo para que el aviso funcione con las dos fuentes.
  val point = report.hourly.firstOrNull { it.time.startsWith(item.date + "T" + item.time) }
  val level = point?.let { flightLevel(it.gust10, limit) }
  GeoCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp).clickable(onClick = onEdit)) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          agendaDateLabel(item.date) + " · " + item.time,
          fontWeight = FontWeight.Bold,
          color = if (past) Geo.Muted else Geo.Text
        )
        Text(
          "✕",
          color = Geo.Muted, fontSize = 18.sp,
          modifier = Modifier.clickable(onClick = onDelete).padding(4.dp)
        )
      }
      Text(item.placeName, color = if (past) Geo.Muted else Geo.Text)
      if (item.note.isNotBlank()) {
        Text(item.note, style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      }
      if (point != null && level != null) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StatusChip(levelShort(level), levelTextColor(level))
          Text(
            "Viento " + formatSpeed(point.speed10, prefs.speedUnit) +
              " · Ráfagas " + formatSpeed(point.gust10, prefs.speedUnit),
            style = MaterialTheme.typography.bodySmall, color = Geo.Muted
          )
        }
      } else if (!past) {
        Spacer(Modifier.height(6.dp))
        Text(
          "Pronóstico no disponible para esa fecha (más de 3 días).",
          style = MaterialTheme.typography.bodySmall, color = Geo.Muted
        )
      }
    }
  }
}

private fun levelShort(level: FlightLevel): String = when (level) {
  FlightLevel.GREEN -> "Se puede volar"
  FlightLevel.YELLOW -> "Precaución"
  FlightLevel.RED -> "No volar"
}
