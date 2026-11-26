package com.geovision.geoviento.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.geovision.geoviento.BuildConfig
import com.geovision.geoviento.data.DRONES
import com.geovision.geoviento.data.DiagEntry
import com.geovision.geoviento.data.DroneOption
import com.geovision.geoviento.data.FlightPrefs
import com.geovision.geoviento.data.OperationType
import com.geovision.geoviento.data.SpeedUnit
import com.geovision.geoviento.data.TempUnit
import com.geovision.geoviento.data.WindReport
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// Destinos de la pestaña Más (v1.3.0).
enum class MoreDestination { DRONES, PREFS, PDF, APPEARANCE, SOURCES, SAFETY, PRIVACY, SUPPORT, ABOUT }

// Pestaña Más: la lista de secciones, como en DroneCheck pero con las
// secciones de GeoViento. Espacio aéreo y Notificaciones son
// "Próximamente" (igual que en DroneCheck: su propio factor de espacio
// aéreo está en Pendiente).
@Composable
fun MoreScreen(onOpen: (MoreDestination) -> Unit) {
  Text("Más", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Spacer(Modifier.height(12.dp))
  MoreRow("Mis drones", "Elige tu dron: su límite mueve el semáforo y el informe PDF", onClick = { onOpen(MoreDestination.DRONES) })
  MoreRow("Preferencias de vuelo", "Duración, margen de viento, operación y unidades", onClick = { onOpen(MoreDestination.PREFS) })
  MoreRow("Informe técnico en PDF", "Informe de vuelo para tu bitácora", onClick = { onOpen(MoreDestination.PDF) })
  MoreRow("Espacio aéreo", "Próximamente — verifica el espacio aéreo antes de volar", enabled = false, onClick = {})
  MoreRow("Apariencia y ubicación", "Ubicación actual y tema de la app", onClick = { onOpen(MoreDestination.APPEARANCE) })
  MoreRow("Notificaciones", "Próximamente", enabled = false, onClick = {})
  MoreRow("Fuentes de datos", "De dónde sale cada dato y diagnóstico de conexión", onClick = { onOpen(MoreDestination.SOURCES) })
  MoreRow("Aviso de seguridad", "Lee esto antes de volar", onClick = { onOpen(MoreDestination.SAFETY) })
  MoreRow("Privacidad", "Qué datos usa la app y dónde quedan", onClick = { onOpen(MoreDestination.PRIVACY) })
  MoreRow("Apoyar", "GeoVisión Aérea Chiapas", onClick = { onOpen(MoreDestination.SUPPORT) })
  MoreRow("Acerca de", "Geoviento Mx v${BuildConfig.VERSION_NAME}", onClick = { onOpen(MoreDestination.ABOUT) })
}

@Composable
private fun MoreRow(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
  Card(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = Geo.Card)
  ) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.Bold, color = if (enabled) Geo.Text else Geo.Muted)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      }
      Text(if (enabled) "›" else "Próximamente", color = Geo.Muted, style = MaterialTheme.typography.bodySmall)
    }
  }
  Spacer(Modifier.height(8.dp))
}

