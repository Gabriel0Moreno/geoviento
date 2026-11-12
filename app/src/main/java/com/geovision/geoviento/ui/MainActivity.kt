package com.geovision.geoviento.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.geovision.geoviento.BuildConfig
import com.geovision.geoviento.data.DRONES
import com.geovision.geoviento.data.HourlyPoint
import com.geovision.geoviento.data.WindReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {
  // Tuxtla por defecto si aún no hay GPS: 16.75, -93.11
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // PERMANENTE (Gabo, 2026-11-11): si la app se cierra de forma
    // inesperada, el error exacto queda guardado en un txt, SIEMPRE. No
    // quitar en versiones futuras. Guarda en el almacenamiento interno de
    // la app (ahí siempre se puede escribir, con nombre por fecha para no
    // pisar el anterior) y una copia en Descargas. OJO: esto solo atrapa
    // errores de la app en Java/Kotlin; un fallo NATIVO (como el del mapa
    // MapLibre, que nunca dejó archivo) no lo puede atrapar nadie desde
    // aquí, por eso el mapa ya no usa código nativo.
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
      try {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "geoviento-crash-$stamp.txt"
        val text = buildString {
          appendLine("Geoviento Mx — cierre inesperado")
          appendLine("Fecha: $stamp")
          appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
          appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
          appendLine("Teléfono: ${Build.MANUFACTURER} ${Build.MODEL}")
          appendLine("Hilo: ${thread.name}")
          appendLine("Último estado: ${CrashLog.lastState}")
          appendLine("Error:")
          appendLine(Log.getStackTraceString(e))
        }
        val dir = File(filesDir, "crashes").apply { mkdirs() }
        File(dir, fileName).writeText(text)
        try {
          val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
          }
          val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
          uri?.let { contentResolver.openOutputStream(it)?.use { os -> os.write(text.toByteArray()) } }
        } catch (_: Exception) { /* la copia interna ya quedó guardada */ }
      } catch (_: Exception) { /* si no se puede guardar, el cierre sigue su curso */ }
      previousHandler?.uncaughtException(thread, e)
    }
    val fused = LocationServices.getFusedLocationProviderClient(this)
    var onPermission: ((Boolean) -> Unit)? = null
    val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      onPermission?.invoke(granted)
    }
    setContent {
      // Fábrica explícita: el ViewModel necesita la Application y la fábrica
      // por defecto no siempre lo crea bien; sin esto la app se cerraba
      // apenas al abrir (pantalla del logo y de regreso al cajón).
      val vm: WindViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          @Suppress("UNCHECKED_CAST")
          return WindViewModel(application) as T
        }
      })
      val rep by vm.report.collectAsState()
      val origin by vm.origin.collectAsState()
      val err by vm.error.collectAsState()
      val loading by vm.loading.collectAsState()
      val diag by vm.diag.collectAsState()
      val lastAttempt by vm.lastAttempt.collectAsState()
      val drone by vm.drone.collectAsState()
      val dayReport by vm.dayReport.collectAsState()
      val dayLoading by vm.dayLoading.collectAsState()
      val dayError by vm.dayError.collectAsState()
      val weekRef by vm.weekRef.collectAsState()
      val places by vm.places.collectAsState()
      val terrain by vm.terrain.collectAsState()
      val terrainLoading by vm.terrainLoading.collectAsState()
      val context = LocalContext.current
      val focusManager = LocalFocusManager.current
      var lat by remember { mutableStateOf(16.75) }
      var lon by remember { mutableStateOf(-93.11) }
      // Nombre del lugar principal (arriba): cambia con la búsqueda o el GPS.
      var placeName by remember { mutableStateOf("Tuxtla Gutiérrez, Chiapas") }
      var mainQuery by remember { mutableStateOf("") }
      val mainPlaces by vm.mainPlaces.collectAsState()
      var hasPermission by remember { mutableStateOf(false) }
      val scope = rememberCoroutineScope()

      fun useGps() {
        if (hasPermission) {
          loadGps(fused, { la, lo -> lat = la; lon = lo; placeName = "Mi ubicación actual" }, { vm.refresh(lat, lon) })
        } else {
          perm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
      }

      // Permiso de ubicación después del primer frame (no en onCreate),
      // y la primera carga con la ubicación por defecto de Tuxtla.
      // Se quita el foco (y el teclado) por si el campo de búsqueda del
      // informe lo agarró solo al abrir: el teclado no debe salir al entrar.
      LaunchedEffect(Unit) {
        focusManager.clearFocus()
        onPermission = { granted ->
          hasPermission = granted
          if (granted) useGps()
        }
        perm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        vm.refresh(lat, lon)
      }
      // Actualización automática cada 15 min con la ubicación actual (un solo ciclo).
      LaunchedEffect(Unit) {
        while (true) { delay(15 * 60 * 1000L); vm.refresh(lat, lon) }
      }

      // Estado de la demo: hora seleccionada, corrientes, zonas y checklist.
      var selectedHour by remember { mutableStateOf(-1) } // -1 = ahora
      var showCurrents by remember { mutableStateOf(true) }
      var playing by remember { mutableStateOf(true) }
      var intensity by remember { mutableStateOf(1f) }
      var selectedZone by remember { mutableStateOf<String?>(null) }
      // El mapa real es un WebView (Leaflet + OSM), no MapLibre: el mapa
      // nativo tumbaba la app al cargarlo con un fallo que el handler de
      // Java no atrapa (nunca dejó geoviento-crash.txt). El WebView se
      // carga directo y, si falla, cae al esquema Canvas de antes.
      var checklist by remember { mutableStateOf(List(6) { false }) }
      var pdfBusy by remember { mutableStateOf(false) }

      // Estado de la sección del informe PDF (como en la demo).
      var pdfPlace by remember { mutableStateOf("Centro de Tuxtla Gutiérrez, Chiapas") }
      var pdfLat by remember { mutableStateOf("16.7552") }
      var pdfLon by remember { mutableStateOf("-93.1154") }
      var pdfQuery by remember { mutableStateOf("") }
      var pdfDay by remember { mutableStateOf(vm.today()) }
      var pdfHour by remember { mutableStateOf("08:00") }
      var pdfAltitude by remember { mutableStateOf(60) }

      // Último estado conocido, para el txt de cierre (ver onCreate).
      LaunchedEffect(rep, origin) {
        CrashLog.lastState = "origen=$origin, fuente=${rep.source}, hora=${rep.now.time}, ráfaga=${rep.now.gust10} m/s, lugar=$lat,$lon ($placeName)"
      }

      // El perfil del relieve sigue al lugar y al viento (v1.2.3): se pide
      // cuando llegan datos nuevos del viento (que es cuando cambia el
      // lugar o gira el viento).
      LaunchedEffect(rep) {
        vm.refreshTerrain(lat, lon, rep.now.dir10)
      }

      // La interfaz SIEMPRE se ve: si aún no hay datos en vivo, se muestra el
      // ejemplo de la demo (marcado) o los datos guardados. Nunca pantalla blanca.
      val report: WindReport = rep
      val limit = drone.limit
      // Las zonas y el cañón son de Tuxtla: solo se muestran cuando el
      // lugar principal está cerca de Tuxtla. El relieve ahora es real
      // para cualquier lugar (v1.2.3), así que ya no va en este filtro.
      val isTuxtla = abs(lat - 16.75) < 0.5 && abs(lon - (-93.11)) < 0.5
      MaterialTheme {
        // Tocar en cualquier parte vacía esconde el teclado.
        Column(
          Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusManager.clearFocus() }
        ) {
          // Encabezado de marca (como en la demo).
          Text("Geoviento Mx — Viento para dron", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0A2E4D))
          Text("${placeName.uppercase(Locale.getDefault())} · %.3f° N, %.3f° O · %s M S. N. M.".format(lat, -lon, report.surface?.elevation?.let { "%.0f".format(it) } ?: "535"), style = MaterialTheme.typography.bodySmall)
          Text("Datos consultados: " + (report.now.time ?: "—") + "  •  Fuente: " + report.source, style = MaterialTheme.typography.bodySmall)
          // Ubicación principal cambiable: busca un lugar por nombre o usa el GPS.
          OutlinedTextField(
            value = mainQuery, onValueChange = { mainQuery = it },
            label = { Text("Buscar lugar para el viento") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.searchMainPlaces(mainQuery); focusManager.clearFocus() }) { Text("Buscar") }
            OutlinedButton(onClick = { useGps() }) { Text("Mi ubicación") }
          }
          mainPlaces.forEach { p ->
            TextButton(onClick = {
              placeName = if (p.detail.isBlank()) p.name else "${p.name}, ${p.detail}"
              lat = p.lat; lon = p.lon
              vm.mainPlaces.value = emptyList()
              vm.refresh(lat, lon)
              focusManager.clearFocus()
            }) { Text("${p.name} — ${p.detail}") }
          }
          if (!hasPermission) Text("Sin permiso de ubicación: se usan las coordenadas de Tuxtla.", style = MaterialTheme.typography.bodySmall)
          if (loading) Text("Actualizando…", style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(12.dp))

          // Aviso de origen de los datos (siempre visible).
          when (origin) {
            "ejemplo" -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))) {
              Column(Modifier.padding(12.dp)) {
                Text("EJEMPLO — Datos de la demo de ayer", fontWeight = FontWeight.Bold)
                Text("Esto NO es el viento de ahora. La interfaz se ve completa para que la revises mientras la app intenta traer datos en vivo. No vueles con estos datos.", style = MaterialTheme.typography.bodySmall)
              }
            }
            "guardado" -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))) {
              Column(Modifier.padding(12.dp)) {
                Text("Datos GUARDADOS de la última vez que sí cargó", fontWeight = FontWeight.Bold)
                Text("Pueden estar viejos. La app está intentando actualizar en vivo.", style = MaterialTheme.typography.bodySmall)
              }
            }
            else -> Text("Datos EN VIVO", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF1B7A3D))
          }
          Spacer(Modifier.height(12.dp))

          // Diagnóstico de conexión: qué funciona y qué no.
          Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EEF2))) {
            Column(Modifier.padding(12.dp)) {
              Text("Diagnóstico de conexión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              lastAttempt?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
              if (diag.isEmpty()) {
                Text("Sin pruebas todavía. Toca 'Probar conexión' para ver qué fuente responde y cuál falla.", style = MaterialTheme.typography.bodySmall)
              } else {
                for (d in diag) {
                  Text((if (d.ok) "✅ " else "❌ ") + d.source + " — " + d.detail + " (" + d.ms + " ms)", style = MaterialTheme.typography.bodySmall)
                }
              }
              err?.let { Text(it, color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall) }
              Spacer(Modifier.height(8.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.testConnection(lat, lon) }, enabled = !loading) { Text("Probar conexión") }
                OutlinedButton(onClick = {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Geoviento", vm.diagText(lat, lon)))
                  Toast.makeText(context, "Diagnóstico copiado, pégalo en el chat", Toast.LENGTH_LONG).show()
                }) { Text("Copiar diagnóstico") }
              }
            }
          }
          Spacer(Modifier.height(12.dp))

          val n = report.now
          val level = flightLevel(n.gust10, limit)
          val levelColor = when (level) {
            FlightLevel.GREEN -> Color(0xFF1B7A3D)
            FlightLevel.YELLOW -> Color(0xFF8A6D00)
            FlightLevel.RED -> Color(0xFFB3261E)
          }
          // 1) Veredicto (semáforo) — como en la demo.
          Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = levelColor)) {
            Column(Modifier.padding(16.dp)) {
              Text(level.label, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
              Text("Ráfaga 10 m: %.1f m/s (%.0f km/h)".format(n.gust10, kmh(n.gust10)), color = Color.White, style = MaterialTheme.typography.titleMedium)
              Text(level.advice, color = Color.White)
              Text("La ráfaga manda, no el viento medio.", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
          }
          Spacer(Modifier.height(8.dp))
          // Tu dron: el límite elegido mueve el semáforo y el informe PDF.
          Text("Tu dron (límite por ráfaga)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in DRONES) {
              FilterChip(
                selected = drone.name == d.name,
                onClick = { vm.drone.value = d },
                label = { Text("${d.name} · ${d.limit} m/s") }
              )
            }
          }
          Spacer(Modifier.height(12.dp))

          // 2) Condiciones actuales — como en la demo.
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Viento sostenido", "%.1f m/s".format(n.speed10), "%.0f km/h  •  %s %.0f°".format(kmh(n.speed10), compass(n.dir10), n.dir10), Modifier.weight(1f))
            StatCard("Ráfaga máxima de hoy", "%.1f m/s".format(report.surface?.gustMaxToday ?: n.gust10), "%.0f km/h".format(kmh(report.surface?.gustMaxToday ?: n.gust10)), Modifier.weight(1f))
            StatCard("Temperatura", n.temp?.let { "%.1f °C".format(it) } ?: "—", "Máxima de hoy: " + (report.surface?.tempMaxToday?.let { "%.0f °C".format(it) } ?: "—"), Modifier.weight(1f))
          }
          Spacer(Modifier.height(12.dp))

          // Aviso de la tarde + condiciones en superficie + leyenda.
          AfternoonWarning(report, limit)
          Spacer(Modifier.height(12.dp))
          SurfaceConditions(report, limit)
          Spacer(Modifier.height(12.dp))
          SemaphoreLegend(limit)
          Spacer(Modifier.height(12.dp))

          // 3) Brújula + dirección.
          // Giro de la tarde POR DATOS (v1.2.2): antes decía fijo "gira
          // al NO", el patrón de Tuxtla; en otra ciudad era información
          // incorrecta. Solo se muestra si la tarde gira más de 45°.
          val aftPoint = report.hourly.filter { (it.time.substringAfter("T").substringBefore(":").toIntOrNull() ?: 0) in 15..18 }.maxByOrNull { it.gust10 }
          val aftTurnDir = aftPoint?.takeIf { kotlin.math.abs((it.dir10 - n.dir10 + 540) % 360 - 180) > 45 }?.dir10
          Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
              WindCompass(n.dir10)
              Spacer(Modifier.width(16.dp))
              Column {
                Text("Dirección del viento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Viene del %s (%.0f°)".format(compass(n.dir10), n.dir10))
                Text("El aire va hacia el %s".format(compass((n.dir10 + 180.0) % 360.0)))
                Text("A 120 m viene del %s".format(n.dir120?.let { compass(it) } ?: "—"))
                if (aftTurnDir != null) Text("Por la tarde el viento gira al ${compass(aftTurnDir)}.", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
          Spacer(Modifier.height(12.dp))

          // 4) Gráfica de líneas hora por hora — como en la demo.
          HourlyLineChart(report.hourly, limit)
          Spacer(Modifier.height(12.dp))

          // 5) Ventanas del día — como en la demo.
          WindowCards(report.hourly, limit)
          Spacer(Modifier.height(12.dp))

          // 6) Mapa real con corrientes y zonas — como en la demo.
          Text("Mapa: zonas de vuelo y relieve", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          if (isTuxtla) {
            Text("El valle de Tuxtla corre de oeste a este entre la Meseta de Copoya (sur) y la entrada del Cañón del Sumidero (norte). Con viento del norte y noroeste, el cañón canaliza el aire hacia la ciudad y la meseta genera turbulencia en su lado protegido. La herramienta de corrientes dibuja el flujo directamente sobre el mapa, hora por hora: elige la hora y sigue las flechas. Toca una zona para ubicarla en el mapa.", style = MaterialTheme.typography.bodySmall)
          } else {
            Text("Mapa real con las corrientes de viento sobre las calles de este lugar, hora por hora: elige la hora y sigue las flechas.", style = MaterialTheme.typography.bodySmall)
          }
          Spacer(Modifier.height(8.dp))
          val hourPoint: HourlyPoint? = if (selectedHour in report.hourly.indices) report.hourly[selectedHour] else null
          val mapDir = hourPoint?.dir10 ?: n.dir10
          val mapGust = hourPoint?.gust10 ?: n.gust10
          Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedHour == -1, onClick = { selectedHour = -1 }, label = { Text("Ahora") })
            for ((i, p) in report.hourly.take(24).withIndex()) {
              FilterChip(selected = selectedHour == i, onClick = { selectedHour = i }, label = { Text(hourLabel(p.time)) })
            }
          }
          Spacer(Modifier.height(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { showCurrents = !showCurrents }) { Text(if (showCurrents) "Ocultar corrientes" else "Corrientes visibles") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { playing = !playing }) { Text(if (playing) "Pausar animación" else "Reanudar animación") }
          }
          Spacer(Modifier.height(8.dp))
          Text("Intensidad del flujo", style = MaterialTheme.typography.bodyMedium)
          Slider(value = intensity, onValueChange = { intensity = it }, valueRange = 0.5f..2f)
          if (showCurrents) {
            WebMap(lat, lon, mapDir, mapGust, limit, playing, intensity, selectedZone, { selectedZone = it }) {
              CurrentsMap(mapDir, mapGust, playing, intensity, selectedZone, { selectedZone = it }, Modifier.fillMaxWidth().height(220.dp))
            }
          } else {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Text("Corrientes ocultas") }
          }
          Spacer(Modifier.height(8.dp))
          if (isTuxtla) {
            Text("En la boca del cañón, la canalización puede subir la ráfaga local a ≈ %.1f m/s.".format(mapGust * 1.45), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            ZoneCards(selectedZone) { selectedZone = it }
          } else {
            // En otra ciudad las zonas, el relieve y el cañón son de
            // Tuxtla y no aplican: se explica en vez de que la
            // información "desaparezca" sin más (v1.2.2).
            Text("Las 5 zonas numeradas y el corte del relieve son de Tuxtla Gutiérrez; aquí el mapa muestra las calles y las flechas de este lugar.", style = MaterialTheme.typography.bodySmall)
          }
          Spacer(Modifier.height(12.dp))

          // 7) Cómo se mueve el aire sobre el relieve: perfil REAL del
          // terreno del lugar elegido (v1.2.3). En Tuxtla se conservan
          // además las 3 explicaciones del valle y el cañón.
          ReliefSection(terrain, terrainLoading, isTuxtla)
          Spacer(Modifier.height(12.dp))

          // 8) Calculadora de viento por altura — como en la demo.
          AltitudeCalculator(report, limit) {}
          Spacer(Modifier.height(8.dp))
          Text("10 m: %.1f m/s, ráfaga %.1f m/s".format(n.speed10, n.gust10))
          Text("80 m: " + (n.speed80?.let { "%.1f m/s".format(it) } ?: "—") + (n.dir80?.let { " (%s)".format(compass(it)) } ?: ""))
          Text("120 m: " + (n.speed120?.let { "%.1f m/s".format(it) } ?: "—") + (n.dir120?.let { " (%s)".format(compass(it)) } ?: "") + ", ráfaga est. " + (n.gust120Est?.let { "%.1f m/s".format(it) } ?: "—"))
          Spacer(Modifier.height(12.dp))

          // 9) Checklist antes de despegar — como en la demo.
          PreflightChecklist(report, limit, checklist) { i ->
            checklist = checklist.mapIndexed { j, v -> if (j == i) !v else v }
          }
          Spacer(Modifier.height(12.dp))

          // 10) Informe técnico de vuelo en PDF — como en la demo.
          Text("Informe técnico de vuelo en PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text("HOY O UN DÍA PASADO · CUALQUIER UBICACIÓN", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
          // La mención al cañón y la meseta solo aplica si el informe es
          // de Tuxtla; en otra ciudad sería información incorrecta (v1.2.2).
          val pdfIsTuxtla = (pdfLat.toDoubleOrNull()?.let { kotlin.math.abs(it - 16.75) < 0.5 } ?: false) && (pdfLon.toDoubleOrNull()?.let { kotlin.math.abs(it - (-93.11)) < 0.5 } ?: false)
          Text("Elige la ubicación y el día, consulta los datos y descarga el informe PDF listo para guardar junto a tu bitácora de vuelo. Para hoy usa el pronóstico de Open-Meteo junto con el archivo histórico de Open-Meteo (ERA5). El PDF incluye datos de la hora elegida, tabla hora por hora, viento estimado a tu altura, la orientación de las corrientes: de dónde viene el aire y hacia dónde se mueve" + if (pdfIsTuxtla) " y cómo lo modifican el Cañón del Sumidero y la Meseta de Copoya." else ".", style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(8.dp))
          Text("Ubicación del vuelo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
          Text("Sitio seleccionado: $pdfPlace", style = MaterialTheme.typography.bodySmall)
          OutlinedTextField(
            value = pdfQuery, onValueChange = { pdfQuery = it },
            label = { Text("Buscar otra ubicación por nombre") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.searchPlaces(pdfQuery); focusManager.clearFocus() }) { Text("Buscar") }
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
            }) { Text("${p.name} — ${p.detail}") }
          }
          Spacer(Modifier.height(8.dp))
          Text("Latitud: $pdfLat   Longitud: $pdfLon", style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(8.dp))
          Text("Día del informe", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
          val today = vm.today()
          val dayOptions = listOf(0, 1, 2, 4, 7).map { today.minusDays(it.toLong()) }
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in dayOptions) {
              val label = when (d) {
                today -> "Hoy · ${d.dayOfMonth} ${d.month.name.take(3).lowercase()}"
                today.minusDays(1) -> "Ayer · ${d.dayOfMonth} ${d.month.name.take(3).lowercase()}"
                else -> "${d.dayOfMonth} ${d.month.name.take(3).lowercase()}"
              }
              FilterChip(selected = pdfDay == d, onClick = { pdfDay = d }, label = { Text(label) })
            }
          }
          Spacer(Modifier.height(8.dp))
          Text("Hora del vuelo: $pdfHour   •   Altura planeada: $pdfAltitude m", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (h in listOf("06:00", "08:00", "10:00", "12:00", "14:00", "16:00", "18:00")) {
              FilterChip(selected = pdfHour == h, onClick = { pdfHour = h }, label = { Text(h) })
            }
          }
          Slider(value = pdfAltitude.toFloat(), onValueChange = { pdfAltitude = it.toInt().coerceIn(10, 120) }, valueRange = 10f..120f)
          if (weekRef.size >= 4) {
            Text("Referencia rápida del archivo: en los últimos 7 días la ráfaga máxima diaria fue de %.1f a %.1f m/s, con viento dominante del %.0f° al %.0f° — en rojo para un dron de menos de 249 g.".format(weekRef[0], weekRef[1], weekRef[2], weekRef[3]), style = MaterialTheme.typography.bodySmall)
          }
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
              val la = pdfLat.toDoubleOrNull() ?: lat
              val lo = pdfLon.toDoubleOrNull() ?: lon
              vm.consultDay(la, lo, pdfDay)
            }, enabled = !dayLoading) { Text(if (dayLoading) "Consultando…" else "Consultar datos del día") }
            Button(
              onClick = {
                if (!pdfBusy) {
                  pdfBusy = true
                  scope.launch {
                    val la = pdfLat.toDoubleOrNull() ?: lat
                    val lo = pdfLon.toDoubleOrNull() ?: lon
                    val uri = withContext(Dispatchers.IO) {
                      PdfReport.generate(this@MainActivity, report, dayReport, la, lo, pdfPlace, pdfHour, pdfAltitude, drone, weekRef, checklist.all { it })
                    }
                    pdfBusy = false
                    if (uri != null) {
                      Toast.makeText(this@MainActivity, "Informe guardado en Descargas", Toast.LENGTH_LONG).show()
                      try {
                        val open = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        startActivity(open)
                      } catch (_: Exception) {}
                    } else {
                      Toast.makeText(this@MainActivity, "No se pudo generar el PDF", Toast.LENGTH_LONG).show()
                    }
                  }
                }
              },
              enabled = !pdfBusy
            ) { Text(if (pdfBusy) "Generando…" else "Descargar informe PDF") }
          }
          dayError?.let { Text(it, color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall) }
          dayReport?.let { dr ->
            Text("Consultado: ${dr.date} — ráfaga máxima %.1f m/s a las %s, dirección dominante %s. Fuente: ${dr.source}.".format(dr.maxGust, dr.maxGustHour?.substringAfter("T")?.substringBefore(":") ?: "—", dr.dominantDir?.let { compass(it) } ?: "—"), style = MaterialTheme.typography.bodySmall)
          }
          Text("Listo. Consulta primero para ver el resumen aquí, o descarga directo: el PDF se genera en tu teléfono con los datos consultados en ese momento.", style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(12.dp))

          // Pie: fuentes y nota de seguridad (como en la demo).
          Text("Fuentes de los datos: Open-Meteo (pronóstico y archivo histórico ERA5) y met.no (respaldo). Calidad del aire: Open-Meteo Air Quality.", style = MaterialTheme.typography.bodySmall)
          Text("Esta app muestra las condiciones al momento de la consulta, pensada para planear el día. El viento puede cambiar en minutos: antes de cada despegue, vuelve a revisar la fuente en vivo. Ante cualquier duda, la decisión segura es no volar.", style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(12.dp))

          err?.let {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA))) {
              Column(Modifier.padding(12.dp)) {
                Text(it, color = Color(0xFFB3261E))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.refresh(lat, lon) }) { Text("Reintentar") }
              }
            }
            Spacer(Modifier.height(12.dp))
          }
          Button(onClick = { vm.refresh(lat, lon) }, enabled = !loading) { Text("Actualizar ahora") }
          Text("Se actualiza solo cada 15 min.", style = MaterialTheme.typography.bodySmall)
        }
      }
    }
  }

  private fun loadGps(fused: com.google.android.gms.location.FusedLocationProviderClient, onLoc: (Double, Double) -> Unit, onDone: () -> Unit) {
    try {
      fused.lastLocation
        .addOnSuccessListener { it?.let { onLoc(it.latitude, it.longitude); onDone() } }
        .addOnFailureListener { /* sin GPS: se queda Tuxtla por defecto */ }
    } catch (_: SecurityException) {}
  }
}

private fun hourLabel(time: String): String {
  val h = time.substringAfter("T").substringBefore(":")
  return if (h.length == 2) "$h:00" else time
}

// Último estado conocido de la app, para el txt de cierre (ver onCreate).
object CrashLog {
  @Volatile var lastState: String = "sin datos todavía"
}

@Composable
private fun StatCard(title: String, value: String, sub: String, modifier: Modifier = Modifier) {
  Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EEF2))) {
    Column(Modifier.padding(12.dp)) {
      Text(title, style = MaterialTheme.typography.bodySmall)
      Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(sub, style = MaterialTheme.typography.bodySmall)
    }
  }
}
