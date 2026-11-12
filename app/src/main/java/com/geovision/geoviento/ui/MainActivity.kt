package com.geovision.geoviento.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.geovision.geoviento.data.DRONES
import com.geovision.geoviento.data.HourlyPoint
import com.geovision.geoviento.data.WindReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
  // Tuxtla por defecto si aún no hay GPS: 16.75, -93.11
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Si la app se vuelve a cerrar, el error exacto queda guardado en
    // Descargas/geoviento-crash.txt para poder diagnosticarlo sin computadora.
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
      try {
        val text = "Geoviento crash\n" + Log.getStackTraceString(e)
        val values = ContentValues().apply {
          put(MediaStore.Downloads.DISPLAY_NAME, "geoviento-crash.txt")
          put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        uri?.let { contentResolver.openOutputStream(it)?.use { os -> os.write(text.toByteArray()) } }
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
      val context = LocalContext.current
      var lat by remember { mutableStateOf(16.75) }
      var lon by remember { mutableStateOf(-93.11) }
      var hasPermission by remember { mutableStateOf(false) }
      val scope = rememberCoroutineScope()

      // Permiso de ubicación después del primer frame (no en onCreate),
      // y la primera carga con la ubicación por defecto de Tuxtla.
      LaunchedEffect(Unit) {
        onPermission = { granted ->
          hasPermission = granted
          if (granted) loadGps(fused, { la, lo -> lat = la; lon = lo }, { vm.refresh(lat, lon) })
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
      // El mapa real se carga SOLO cuando Gabo lo pide con el botón: en la
      // v1.1.2 el mapa se creaba al abrir y la app se cerraba sin dejar el
      // archivo de error (fallo nativo del SDK, que el handler de Java no
      // atrapa). Con el esquema por defecto la app abre seguro.
      var loadRealMap by remember { mutableStateOf(false) }
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

      // La interfaz SIEMPRE se ve: si aún no hay datos en vivo, se muestra el
      // ejemplo de la demo (marcado) o los datos guardados. Nunca pantalla blanca.
      val report: WindReport = rep
      val limit = drone.limit
      MaterialTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
          // Encabezado de marca (como en la demo).
          Text("Geoviento Mx — Viento para dron", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0A2E4D))
          Text("TUXTLA GUTIÉRREZ, CHIAPAS · %.3f° N, %.3f° O · %s M S. N. M.".format(lat, -lon, report.surface?.elevation?.let { "%.0f".format(it) } ?: "535"), style = MaterialTheme.typography.bodySmall)
          Text("Datos consultados: " + (report.now.time ?: "—") + "  •  Fuente: " + report.source, style = MaterialTheme.typography.bodySmall)
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
              Text("Límite ${drone.name}: %.1f m/s. La ráfaga manda, no el viento medio.".format(limit), color = Color.White, style = MaterialTheme.typography.bodySmall)
              Text(bestWindowText(report.hourly, limit), color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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
          }
          Spacer(Modifier.height(8.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Temperatura", n.temp?.let { "%.1f °C".format(it) } ?: "—", "Máxima de hoy: " + (report.surface?.tempMaxToday?.let { "%.0f °C".format(it) } ?: "—"), Modifier.weight(1f))
            StatCard("Límite — ${drone.name}", "%.1f m/s".format(limit), "Resistencia al viento de referencia", Modifier.weight(1f))
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
          Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
              WindCompass(n.dir10)
              Spacer(Modifier.width(16.dp))
              Column {
                Text("Dirección del viento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Viene del %s (%.0f°)".format(compass(n.dir10), n.dir10))
                Text("El aire va hacia el %s".format(compass((n.dir10 + 180.0) % 360.0)))
                Text("A 120 m viene del %s".format(n.dir120?.let { compass(it) } ?: "—"))
                Text("Por la tarde el viento suele girar al NO.", style = MaterialTheme.typography.bodySmall)
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
          Text("El valle de Tuxtla corre de oeste a este entre la Meseta de Copoya (sur) y la entrada del Cañón del Sumidero (norte). Con viento del norte y noroeste, el cañón canaliza el aire hacia la ciudad y la meseta genera turbulencia en su lado protegido. La herramienta de corrientes dibuja el flujo directamente sobre el mapa, hora por hora: elige la hora y sigue las flechas. Toca una zona para ubicarla en el mapa.", style = MaterialTheme.typography.bodySmall)
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
            if (loadRealMap) {
              RealMap(lat, lon, mapDir, mapGust, limit, playing, intensity, selectedZone, { selectedZone = it }) {
                CurrentsMap(mapDir, mapGust, playing, intensity, selectedZone, { selectedZone = it }, Modifier.fillMaxWidth().height(220.dp))
              }
            } else {
              CurrentsMap(mapDir, mapGust, playing, intensity, selectedZone, { selectedZone = it }, Modifier.fillMaxWidth().height(220.dp))
              Spacer(Modifier.height(8.dp))
              OutlinedButton(onClick = { loadRealMap = true }) { Text("Cargar mapa real con calles") }
              Text("El mapa real se carga solo cuando lo pides, para que la app abra aunque el mapa falle.", style = MaterialTheme.typography.bodySmall)
            }
          } else {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Text("Corrientes ocultas") }
          }
          Spacer(Modifier.height(8.dp))
          val flowHour = hourPoint?.let { hourLabel(it.time) } ?: "Ahora"
          Text("$flowHour: Viento desde el ${compass(mapDir)} (${mapDir.toInt()}°), el aire se mueve hacia el ${compass((mapDir + 180) % 360)}. Sostenido %.1f m/s, ráfaga %.1f m/s. En la boca del cañón, la canalización puede subir la ráfaga local a ≈ %.1f m/s.".format(n.speed10, mapGust, mapGust * 1.45), style = MaterialTheme.typography.bodySmall)
          Text("Flecha = hacia dónde se mueve el aire  •  Verde < %.1f m/s  •  Amarillo %.1f–%.1f m/s  •  Rojo ≥ %.1f m/s".format(limit * 0.75, limit * 0.75, limit, limit), style = MaterialTheme.typography.bodySmall)
          Text("Capa esquemática a partir de la dirección y la ráfaga del pronóstico de hoy: no es una medición en sitio.", style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(8.dp))
          ZoneCards(selectedZone) { selectedZone = it }
          Spacer(Modifier.height(12.dp))

          // 7) Cómo se mueve el aire sobre el relieve — como en la demo.
          ReliefSection()
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
          Text("Elige la ubicación y el día, consulta los datos y descarga el informe PDF listo para guardar junto a tu bitácora de vuelo. Para hoy usa el pronóstico de Open-Meteo junto con el archivo histórico de Open-Meteo (ERA5). El PDF incluye datos de la hora elegida, tabla hora por hora, viento estimado a tu altura, la orientación de las corrientes: de dónde viene el aire, hacia dónde se mueve y cómo lo modifican el Cañón del Sumidero y la Meseta de Copoya.", style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(8.dp))
          Text("Ubicación del vuelo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
          Text("Sitio seleccionado: $pdfPlace", style = MaterialTheme.typography.bodySmall)
          OutlinedTextField(value = pdfQuery, onValueChange = { pdfQuery = it }, label = { Text("Buscar otra ubicación por nombre") }, modifier = Modifier.fillMaxWidth())
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.searchPlaces(pdfQuery) }) { Text("Buscar") }
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
          Text("Tu dron (límite por ráfaga): ${drone.name} — límite %.1f m/s".format(limit), style = MaterialTheme.typography.bodySmall)
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
          Text("Umbrales del semáforo aplicados a ${drone.name} (límite %.1f m/s): verde < %.1f m/s, amarillo %.1f–%.1f m/s, rojo ≥ %.1f m/s, aplicados sobre la ráfaga.".format(limit, limit * 0.75, limit * 0.75, limit, limit), style = MaterialTheme.typography.bodySmall)
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
          Text("Se actualiza solo cada 15 min. La ráfaga manda, no el viento medio.", style = MaterialTheme.typography.bodySmall)
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

private fun bestWindowText(hourly: List<HourlyPoint>, limit: Double): String {
  val green = limit * 0.75
  var bestStart = -1; var bestLen = 0; var curStart = -1; var curLen = 0
  for ((i, p) in hourly.take(24).withIndex()) {
    if (p.gust10 < green) {
      if (curStart == -1) { curStart = i; curLen = 1 } else curLen++
      if (curLen > bestLen) { bestLen = curLen; bestStart = curStart }
    } else { curStart = -1; curLen = 0 }
  }
  if (bestStart < 0 || bestLen < 2) return "Sin ventana verde larga en las próximas 24 h: vigila la ráfaga hora por hora."
  if (bestLen >= 24) return "Todo el día en verde (ráfaga < %.1f m/s).".format(green)
  return "Mejor ventana: " + hourLabel(hourly[bestStart].time) + "–" + hourLabel(hourly[(bestStart + bestLen - 1).coerceAtMost(23)].time) + " (ráfaga < %.1f m/s).".format(green)
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