// Mis drones: la lista completa de DJI (DRONES en Models.kt). Solo se
// muestran nombre y límite: no se inventan pesos ni tiempos de vuelo.
@Composable
fun DronesScreen(drone: DroneOption, onSelect: (DroneOption) -> Unit) {
  var filter by remember { mutableStateOf("") }
  Text("Mis drones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Text("Tu dron elegido mueve el semáforo y el informe PDF.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(12.dp))
  GeoCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(drone.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Geo.Text, modifier = Modifier.weight(1f))
        StatusChip("Principal", Geo.Accent)
      }
      Text("Límite de ráfaga: ${drone.limit} m/s (%.0f km/h)".format(drone.limit * 3.6), style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
    }
  }
  Spacer(Modifier.height(12.dp))
  OutlinedTextField(
    value = filter, onValueChange = { filter = it },
    placeholder = { Text("Buscar dron…", color = Geo.Muted) },
    modifier = Modifier.fillMaxWidth(),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = Geo.Text, unfocusedTextColor = Geo.Text,
      focusedBorderColor = Geo.Accent, unfocusedBorderColor = Geo.Muted, cursorColor = Geo.Accent
    )
  )
  Spacer(Modifier.height(8.dp))
  DRONES.filter { it.name.contains(filter, ignoreCase = true) }.forEach { d ->
    val selected = d.name == drone.name
    Card(
      onClick = { onSelect(d) },
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = if (selected) Geo.Card2 else Geo.Card)
    ) {
      Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(d.name, fontWeight = FontWeight.Bold, color = Geo.Text)
          Text("Límite de ráfaga: ${d.limit} m/s", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
        }
        if (selected) Text("✓ Principal", color = Geo.Accent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}

// Preferencias de vuelo: duración, margen de viento, tipo de operación,
// unidades y altura opcional. Se guardan en el teléfono al tocar Guardar.
@Composable
fun PrefsScreen(prefs: FlightPrefs, droneLimit: Double, onSave: (FlightPrefs) -> Unit) {
  var draft by remember(prefs) { mutableStateOf(prefs) }
  var saved by remember { mutableStateOf(false) }
  val effLimit = effectiveLimit(droneLimit, draft.windMarginPct)
  Text("Preferencias de vuelo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Spacer(Modifier.height(12.dp))
  SectionTitle("Duración estimada del vuelo")
  Spacer(Modifier.height(8.dp))
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (m in listOf(10, 15, 20, 30, 45, 60)) {
      FilterChip(
        selected = draft.flightMinutes == m,
        onClick = { draft = draft.copy(flightMinutes = m); saved = false },
        label = { Text("$m min") },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
          containerColor = Geo.Card, labelColor = Geo.Muted
        )
      )
    }
  }
  Spacer(Modifier.height(12.dp))
  SectionTitle("Margen de viento: ${draft.windMarginPct}%")
  Text(
    "El margen reduce el límite efectivo del semáforo: con tu dron (${droneLimit} m/s) y ${draft.windMarginPct}%, el rojo empieza en %.1f m/s.".format(effLimit),
    style = MaterialTheme.typography.bodySmall, color = Geo.Muted
  )
  Slider(
    value = draft.windMarginPct.toFloat(),
    onValueChange = { draft = draft.copy(windMarginPct = it.roundToInt().coerceIn(0, 50)); saved = false },
    valueRange = 0f..50f
  )
  Text("0% (límite tal cual) — 50% (la mitad del límite)", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(12.dp))
  SectionTitle("Tipo de operación")
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (op in OperationType.values()) {
      FilterChip(
        selected = draft.operation == op,
        onClick = { draft = draft.copy(operation = op); saved = false },
        label = { Text(op.label) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
          containerColor = Geo.Card, labelColor = Geo.Muted
        )
      )
    }
  }
  Spacer(Modifier.height(12.dp))
  SectionTitle("Unidad de velocidad")
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (u in SpeedUnit.values()) {
      FilterChip(
        selected = draft.speedUnit == u,
        onClick = { draft = draft.copy(speedUnit = u); saved = false },
        label = { Text(u.label) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
          containerColor = Geo.Card, labelColor = Geo.Muted
        )
      )
    }
  }
  Spacer(Modifier.height(12.dp))
  SectionTitle("Unidad de temperatura")
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (u in TempUnit.values()) {
      FilterChip(
        selected = draft.tempUnit == u,
        onClick = { draft = draft.copy(tempUnit = u); saved = false },
        label = { Text(u.label) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
          containerColor = Geo.Card, labelColor = Geo.Muted
        )
      )
    }
  }
  Spacer(Modifier.height(12.dp))
  SectionTitle("Altura opcional del vuelo: ${draft.plannedAltitudeM} m")
  Text("Se usa en el diagnóstico del viento a la altura prevista.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Slider(
    value = draft.plannedAltitudeM.toFloat(),
    onValueChange = { draft = draft.copy(plannedAltitudeM = it.roundToInt().coerceIn(10, 120)); saved = false },
    valueRange = 10f..120f
  )
  Text("10 m — 120 m", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(12.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
      onClick = { onSave(draft); saved = true },
      colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent, contentColor = Geo.Text)
    ) { Text("Guardar") }
    OutlinedButton(onClick = { draft = FlightPrefs.DEFAULT; saved = false }) { Text("Restablecer") }
  }
  if (saved) {
    Spacer(Modifier.height(8.dp))
    Text("Preferencias guardadas.", color = Geo.Green, style = MaterialTheme.typography.bodySmall)
  }
}

