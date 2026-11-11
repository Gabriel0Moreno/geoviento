package com.geovision.geoviento.ui
import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
class MainActivity : ComponentActivity() {
  // Tuxtla por defecto si aún no hay GPS: 16.75, -93.11
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val fused = LocationServices.getFusedLocationProviderClient(this)
    val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    perm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    setContent {
      val vm: WindViewModel = viewModel()
      val rep by vm.report.collectAsState(); val err by vm.error.collectAsState()
      var lat by remember { mutableStateOf(16.75) }; var lon by remember { mutableStateOf(-93.11) }
      LaunchedEffect(Unit) { vm.refresh(lat, lon)
        try { fused.lastLocation.addOnSuccessListener { it?.let { lat = it.latitude; lon = it.longitude; vm.refresh(lat, lon) } } } catch (_: SecurityException) {}
      }
      MaterialTheme { Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Geoviento Mx — Viento para dron", style = MaterialTheme.typography.headlineSmall)
        Text("Tuxtla: %.4f, %.4f".format(lat, lon))
        Spacer(Modifier.height(12.dp))
        if (rep != null) { val n = rep!!.now; val level = flightLevel(n.gust10)
          Text(level.label, style = MaterialTheme.typography.titleLarge)
          Text(level.advice)
          Spacer(Modifier.height(8.dp))
          Text("Viento 10 m: %.1f m/s (%.0f km/h) %s %.0f°".format(n.speed10, kmh(n.speed10), compass(n.dir10), n.dir10))
          Text("Ráfaga 10 m: %.1f m/s (%.0f km/h)".format(n.gust10, kmh(n.gust10)))
          Text("Ráfaga 120 m (dron): " + (n.gust120?.let { "%.1f m/s".format(it) } ?: "sin dato"))
          Text("Temp: " + (n.temp?.let { "%.1f °C".format(it) } ?: "—") + "  •  Fuente: " + rep!!.source)
          Text("Mapa: abre el mapa con relieve y partículas en la siguiente pantalla (MapScreen + MapLibre).")
        } else Text("Cargando viento en vivo…")
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.refresh(lat, lon) }) { Text("Actualizar ahora") }
        Text("Se actualiza solo cada 15 min. La ráfaga manda, no el viento medio.", style = MaterialTheme.typography.bodySmall)
      } }
    }
  }
}
