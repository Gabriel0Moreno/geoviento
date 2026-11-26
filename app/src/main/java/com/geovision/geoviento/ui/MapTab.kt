package com.geovision.geoviento.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geovision.geoviento.data.FlightPrefs
import com.geovision.geoviento.data.TerrainProfile
import com.geovision.geoviento.data.WindReport

// Pestaña Mapa (v1.3.0): controles de capas visibles (Viento, Radar de
// lluvia con play/pausa, Clima), el mapa WebView/Leaflet con base oscura
// (respaldo OSM), la hoja inferior con los 9 datos del punto elegido y
// las zonas + relieve de siempre. El mapa sigue siendo WebView: nada de
// código nativo de mapa. La línea de diagnóstico bajo el mapa se conserva.
@Composable
fun MapTab(
  report: WindReport,
  lat: Double,
  lon: Double,
  placeName: String,
  limit: Double,
  prefs: FlightPrefs,
  isTuxtla: Boolean,
  terrain: TerrainProfile?,
  terrainLoading: Boolean,
  radarFrames: List<String>,
  onRequestRadar: () -> Unit
) {
  val n = report.now
  val points = report.hourly.take(24)
  var selectedHour by remember { mutableStateOf(-1) } // -1 = ahora
  var showCurrents by remember { mutableStateOf(true) }
  var playing by remember { mutableStateOf(true) }
  var intensity by remember { mutableStateOf(1f) }
  var selectedZone by remember { mutableStateOf<String?>(null) }
  // Capas (v1.3.0): viento (flechas), radar (RainViewer) y clima
  // (nubosidad/precipitación de Open-Meteo, como tarjeta sobre el mapa).
  var radarOn by remember { mutableStateOf(false) }
  var radarPlaying by remember { mutableStateOf(false) }
  var climaOn by remember { mutableStateOf(false) }

  // Los frames del radar se piden al abrir la pestaña; si la consulta
  // falla, la lista queda vacía y el radar no se ofrece.
  LaunchedEffect(Unit) { onRequestRadar() }

  val hourPoint = if (selectedHour in points.indices) points[selectedHour] else null

  Text("Mapa", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Text("$placeName · ${"%.3f".format(lat)}° N, ${"%.3f".format(-lon)}° O", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(12.dp))

  // Controles de capas.
  SectionTitle("Capas")
  Spacer(Modifier.height(8.dp))
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    FilterChip(
      selected = showCurrents,
      onClick = { showCurrents = !showCurrents },
      label = { Text("Viento") },
      colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
        containerColor = Geo.Card, labelColor = Geo.Muted
      )
    )
    FilterChip(
      selected = radarOn,
      onClick = { if (radarFrames.isNotEmpty()) radarOn = !radarOn },
      enabled = radarFrames.isNotEmpty(),
      label = { Text(if (radarFrames.isNotEmpty()) "Radar de lluvia" else "Radar no disponible") },
      colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
        containerColor = Geo.Card, labelColor = Geo.Muted,
        disabledContainerColor = Geo.Card, disabledLabelColor = Geo.Muted
      )
    )
    FilterChip(
      selected = climaOn,
      onClick = { climaOn = !climaOn },
      label = { Text("Clima") },
      colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
        containerColor = Geo.Card, labelColor = Geo.Muted
      )
    )
  }
  if (radarOn && radarFrames.isNotEmpty()) {
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Button(
        onClick = { radarPlaying = !radarPlaying },
        colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent, contentColor = Geo.Text)
      ) { Text(if (radarPlaying) "⏸ Pausar radar" else "▶ Animación de lluvia") }
      Text("${radarFrames.size} imágenes · RainViewer", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
    }
  }
  Spacer(Modifier.height(8.dp))

  // Hora del mapa.
  Text("Hora del mapa", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    FilterChip(
      selected = selectedHour == -1,
      onClick = { selectedHour = -1 },
      label = { Text("Ahora") },
      colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
        containerColor = Geo.Card, labelColor = Geo.Muted
      )
    )
    points.forEachIndexed { i, p ->
      FilterChip(
        selected = selectedHour == i,
        onClick = { selectedHour = i },
        label = { Text(hourLabel(p.time)) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
          containerColor = Geo.Card, labelColor = Geo.Muted
        )
      )
    }
  }
  Spacer(Modifier.height(8.dp))

  // El mapa: flechas animadas, corrientes on/off, intensidad.
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    FilterChip(
      selected = playing,
      onClick = { playing = !playing },
      label = { Text(if (playing) "Corrientes: en marcha" else "Corrientes: en pausa") },
      colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
        containerColor = Geo.Card, labelColor = Geo.Muted
      )
    )
  }
  Text("Intensidad de las flechas: ${"%.0f".format(intensity * 100)}%", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  androidx.compose.material3.Slider(
    value = intensity,
    onValueChange = { intensity = it },
    valueRange = 0.2f..1.5f
  )
  Spacer(Modifier.height(4.dp))

  WebMap(
    lat, lon, hourPoint?.dir10 ?: n.dir10, hourPoint?.gust10 ?: n.gust10, limit,
    playing, intensity, selectedZone,
    onZone = { selectedZone = it },
    fallback = {
      CurrentsMap(
        hourPoint?.dir10 ?: n.dir10, hourPoint?.gust10 ?: n.gust10,
        playing, intensity, selectedZone,
        onZoneSelected = { selectedZone = it },
        modifier = Modifier.fillMaxWidth().height(300.dp),
        limit = limit
      )
    },
    radarFrames = radarFrames,
    radarVisible = radarOn,
    radarPlaying = radarPlaying,
    arrowsVisible = showCurrents
  )

  // Tarjeta de clima modelado sobre el mapa (nubosidad/precipitación
  // de Open-Meteo para el punto y la hora elegidos).
  if (climaOn) {
    Spacer(Modifier.height(8.dp))
    GeoCard(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(12.dp)) {
        Text("Clima modelado · ${hourPoint?.let { hourLabel(it.time) } ?: "ahora"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
        Text(
          "Nubosidad ${report.surface?.cloudCover?.let { "%.0f%%".format(it) } ?: "—"} · " +
            "Precipitación ${hourPoint?.precipMm?.let { "%.1f mm".format(it) } ?: (report.surface?.precipMm?.let { "%.1f mm".format(it) } ?: "—")} · " +
            "Prob. de lluvia ${hourPoint?.precipProb?.let { "%.0f%%".format(it) } ?: (report.surface?.precipProbNow?.let { "%.0f%%".format(it) } ?: "—")}",
          style = MaterialTheme.typography.bodyMedium, color = Geo.Text
        )
        Text("Fuente: Open-Meteo (modelo). Sin satélite en vivo.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      }
    }
  }
  Spacer(Modifier.height(12.dp))

  // Hoja inferior: los 9 datos del punto elegido.
  SectionTitle("Detalles del punto")
  Spacer(Modifier.height(8.dp))
  val sheetWind = hourPoint?.speed10 ?: n.speed10
  val sheetGust = hourPoint?.gust10 ?: n.gust10
  val sheetTemp = hourPoint?.temp10 ?: n.temp
  val sheetDir = hourPoint?.dir10 ?: n.dir10
  val sheetVis = hourPoint?.visibilityM ?: n.visibilityM
  val sheetPrecipMm = hourPoint?.precipMm ?: report.surface?.precipMm
  val sheetPrecipProb = hourPoint?.precipProb ?: report.surface?.precipProbNow
  GeoCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
      SheetRow("Viento", formatSpeed(sheetWind, prefs.speedUnit))
      SheetRow("Ráfagas", formatSpeed(sheetGust, prefs.speedUnit))
      SheetRow("Temperatura", sheetTemp?.let { formatTemp(it, prefs.tempUnit) } ?: "—")
      SheetRow("Dirección", "${compass(sheetDir)} (${"%.0f".format(sheetDir)}°)")
      SheetRow("Visibilidad", formatVisibility(sheetVis))
      SheetRow("Presión", report.surface?.pressure?.let { "%.0f hPa".format(it) } ?: "—")
      SheetRow("Precipitación", (sheetPrecipMm?.let { "%.1f mm".format(it) } ?: "—") + (sheetPrecipProb?.let { " · %.0f%%".format(it) } ?: ""))
      SheetRow("Nubosidad", report.surface?.cloudCover?.let { "%.0f%%".format(it) } ?: "—")
      SheetRow("Humedad", report.surface?.humidity?.let { "%.0f%%".format(it) } ?: "—")
    }
  }
  Spacer(Modifier.height(12.dp))

  // Nota del cañón (solo cerca de Tuxtla).
  if (isTuxtla) {
    Text(
      "En el Cañón del Sumidero la ráfaga local puede subir ~45% respecto a la ciudad: %.1f m/s.".format((hourPoint?.gust10 ?: n.gust10) * 1.45),
      style = MaterialTheme.typography.bodySmall, color = Geo.Muted
    )
    Spacer(Modifier.height(12.dp))
  }

  // Zonas de Tuxtla (solo cerca de Tuxtla, como siempre).
  if (isTuxtla) {
    SectionTitle("Zonas de Tuxtla")
    Spacer(Modifier.height(8.dp))
    ZoneCards(selectedZone) { selectedZone = it }
    Spacer(Modifier.height(4.dp))
  }

  // Relieve real del lugar.
  ReliefSection(terrain, terrainLoading, isTuxtla)
}

@Composable
private fun SheetRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = Geo.Muted)
    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Geo.Text)
  }
}