// Motivos del estado de vuelo: cada factor con su chip de estado y el
// pie con fecha/hora de evaluación, fuente y hora de consulta.
@Composable
fun ReasonsScreen(report: WindReport, origin: String, prefs: FlightPrefs, gpsPlace: Boolean, droneLimit: Double) {
  val factors = computeFactors(report, origin, prefs, gpsPlace, droneLimit)
  val overall = overallStatus(factors)
  Text("Motivos del estado de vuelo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Spacer(Modifier.height(8.dp))
  Text(
    "Estado general: ${overallTitle(overall)}",
    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = factorChipColor(overall)
  )
  Text("Un factor en «Pendiente» no bloquea el vuelo: solo avisa que ese dato no se pudo comprobar.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(12.dp))
  factors.forEach { f ->
    GeoCard(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(f.name, fontWeight = FontWeight.Bold, color = Geo.Text, modifier = Modifier.weight(1f))
          StatusChip(factorChipLabel(f.status), factorChipColor(f.status))
        }
        Spacer(Modifier.height(4.dp))
        Text(f.detail, style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      }
    }
    Spacer(Modifier.height(8.dp))
  }
  val evaluated = LocalDateTime.now(ZoneId.of("America/Mexico_City"))
    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
  Text(
    "Evaluado: $evaluated · Fuente: ${report.source} · Consultado: ${report.now.time ?: "—"}",
    style = MaterialTheme.typography.bodySmall, color = Geo.Muted
  )
}

// Apariencia y ubicación: el tema oscuro es la identidad fija de la app;
// aquí se cambia la ubicación y se vuelve a Tuxtla.
@Composable
fun AppearanceScreen(placeName: String, onUseGps: () -> Unit, onBackToTuxtla: () -> Unit) {
  Text("Apariencia y ubicación", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Spacer(Modifier.height(12.dp))
  Text("Ubicación actual: $placeName", style = MaterialTheme.typography.bodyMedium, color = Geo.Text)
  Spacer(Modifier.height(8.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = onUseGps, colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent, contentColor = Geo.Text)) { Text("Usar mi ubicación") }
    OutlinedButton(onClick = onBackToTuxtla) { Text("Volver a Tuxtla") }
  }
  Spacer(Modifier.height(12.dp))
  Text("Tema: oscuro. Es la identidad de Geoviento Mx y no se cambia.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
}

// Fuentes de datos: de dónde sale cada dato, el diagnóstico de conexión
// y los enlaces oficiales de espacio aéreo (SENEAM). El espacio aéreo
// NO se verifica en vivo en esta versión: está en "Próximamente".
@Composable
fun SourcesScreen(
  diag: List<DiagEntry>,
  lastAttempt: String?,
  error: String?,
  loading: Boolean,
  onTestConnection: () -> Unit,
  onCopyDiag: () -> Unit
) {
  val uriHandler = LocalUriHandler.current
  Text("Fuentes de datos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Spacer(Modifier.height(12.dp))
  GeoCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
      Text("Diagnóstico de conexión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Geo.Text)
      lastAttempt?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Geo.Muted) }
      if (diag.isEmpty()) {
        Text("Sin pruebas todavía. Toca 'Probar conexión' para ver qué fuente responde y cuál falla.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      } else {
        for (d in diag) {
          Text((if (d.ok) "✅ " else "❌ ") + d.source + " — " + d.detail + " (" + d.ms + " ms)", style = MaterialTheme.typography.bodySmall, color = Geo.Text)
        }
      }
      error?.let { Text(it, color = Geo.ErrorText, style = MaterialTheme.typography.bodySmall) }
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = onTestConnection,
          enabled = !loading,
          colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent, contentColor = Geo.Text)
        ) { Text("Probar conexión") }
        OutlinedButton(onClick = onCopyDiag) { Text("Copiar diagnóstico") }
      }
    }
  }
  Spacer(Modifier.height(12.dp))
  Text("Viento y clima: Open-Meteo (pronóstico y archivo histórico ERA5). Respaldo: met.no. Calidad del aire: Open-Meteo Air Quality. Elevación del terreno: Open-Meteo. Índice Kp: NOAA SWPC. Radar de lluvia: RainViewer. Mapa: Leaflet con teselas CartoDB Dark Matter (respaldo OpenStreetMap).", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(12.dp))
  SectionTitle("Espacio aéreo (México)")
  Text("GeoViento no verifica el espacio aéreo en vivo. Antes de volar, revisa:", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(4.dp))
  TextButton(onClick = { uriHandler.openUri("https://aipmexico.seneam.gob.mx") }) {
    Text("SENEAM — Publicación de Información Aeronáutica (AIP México)", color = Geo.Accent)
  }
  TextButton(onClick = { uriHandler.openUri("https://apps.seneam.gob.mx/notam/") }) {
    Text("SENEAM — NOTAM", color = Geo.Accent)
  }
  TextButton(onClick = { uriHandler.openUri("https://www.gob.mx/afac/acciones-y-programas/rpas-drones") }) {
    Text("AFAC — RPAS / Drones (NOM-107-SCT3-2019)", color = Geo.Accent)
  }
}

