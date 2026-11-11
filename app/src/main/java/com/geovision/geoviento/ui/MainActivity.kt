package com.geovision.geoviento.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.geovision.geoviento.data.HourlyPoint
import com.geovision.geoviento.data.WindReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

class MainActivity : ComponentActivity() {
  // Tuxtla por defecto si aún no hay GPS: 16.75, -93.11
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val fused = LocationServices.getFusedLocationProviderClient(this)
    var onPermission: ((Boolean) -> Unit)? = null
    val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      onPermission?.invoke(granted)
    }
    setContent {
      val vm: WindViewModel = viewModel()
      val rep by vm.report.collectAsState()
      val err by vm.error.collectAsState()
      val loading by vm.loading.collectAsState()
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

      // Estado de la demo: hora seleccionada, corrientes, altura y checklist.
      var selectedHour by remember { mutableStateOf(-1) } // -1 = ahora
      var showCurrents by remember { mutableStateOf(true) }
      var playing by remember { mutableStateOf(true) }
      var intensity by remember { mutableStateOf(1f) }
      var selectedZone by remember { mutableStateOf<String?>(null) }
      var altitude by remember { mutableStateOf(60f) }
      var checkBattery by remember { mutableStateOf(false) }
      var checkRth by remember { mutableStateOf(false) }
      var checkClear by remember { mutableStateOf(false) }
      var checkWeather by remember { mutableStateOf(false) }
      var pdfBusy by remember { mutableStateOf(false) }

      val report = rep
      MaterialTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
          // Encabezado de marca.
          Text("Geoviento Mx — Viento para dron", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0A2E4D))
          Text("Tuxtla: %.4f, %.4f".format(lat, lon), style = MaterialTheme.typography.bodyMedium)
          report?.let { Text("Actualizado: " + (it.now.time ?: "—") + "  •  Fuente: " + it.source, style = MaterialTheme.typography.bodySmall) }
          if (!hasPermission) Text("Sin permiso de ubicación: se usan las coordenadas de Tuxtla.", style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(12.dp))

          if (report != null) {
            val n = report.now
            val level = flightLevel(n.gust10)
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
                Text("Límite dron <249 g (DJI Lito X1): 10.7 m/s. La ráfaga manda, no el viento medio.", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Text(bestWindowText(report.hourly), color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
              }
            }
            Spacer(Modifier.height(12.dp))

            // 2) Condiciones actuales — como en la demo.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatCard("Viento sostenido", "%.1f m/s".format(n.speed10), "%.0f km/h  •  %s %.0f°".format(kmh(n.speed10), compass(n.dir10), n.dir10), Modifier.weight(1f))
              StatCard("Ráfaga máxima", "%.1f m/s".format(n.gust10), "%.0f km/h".format(kmh(n.gust10)), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatCard("Viento 120 m", n.speed120?.let { "%.1f m/s".format(it) } ?: "—", "altura del dron", Modifier.weight(1f))
              StatCard("Ráfaga 120 m (est.)", n.gust120Est?.let { "%.1f m/s".format(it) } ?: "—", "estimada, no medida", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Temp: " + (n.temp?.let { "%.1f °C".format(it) } ?: "—"), style = MaterialTheme.typography.bodyMedium)
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

            // 4) Gráfica hora por hora — como en la demo.
            if (report.hourly.isNotEmpty()) {
              Text("Viento y ráfagas hora por hora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Spacer(Modifier.height(8.dp))
              Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in report.hourly.take(24)) HourlyBar(p)
              }
              Text("Verde < 8 m/s, amarillo 8–10.7, rojo ≥ 10.7 (no volar).", style = MaterialTheme.typography.bodySmall)
              Spacer(Modifier.height(12.dp))
            }

            // 5) Mapa de corrientes con selector de hora — como en la demo.
            Text("Corrientes en el mapa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            Card(Modifier.fillMaxWidth()) {
              Column(Modifier.padding(12.dp)) {
                if (showCurrents) {
                  CurrentsMap(mapDir, mapGust, playing, intensity, selectedZone, { selectedZone = it }, Modifier.fillMaxWidth().height(220.dp))
                } else {
                  Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Text("Corrientes ocultas") }
                }
                Spacer(Modifier.height(8.dp))
                Text("Esquema de corrientes, no mapa satelital en vivo. Flechas en la dirección del viento " + (if (hourPoint != null) "a las " + hourLabel(hourPoint.time) else "ahora") + ". Toca una zona para ver su nota.", style = MaterialTheme.typography.bodySmall)
                selectedZone?.let { zn ->
                  val zone = TUxtla_ZONES.firstOrNull { it.name == zn }
                  zone?.let { Text("📍 ${it.name}: ${it.note}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
                }
              }
            }
            Spacer(Modifier.height(12.dp))

            // 6) Cómo se mueve el aire hoy sobre el relieve — como en la demo.
            Text("Cómo se mueve el aire hoy sobre el relieve", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("En el Cañón del Sumidero el viento se canaliza entre las paredes y la ráfaga local puede subir ~45%: %.1f m/s.".format(n.gust10 * 1.45))
            Text("En Copoya el aire sube por la ladera y puede empujar el dron hacia arriba. En Chiapa de Corzo la brisa del río cambia la dirección por la tarde.")
            Spacer(Modifier.height(12.dp))

            // 7) Calculadora de viento por altura — como en la demo.
            Text("Calculadora de viento por altura", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val altSpeed = n.speed10 * (altitude / 10.0).pow(0.14)
            val altGust = n.gust10 * (altitude / 10.0).pow(0.14)
            Text("Altura: %.0f m".format(altitude), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Slider(value = altitude, onValueChange = { altitude = it }, valueRange = 10f..120f)
            Text("A %.0f m: viento %.1f m/s, ráfaga est. %.1f m/s (factor ×%.2f). Estimación por ley de potencia, no medición.".format(altitude, altSpeed, altGust, (altitude / 10.0).pow(0.14)))
            Text("10 m: %.1f m/s, ráfaga %.1f m/s".format(n.speed10, n.gust10))
            Text("80 m: " + (n.speed80?.let { "%.1f m/s".format(it) } ?: "—") + (n.dir80?.let { " (%s)".format(compass(it)) } ?: ""))
            Text("120 m: " + (n.speed120?.let { "%.1f m/s".format(it) } ?: "—") + (n.dir120?.let { " (%s)".format(compass(it)) } ?: "") + ", ráfaga est. " + (n.gust120Est?.let { "%.1f m/s".format(it) } ?: "—"))
            Spacer(Modifier.height(12.dp))

            // 8) Checklist antes de despegar — como en la demo.
            Text("Antes de despegar hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            CheckRow("Ráfaga por debajo de 10.7 m/s", n.gust10 < 10.7, enabled = false, onChange = {})
            CheckRow("Batería cargada", checkBattery, enabled = true, onChange = { checkBattery = it })
            CheckRow("Regreso a casa activado", checkRth, enabled = true, onChange = { checkRth = it })
            CheckRow("Zona despejada de personas", checkClear, enabled = true, onChange = { checkClear = it })
            CheckRow("Sin lluvia ni tormenta cerca", checkWeather, enabled = true, onChange = { checkWeather = it })
            Spacer(Modifier.height(12.dp))

            // 9) Informe técnico en PDF — como en la demo.
            Button(
              onClick = {
                if (!pdfBusy) {
                  pdfBusy = true
                  scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                      PdfReport.generate(this@MainActivity, report, lat, lon, altitude.toInt(), checkBattery && checkRth && checkClear && checkWeather)
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
            ) { Text(if (pdfBusy) "Generando informe…" else "Descargar informe técnico en PDF") }
            Text("El informe lleva ubicación, día, hora, altura, modelo de dron (DJI Lito X1) y el semáforo.", style = MaterialTheme.typography.bodySmall)
          } else {
            if (loading) {
              CircularProgressIndicator()
              Spacer(Modifier.height(8.dp))
            }
            Text("Cargando viento en vivo…")
            Text("Si tarda más de 20 segundos, revisa tu internet y toca Actualizar ahora.", style = MaterialTheme.typography.bodySmall)
          }

          err?.let {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA))) {
              Column(Modifier.padding(12.dp)) {
                Text(it, color = Color(0xFFB3261E))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.refresh(lat, lon) }) { Text("Reintentar") }
              }
            }
          }
          Spacer(Modifier.height(12.dp))
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

private fun bestWindowText(hourly: List<HourlyPoint>): String {
  var bestStart = -1; var bestLen = 0; var curStart = -1; var curLen = 0
  for ((i, p) in hourly.take(24).withIndex()) {
    if (p.gust10 < 8.0) {
      if (curStart == -1) { curStart = i; curLen = 1 } else curLen++
      if (curLen > bestLen) { bestLen = curLen; bestStart = curStart }
    } else { curStart = -1; curLen = 0 }
  }
  return if (bestStart >= 0 && bestLen >= 2) {
    "Mejor ventana: " + hourLabel(hourly[bestStart].time) + "–" + hourLabel(hourly[(bestStart + bestLen - 1).coerceAtMost(23)].time) + " (ráfaga < 8 m/s)."
  } else "Sin ventana verde larga en las próximas 24 h: vigila la ráfaga hora por hora."
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

@Composable
private fun HourlyBar(p: HourlyPoint) {
  val level = flightLevel(p.gust10)
  val color = when (level) {
    FlightLevel.GREEN -> Color(0xFF1B7A3D)
    FlightLevel.YELLOW -> Color(0xFFB8860B)
    FlightLevel.RED -> Color(0xFFB3261E)
  }
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
    Text("%.0f".format(p.gust10), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    Box(Modifier.width(28.dp).height(((p.gust10 / 14.0) * 80.0).coerceIn(6.0, 80.0).dp).background(color, RoundedCornerShape(4.dp)))
    Text(hourLabel(p.time), style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
    Text(label)
  }
}
