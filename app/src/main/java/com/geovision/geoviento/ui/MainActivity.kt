package com.geovision.geoviento.ui

import android.Manifest
import android.os.Bundle
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
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  // Tuxtla por defecto si aún no hay GPS: 16.75, -93.11
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val fused = LocationServices.getFusedLocationProviderClient(this)
    val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    perm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    setContent {
      val vm: WindViewModel = viewModel()
      val rep by vm.report.collectAsState()
      val err by vm.error.collectAsState()
      val loading by vm.loading.collectAsState()
      var lat by remember { mutableStateOf(16.75) }
      var lon by remember { mutableStateOf(-93.11) }

      LaunchedEffect(Unit) {
        vm.refresh(lat, lon)
        try {
          fused.lastLocation.addOnSuccessListener {
            it?.let { lat = it.latitude; lon = it.longitude; vm.refresh(lat, lon) }
          }
        } catch (_: SecurityException) {}
      }
      // Actualización automática cada 15 min con la ubicación actual.
      LaunchedEffect(lat, lon) {
        while (true) { delay(15 * 60 * 1000L); vm.refresh(lat, lon) }
      }

      MaterialTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
          Text("Geoviento Mx — Viento para dron", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          Text("Tuxtla: %.4f, %.4f".format(lat, lon), style = MaterialTheme.typography.bodyMedium)
          rep?.let { Text("Actualizado: " + (it.now.time ?: "—") + "  •  Fuente: " + it.source, style = MaterialTheme.typography.bodySmall) }
          Spacer(Modifier.height(12.dp))

          if (rep != null) {
            val n = rep!!.now
            val level = flightLevel(n.gust10)
            val levelColor = when (level) {
              FlightLevel.GREEN -> Color(0xFF1B7A3D)
              FlightLevel.YELLOW -> Color(0xFF8A6D00)
              FlightLevel.RED -> Color(0xFFB3261E)
            }
            // Tarjeta veredicto (semáforo).
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = levelColor)) {
              Column(Modifier.padding(16.dp)) {
                Text(level.label, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Ráfaga 10 m: %.1f m/s (%.0f km/h)".format(n.gust10, kmh(n.gust10)), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(level.advice, color = Color.White)
                Text("Límite dron <249 g (DJI Lito X1): 10.7 m/s. La ráfaga manda, no el viento medio.", color = Color.White, style = MaterialTheme.typography.bodySmall)
              }
            }
            Spacer(Modifier.height(12.dp))

            // Condiciones actuales.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatCard("Viento 10 m", "%.1f m/s".format(n.speed10), "%.0f km/h".format(kmh(n.speed10)), Modifier.weight(1f))
              StatCard("Ráfaga 10 m", "%.1f m/s".format(n.gust10), "%.0f km/h".format(kmh(n.gust10)), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatCard("Viento 120 m", n.speed120?.let { "%.1f m/s".format(it) } ?: "—", "altura del dron", Modifier.weight(1f))
              StatCard("Ráfaga 120 m (est.)", n.gust120Est?.let { "%.1f m/s".format(it) } ?: "—", "estimada, no medida", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Temp: " + (n.temp?.let { "%.1f °C".format(it) } ?: "—"), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))

            // Brújula + dirección.
            Card(Modifier.fillMaxWidth()) {
              Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                WindCompass(n.dir10)
                Spacer(Modifier.width(16.dp))
                Column {
                  Text("Dirección del viento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Text("Viene del %s (%.0f°)".format(compass(n.dir10), n.dir10))
                  Text("El aire va hacia el %s".format(compass((n.dir10 + 180.0) % 360.0)))
                  Text("A 120 m viene del %s".format(n.dir120?.let { compass(it) } ?: "—"))
                }
              }
            }
            Spacer(Modifier.height(12.dp))

            // Próximas horas: ráfaga por hora.
            if (rep!!.hourly.isNotEmpty()) {
              Text("Próximas horas — ráfaga (m/s)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Spacer(Modifier.height(8.dp))
              Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in rep!!.hourly.take(24)) HourlyBar(p)
              }
              Spacer(Modifier.height(12.dp))
            }

            // Mapa de corrientes (esquema).
            Text("Corrientes en el mapa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
              Column(Modifier.padding(12.dp)) {
                CurrentsMap(n.dir10, n.gust10, Modifier.fillMaxWidth().height(220.dp))
                Spacer(Modifier.height(8.dp))
                Text("Esquema de corrientes, no mapa satelital en vivo. Flechas en la dirección real del viento.", style = MaterialTheme.typography.bodySmall)
                Text("En el Cañón del Sumidero el viento se canaliza y la ráfaga local puede subir ~45%: %.1f m/s.".format(n.gust10 * 1.45), style = MaterialTheme.typography.bodySmall)
              }
            }
            Spacer(Modifier.height(12.dp))

            // Viento por altura.
            Text("Viento por altura de vuelo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("10 m: %.1f m/s, ráfaga %.1f m/s".format(n.speed10, n.gust10))
            Text("80 m: " + (n.speed80?.let { "%.1f m/s".format(it) } ?: "—") + (n.dir80?.let { " (%s)".format(compass(it)) } ?: ""))
            Text("120 m: " + (n.speed120?.let { "%.1f m/s".format(it) } ?: "—") + (n.dir120?.let { " (%s)".format(compass(it)) } ?: "") + ", ráfaga est. " + (n.gust120Est?.let { "%.1f m/s".format(it) } ?: "—"))
            Spacer(Modifier.height(12.dp))

            // Checklist previo al vuelo.
            Text("Antes de volar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("✓ Ráfaga por debajo de 10.7 m/s  •  ✓ Batería cargada  •  ✓ Regreso a casa activado")
            Text("✓ Sin lluvia ni tormenta cerca  •  ✓ Zona despejada de personas")
          } else {
            if (loading) {
              CircularProgressIndicator()
              Spacer(Modifier.height(8.dp))
            }
            Text("Cargando viento en vivo…")
          }

          err?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
          }
          Spacer(Modifier.height(12.dp))
          Button(onClick = { vm.refresh(lat, lon) }, enabled = !loading) { Text("Actualizar ahora") }
          Text("Se actualiza solo cada 15 min. La ráfaga manda, no el viento medio.", style = MaterialTheme.typography.bodySmall)
        }
      }
    }
  }
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
  val hour = p.time.substringAfter("T").substringBefore(":").let { if (it.length == 2) "$it:00" else p.time }
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
    Text("%.0f".format(p.gust10), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    Box(Modifier.width(28.dp).height(((p.gust10 / 14.0) * 80.0).coerceIn(6.0, 80.0).dp).background(color, RoundedCornerShape(4.dp)))
    Text(hour, style = MaterialTheme.typography.bodySmall)
  }
}