@Composable
fun SafetyScreen() {
  Text("Aviso de seguridad", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Spacer(Modifier.height(12.dp))
  Text("Esta app muestra las condiciones al momento de la consulta, pensada para planear el día. El viento puede cambiar en minutos: antes de cada despegue, vuelve a revisar la fuente en vivo. Ante cualquier duda, la decisión segura es no volar.", style = MaterialTheme.typography.bodyMedium, color = Geo.Text)
  Spacer(Modifier.height(8.dp))
  Text("• Mantén el dron siempre a la vista.\n• No vueles sobre personas ni cerca de aeropuertos.\n• Verifica el espacio aéreo (SENEAM / AFAC) antes de despegar.\n• El semáforo es una ayuda, no un permiso: tú decides y tú respondes.", style = MaterialTheme.typography.bodyMedium, color = Geo.Muted)
}

@Composable
fun PrivacyScreen() {
  Text("Privacidad", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Spacer(Modifier.height(12.dp))
  Text("Geoviento Mx no tiene cuentas ni registra usuarios. Tu ubicación solo se usa para pedir el pronóstico de ese lugar a Open-Meteo (y el respaldo met.no) y no se guarda en ningún servidor. Tus preferencias y tu dron elegido quedan guardados solo en tu teléfono. El informe PDF se genera en tu teléfono y se guarda en Descargas.", style = MaterialTheme.typography.bodyMedium, color = Geo.Text)
}

@Composable
fun SupportScreen() {
  val uriHandler = LocalUriHandler.current
  Text("Apoyar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Spacer(Modifier.height(12.dp))
  Text("Geoviento Mx es de GeoVisión Aérea Chiapas: foto con dron y recorridos 360° para vender terrenos y casas en Tuxtla Gutiérrez.", style = MaterialTheme.typography.bodyMedium, color = Geo.Text)
  Spacer(Modifier.height(8.dp))
  TextButton(onClick = { uriHandler.openUri("https://wa.me/529615147666") }) {
    Text("WhatsApp GeoVisión: 961 514 7666", color = Geo.Accent)
  }
}

@Composable
fun AboutScreen() {
  Text("Acerca de", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Spacer(Modifier.height(12.dp))
  Text("Geoviento Mx v${BuildConfig.VERSION_NAME} (código ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Geo.Text)
  Text("Viento para dron, con datos en vivo de Open-Meteo y respaldo met.no. Hecha por GeoVisión Aérea Chiapas, Tuxtla Gutiérrez.", style = MaterialTheme.typography.bodyMedium, color = Geo.Muted)
}

// Informe técnico de vuelo en PDF: la sección que ya tenía la app,
// movida aquí sin cambiar su lógica (ubicación, día, hora, altura,
// consulta del día, descarga). El estado del formulario vive en esta
// pantalla y se inicia con el lugar principal.
@Composable
fun PdfReportScreen(
  vm: WindViewModel,
  report: WindReport,
  lat: Double,
  lon: Double,
  placeName: String,
  drone: DroneOption,
  checklist: List<Boolean>
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val dayReport by vm.dayReport.collectAsState()
  val dayLoading by vm.dayLoading.collectAsState()
  val dayError by vm.dayError.collectAsState()
  val weekRef by vm.weekRef.collectAsState()
  val places by vm.places.collectAsState()

  var pdfPlace by remember { mutableStateOf(placeName) }
  var pdfLat by remember { mutableStateOf("%.4f".format(lat)) }
  var pdfLon by remember { mutableStateOf("%.4f".format(lon)) }
  var pdfQuery by remember { mutableStateOf("") }
  var pdfDay by remember { mutableStateOf(vm.today()) }
  var pdfHour by remember { mutableStateOf("08:00") }
  var pdfAltitude by remember { mutableStateOf(60) }
  var pdfBusy by remember { mutableStateOf(false) }

  Text("Informe técnico de vuelo en PDF", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Text("HOY O UN DÍA PASADO · CUALQUIER UBICACIÓN", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Muted)
  val pdfIsTuxtla = (pdfLat.toDoubleOrNull()?.let { kotlin.math.abs(it - 16.75) < 0.5 } ?: false) && (pdfLon.toDoubleOrNull()?.let { kotlin.math.abs(it - (-93.11)) < 0.5 } ?: false)
  Text(
    "Elige la ubicación y el día, consulta los datos y descarga el informe PDF listo para guardar junto a tu bitácora de vuelo. Para hoy usa el pronóstico de Open-Meteo junto con el archivo histórico de Open-Meteo (ERA5). El PDF incluye datos de la hora elegida, tabla hora por hora, viento estimado a tu altura, la orientación de las corrientes: de dónde viene el aire y hacia dónde se mueve" + if (pdfIsTuxtla) " y cómo lo modifican el Cañón del Sumidero y la Meseta de Copoya." else ".",
    style = MaterialTheme.typography.bodySmall, color = Geo.Muted
  )
  Spacer(Modifier.height(8.dp))
  Text("Ubicación del vuelo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Text("Sitio seleccionado: $pdfPlace", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  OutlinedTextField(
    value = pdfQuery, onValueChange = { pdfQuery = it },
    label = { Text("Buscar otra ubicación por nombre", color = Geo.Muted) },
    modifier = Modifier.fillMaxWidth(),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = {}),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = Geo.Text, unfocusedTextColor = Geo.Text,
      focusedBorderColor = Geo.Accent, unfocusedBorderColor = Geo.Muted, cursorColor = Geo.Accent
    )
  )
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
      onClick = { vm.searchPlaces(pdfQuery) },
      colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent, contentColor = Geo.Text)
    ) { Text("Buscar") }
    OutlinedButton(onClick = {
      pdfLat = "%.4f".format(lat); pdfLon = "%.4f".format(lon)
      pdfPlace = "Mi ubicación actual (%.4f, %.4f)".format(lat, lon)
    }) { Text("Usar mi ubicación actual") }
  }
  places.forEach { p ->
    TextButton(onClick = {
      pdfPlace = if (p.detail.isBlank()) p.name else "${p.name}, ${p.detail}"
      pdfLat = "%.4f".format(p.lat); pdfLon = "%.4f".format(p.lon)
      vm.places.value = emptyList()
    }) { Text("${p.name} — ${p.detail}", color = Geo.Accent) }
  }
  Spacer(Modifier.height(8.dp))
  Text("Latitud: $pdfLat   Longitud: $pdfLon", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(8.dp))
  Text("Día del informe", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  val today = vm.today()
  val dayOptions = listOf(0, 1, 2, 4, 7).map { today.minusDays(it.toLong()) }
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (d in dayOptions) {
      val label = when (d) {
        today -> "Hoy · ${d.dayOfMonth} ${d.month.name.take(3).lowercase()}"
        today.minusDays(1) -> "Ayer · ${d.dayOfMonth} ${d.month.name.take(3).lowercase()}"
        else -> "${d.dayOfMonth} ${d.month.name.take(3).lowercase()}"
      }
      FilterChip(
        selected = pdfDay == d,
        onClick = { pdfDay = d },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
          containerColor = Geo.Card, labelColor = Geo.Muted
        )
      )
    }
  }
  Spacer(Modifier.height(8.dp))
  Text("Hora del vuelo: $pdfHour   •   Altura planeada: $pdfAltitude m", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (h in listOf("06:00", "08:00", "10:00", "12:00", "14:00", "16:00", "18:00")) {
      FilterChip(
        selected = pdfHour == h,
        onClick = { pdfHour = h },
        label = { Text(h) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
          containerColor = Geo.Card, labelColor = Geo.Muted
        )
      )
    }
  }
  Slider(value = pdfAltitude.toFloat(), onValueChange = { pdfAltitude = it.toInt().coerceIn(10, 120) }, valueRange = 10f..120f)
  if (weekRef.size >= 4) {
    Text(
      "Referencia rápida del archivo: en los últimos 7 días la ráfaga máxima diaria fue de %.1f a %.1f m/s, con viento dominante del %.0f° al %.0f° — en rojo para un dron de menos de 249 g.".format(weekRef[0], weekRef[1], weekRef[2], weekRef[3]),
      style = MaterialTheme.typography.bodySmall, color = Geo.Muted
    )
  }
  Spacer(Modifier.height(8.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
      onClick = {
        val la = pdfLat.toDoubleOrNull() ?: lat
        val lo = pdfLon.toDoubleOrNull() ?: lon
        vm.consultDay(la, lo, pdfDay)
      },
      enabled = !dayLoading,
      colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent, contentColor = Geo.Text)
    ) { Text(if (dayLoading) "Consultando…" else "Consultar datos del día") }
    Button(
      onClick = {
        if (!pdfBusy) {
          pdfBusy = true
          scope.launch {
            val la = pdfLat.toDoubleOrNull() ?: lat
            val lo = pdfLon.toDoubleOrNull() ?: lon
            val uri = withContext(Dispatchers.IO) {
              PdfReport.generate(context, report, dayReport, la, lo, pdfPlace, pdfHour, pdfAltitude, drone, weekRef, checklist.all { it })
            }
            pdfBusy = false
            if (uri != null) {
              Toast.makeText(context, "Informe guardado en Descargas", Toast.LENGTH_LONG).show()
              try {
                val open = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                context.startActivity(open)
              } catch (_: Exception) { /* sin visor de PDF: el archivo ya quedó en Descargas */ }
            } else {
              Toast.makeText(context, "No se pudo generar el PDF", Toast.LENGTH_LONG).show()
            }
          }
        }
      },
      enabled = !pdfBusy,
      colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent, contentColor = Geo.Text)
    ) { Text(if (pdfBusy) "Generando…" else "Descargar informe PDF") }
  }
  dayError?.let { Text(it, color = Geo.ErrorText, style = MaterialTheme.typography.bodySmall) }
  dayReport?.let { dr ->
    Text(
      "Consultado: ${dr.date} — ráfaga máxima %.1f m/s a las %s, dirección dominante %s. Fuente: ${dr.source}.".format(
        dr.maxGust, dr.maxGustHour?.substringAfter("T")?.substringBefore(":") ?: "—", dr.dominantDir?.let { compass(it) } ?: "—"
      ),
      style = MaterialTheme.typography.bodySmall, color = Geo.Muted
    )
  }
  Text("Listo. Consulta primero para ver el resumen aquí, o descarga directo: el PDF se genera en tu teléfono con los datos consultados en ese momento.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(12.dp))
  Text("Fuentes de los datos: Open-Meteo (pronóstico y archivo histórico ERA5) y met.no (respaldo). Calidad del aire: Open-Meteo Air Quality.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Text("Esta app muestra las condiciones al momento de la consulta, pensada para planear el día. El viento puede cambiar en minutos: antes de cada despegue, vuelve a revisar la fuente en vivo. Ante cualquier duda, la decisión segura es no volar.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
}
