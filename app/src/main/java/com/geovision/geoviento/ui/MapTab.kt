package com.geovision.geoviento.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geovision.geoviento.data.FlightPrefs
import com.geovision.geoviento.data.GridPoint
import com.geovision.geoviento.data.TerrainProfile
import com.geovision.geoviento.data.WindGrid
import com.geovision.geoviento.data.WindReport
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Pestaña Mapa (v1.4.0): el mapa a pantalla casi completa, como la
// pantalla de mapa que Gabo pidió (DroneCheck): base clara con relieve,
// barbas de viento reales, controles flotantes sobre el mapa (píldora
// superior con atrás/viento/sol, descarga del informe, chip "Viento
// modelado · 10 m"), tira de horas flotante y hoja inferior oscura con
// los 9 datos en 3 columnas. La v1.3.0 apilaba los controles sobre un
// mapa oscuro pequeño y se veía como un vacío negro con 3 flechas
// sueltas; eso se fue. El mapa sigue siendo WebView/Leaflet: nada de
// código nativo de mapa. La línea de diagnóstico sigue bajo el mapa.
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
  onRequestRadar: () -> Unit,
  windGrid: WindGrid?,
  gridFailed: Boolean,
  onRequestGrid: () -> Unit,
  onBackToHome: () -> Unit,
  onDownloadPdf: (String) -> Unit
) {
  val n = report.now
  val points = report.hourly.take(24)
  var selectedHour by remember { mutableStateOf(-1) } // -1 = ahora
  var selectedZone by remember { mutableStateOf<String?>(null) }
  var radarOn by remember { mutableStateOf(false) }
  var radarPlaying by remember { mutableStateOf(false) }
  var climaOn by remember { mutableStateOf(false) }
  var barbsOn by remember { mutableStateOf(true) }
  var mapStatus by remember { mutableStateOf("Mapa: cargando…") }

  // Los frames del radar y la malla de viento se piden al abrir la
  // pestaña (la malla, una vez por lugar; cambiar de hora no repide).
  LaunchedEffect(Unit) { onRequestRadar() }
  LaunchedEffect(lat, lon) { onRequestGrid() }

  val hourPoint = if (selectedHour in points.indices) points[selectedHour] else null
  val hourText = hourPoint?.let { hourLabel(it.time) } ?: "Ahora"
  val pdfHour = hourPoint?.let { hourLabel(it.time) } ?: (report.now.time?.let { hourLabel(it) } ?: "08:00")

  // Malla recortada a la ventana de 24 h que muestra la pestaña (la
  // malla trae 72 h; al JS solo le mandamos las 24 que se ven, para que
  // el JSON sea pequeño). El índice de barba es la posición dentro de
  // esa ventana: 0 = ahora.
  val gridSlice = remember(windGrid, report.hourly) {
    val g = windGrid ?: return@remember null
    val start = report.hourly.firstOrNull()?.time ?: return@remember null
    var si = g.times.indexOf(start)
    if (si < 0) si = g.times.indexOfFirst { it >= start }.takeIf { it >= 0 } ?: 0
    val times = g.times.drop(si).take(24)
    if (times.isEmpty()) return@remember null
    WindGrid(times, g.points.map { p ->
      GridPoint(p.lat, p.lon, p.dir.drop(si).take(24), p.speed.drop(si).take(24), p.gust.drop(si).take(24))
    })
  }
  val gridJson = remember(gridSlice) {
    gridSlice?.let { g ->
      JSONObject().apply {
        put("times", JSONArray(g.times))
        put("points", JSONArray().apply {
          g.points.forEach { p ->
            put(JSONObject().apply {
              put("lat", p.lat)
              put("lon", p.lon)
              put("dir", JSONArray(p.dir))
              put("speed", JSONArray(p.speed))
            })
          }
        })
      }.toString()
    }
  }
  val barbHour = when {
    gridSlice == null -> -1
    selectedHour in points.indices -> {
      val idx = gridSlice.times.indexOf(points[selectedHour].time)
      if (idx >= 0) idx else selectedHour.coerceIn(0, gridSlice.times.size - 1)
    }
    else -> 0
  }

  // El mapa domina la pantalla: alto casi completo menos lo que ocupan
  // la barra inferior y el respiro para el contenido de abajo.
  val screenH = LocalConfiguration.current.screenHeightDp.dp
  val mapHeight = (screenH - 240.dp).coerceIn(400.dp, 620.dp)

  // Los 9 datos del punto elegido (mismos valores y formatos de siempre).
  val sheetWind = hourPoint?.speed10 ?: n.speed10
  val sheetGust = hourPoint?.gust10 ?: n.gust10
  val sheetTemp = hourPoint?.temp10 ?: n.temp
  val sheetDir = hourPoint?.dir10 ?: n.dir10
  val sheetVis = hourPoint?.visibilityM ?: n.visibilityM
  val sheetPrecipMm = hourPoint?.precipMm ?: report.surface?.precipMm
  val sheetPrecipProb = hourPoint?.precipProb ?: report.surface?.precipProbNow

  Box(Modifier.fillMaxWidth().height(mapHeight)) {
    WebMap(
      lat, lon, selectedZone,
      onZone = { selectedZone = it },
      fallback = {
        CurrentsMap(
          hourPoint?.dir10 ?: n.dir10, hourPoint?.gust10 ?: n.gust10,
          true, 1f, selectedZone,
          onZoneSelected = { selectedZone = it },
          modifier = Modifier.fillMaxSize(),
          limit = limit
        )
      },
      radarFrames = radarFrames,
      radarVisible = radarOn,
      radarPlaying = radarPlaying,
      barbsVisible = barbsOn,
      barbHour = barbHour,
      gridJson = gridJson,
      modifier = Modifier.fillMaxSize(),
      onStatus = { mapStatus = it }
    )

    // Controles flotantes superiores.
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        // Píldora: atrás | viento | sol.
        Row(
          Modifier.background(Color(0xE60A1622), RoundedCornerShape(24.dp)).padding(4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          PillButton(onClick = onBackToHome) {
            Text("‹", color = Geo.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
          }
          Box(Modifier.width(1.dp).height(24.dp).background(Geo.Muted.copy(alpha = 0.4f)))
          PillButton(onClick = { barbsOn = !barbsOn }) {
            WindGlyph(color = if (barbsOn) Geo.Accent else Geo.Text)
          }
          Box(Modifier.width(1.dp).height(24.dp).background(Geo.Muted.copy(alpha = 0.4f)))
          PillButton(onClick = { climaOn = !climaOn }) {
            SunGlyph(color = if (climaOn) Geo.Accent else Geo.Text)
          }
        }
        // Descarga del informe PDF del punto y la hora elegidos.
        Box(
          Modifier.size(44.dp).background(Color(0xE60A1622), CircleShape)
            .clickable { onDownloadPdf(pdfHour) },
          contentAlignment = Alignment.Center
        ) {
          Text("↓", color = Geo.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
      }
      Spacer(Modifier.height(8.dp))
      // Chip del modelo de viento + hora.
      Box(
        Modifier.background(Color(0xE60A1622), RoundedCornerShape(16.dp))
          .padding(horizontal = 12.dp, vertical = 7.dp)
      ) {
        Text("Viento modelado · 10 m · $hourText", color = Geo.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
      if (radarFrames.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MapChip("Radar · ${radarFrames.size}", radarOn) { radarOn = !radarOn }
          if (radarOn) {
            MapChip(if (radarPlaying) "⏸ Pausar" else "▶ Animar", radarPlaying) { radarPlaying = !radarPlaying }
          }
        }
      }
      if (gridFailed) {
        Spacer(Modifier.height(8.dp))
        Box(
          Modifier.background(Color(0xE60A1622), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
          Text("Viento modelado no disponible", color = Geo.WarnText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
      // Tarjeta de clima modelado sobre el mapa (nubosidad/precipitación
      // de Open-Meteo para el punto y la hora elegidos).
      if (climaOn) {
        Spacer(Modifier.height(8.dp))
        Card(
          Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xF2152A3D))
        ) {
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
    }

    // Abajo: tira de horas flotante + hoja inferior con los 9 datos.
    Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        HourChip("Ahora", selectedHour == -1) { selectedHour = -1 }
        points.forEachIndexed { i, p ->
          HourChip(hourLabel(p.time), selectedHour == i) { selectedHour = i }
        }
      }
      Spacer(Modifier.height(8.dp))
      Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF2152A3D))
      ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
          Text(sheetPlaceName(placeName, lat, lon), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Geo.Text)
          Spacer(Modifier.height(8.dp))
          Row(Modifier.fillMaxWidth()) {
            SheetCell("Viento", formatSpeed(sheetWind, prefs.speedUnit), Modifier.weight(1f))
            SheetCell("Ráfagas", formatSpeed(sheetGust, prefs.speedUnit), Modifier.weight(1f))
            SheetCell("Temperatura", sheetTemp?.let { formatTemp(it, prefs.tempUnit) } ?: "—", Modifier.weight(1f))
          }
          Spacer(Modifier.height(8.dp))
          Row(Modifier.fillMaxWidth()) {
            SheetCell("Dirección", "${compass(sheetDir)} (${"%.0f".format(sheetDir)}°)", Modifier.weight(1f))
            SheetCell("Visibilidad", formatVisibility(sheetVis), Modifier.weight(1f))
            SheetCell("Presión local", report.surface?.pressure?.let { "%.0f hPa".format(it) } ?: "—", Modifier.weight(1f))
          }
          Spacer(Modifier.height(8.dp))
          Row(Modifier.fillMaxWidth()) {
            SheetCell("Precipitación", (sheetPrecipMm?.let { "%.1f mm".format(it) } ?: "—") + (sheetPrecipProb?.let { " · %.0f%%".format(it) } ?: ""), Modifier.weight(1f))
            SheetCell("Nubosidad", report.surface?.cloudCover?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
            SheetCell("Humedad", report.surface?.humidity?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
          }
        }
      }
    }
  }
  Spacer(Modifier.height(4.dp))
  Text(mapStatus, style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(12.dp))

  // Nota del cañón (solo cerca de Tuxtla). OJO: el % literal va como
  // %% dentro de .format(), si no la app se cierra (crash 2026-11-26).
  if (isTuxtla) {
    Text(
      "En el Cañón del Sumidero la ráfaga local puede subir ~45%% respecto a la ciudad: %.1f m/s.".format((hourPoint?.gust10 ?: n.gust10) * 1.45),
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

// Nombre del lugar para la hoja inferior: con ", Chiapas, México"
// cuando el lugar está en Chiapas; si el nombre ya trae Chiapas, solo
// se le agrega ", México".
private fun sheetPlaceName(placeName: String, lat: Double, lon: Double): String {
  val inChiapas = lat in 14.0..18.0 && lon in -94.5..-90.0
  return when {
    placeName.contains("Chiapas") && placeName.contains("México") -> placeName
    placeName.contains("Chiapas") -> "$placeName, México"
    inChiapas -> "$placeName, Chiapas, México"
    else -> placeName
  }
}

@Composable
private fun PillButton(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
  Box(
    Modifier.size(40.dp).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
    content = content
  )
}

@Composable
private fun MapChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    Modifier.background(if (selected) Geo.Accent else Color(0xE60A1622), RoundedCornerShape(16.dp))
      .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(label, color = Geo.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun HourChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    Modifier.background(if (selected) Geo.Accent else Color(0xCC0A1622), RoundedCornerShape(14.dp))
      .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(label, color = Geo.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun SheetCell(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Geo.Text)
  }
}

// Glifo de viento (tres líneas onduladas), dibujado con Canvas como los
// glifos de la navegación inferior: sin depender de paquetes de iconos.
@Composable
private fun WindGlyph(color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier.size(24.dp)) {
    val s = Stroke(width = 2.5f, cap = StrokeCap.Round)
    val w = size.width
    val h = size.height
    for (i in 0..2) {
      val y = h * (0.28f + i * 0.22f)
      val len = w * (0.72f - i * 0.12f)
      val p = Path().apply {
        moveTo(2f, y)
        quadraticBezierTo(w * 0.35f, y - 5f, w * 0.55f, y)
        quadraticBezierTo(w * 0.75f, y + 5f, 2f + len, y)
      }
      drawPath(p, color, style = s)
    }
  }
}

// Glifo de sol (círculo + 8 rayos), dibujado con Canvas.
@Composable
private fun SunGlyph(color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier.size(24.dp)) {
    val c = center
    drawCircle(color, radius = 6f, center = c)
    for (i in 0 until 8) {
      val a = i * 45.0 * PI / 180.0
      val r1 = 9f
      val r2 = 12.5f
      drawLine(
        color,
        Offset(c.x + cos(a).toFloat() * r1, c.y + sin(a).toFloat() * r1),
        Offset(c.x + cos(a).toFloat() * r2, c.y + sin(a).toFloat() * r2),
        strokeWidth = 2.5f, cap = StrokeCap.Round
      )
    }
  }
}
