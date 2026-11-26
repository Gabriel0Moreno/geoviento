package com.geovision.geoviento.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geovision.geoviento.data.WindReport

// Aviso si la primera tarde con viento fuerte llega hoy, y en qué sentido
// va a girar el viento en la tarde. Solo se mira la tarde DE HOY.
@Composable
fun AfternoonWarning(report: WindReport, limit: Double) {
  // Solo la tarde DE HOY: el pronóstico ya trae 72 h y sin el filtro
  // por fecha el aviso podría dispararse por la tarde de mañana.
  val todayStr = (report.now.time ?: "").substringBefore("T")
  val afternoon = report.hourly.filter {
    val h = it.time.substringAfter("T").substringBefore(":").toIntOrNull() ?: 0
    it.time.startsWith(todayStr) && h in 13..18
  }
  val firstBad = afternoon.firstOrNull { it.gust10 >= limit * 0.75 }
  val turn = firstBad?.let {
    val before = afternoon.filter { hourInt(it.time) < hourInt(firstBad.time) }.lastOrNull()
    val b = before?.dir10 ?: report.now.dir10
    val d = firstBad.dir10
    val diff = (d - b + 540) % 360 - 180
    if (kotlin.math.abs(diff) > 45) diff else null
  }
  if (firstBad == null) return
  GeoCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
      Text(
        "Ojo con la tarde: a las " + hourLabel(firstBad.time) + " la ráfaga sube a %.1f m/s.".format(firstBad.gust10),
        color = Geo.WarnText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
      )
      if (turn != null) {
        Text(
          "El viento gira " + (if (turn > 0) "a la derecha" else "a la izquierda") + " en la tarde: programa el regreso.",
          color = Geo.WarnText, style = MaterialTheme.typography.bodyMedium
        )
      }
    }
  }
}

@Composable
fun SemaphoreLegend() {
  Column(Modifier.fillMaxWidth()) {
    Text("Semáforo: verde = vuela tranquilo · amarillo = con cuidado · rojo = no vueles", color = Geo.Muted, style = MaterialTheme.typography.bodySmall)
    Text("El límite es el de tu dron. La ráfaga es la que manda, no el viento sostenido.", color = Geo.Muted, style = MaterialTheme.typography.bodySmall)
  }
}

// Condiciones en superficie (demo): tiles de humedad, presión, UV,
// amanecer/atardecer y calidad del aire. v1.3.0: se quitaron el tile del
// semáforo (lo repite el banner de inicio) y el de prob. de lluvia (lo
// repite la tarjeta 2×2 de inicio).
@Composable
fun SurfaceConditions(report: WindReport) {
  val s = report.surface ?: return
  val tiles = listOf(
    Triple("HUMEDAD", s.humidity?.let { "%.0f%%".format(it) } ?: "—", "Humedad relativa"),
    Triple("PRESIÓN", s.pressure?.let { "%.0f hPa".format(it) } ?: "—", "Presión en superficie"),
    Triple(
      "ÍNDICE UV",
      s.uvIndex?.let { "%.1f".format(it) } ?: "—",
      when {
        s.uvIndex == null -> "Sin dato"
        s.uvIndex < 3 -> "Bajo"
        s.uvIndex < 6 -> "Moderado"
        s.uvIndex < 8 -> "Alto"
        s.uvIndex < 11 -> "Muy alto"
        else -> "Extremo"
      }
    ),
    Triple(
      "AMANECER / ATARDECER",
      (s.sunrise?.substringAfter("T") ?: "—") + " / " + (s.sunset?.substringAfter("T") ?: "—"),
      s.sunrise?.let { sr ->
        s.sunset?.let { ss ->
          val h1 = sr.substringAfter("T").substringBefore(":").toIntOrNull() ?: 0
          val m1 = sr.substringAfter(":").substringBefore(":").toIntOrNull() ?: 0
          val h2 = ss.substringAfter("T").substringBefore(":").toIntOrNull() ?: 0
          val m2 = ss.substringAfter(":").substringBefore(":").toIntOrNull() ?: 0
          val mins = (h2 * 60 + m2) - (h1 * 60 + m1)
          if (mins > 0) "Luz de día: ${mins / 60} h ${mins % 60} min" else "Luz de día: —"
        }
      } ?: "Luz de día: —"
    ),
    Triple(
      "CALIDAD DEL AIRE",
      report.air?.aqiUs?.let { "AQI %.0f".format(it) } ?: "—",
      run {
        val aqi = report.air?.aqiUs
        val pm = report.air?.pm25?.let { " · PM2.5 %.1f".format(it) } ?: ""
        when {
          aqi == null -> "Sin dato"
          aqi <= 50 -> "Buena$pm"
          aqi <= 100 -> "Moderada$pm"
          aqi <= 150 -> "Dañina para sensibles"
          else -> "Dañina"
        }
      }
    )
  )
  Column {
    SectionTitle("Condiciones en superficie")
    Spacer(Modifier.height(8.dp))
    for (row in tiles.chunked(2)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { (label, value, sub) ->
          SurfaceTile(label, value, sub, Modifier.weight(1f))
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
private fun SurfaceTile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
  GeoCard(modifier) {
    Column(Modifier.padding(12.dp)) {
      Text(label, color = Geo.Muted, style = MaterialTheme.typography.labelSmall)
      Text(value, color = Geo.Text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(sub, color = Geo.Muted, style = MaterialTheme.typography.bodySmall)
    }
  }
}
