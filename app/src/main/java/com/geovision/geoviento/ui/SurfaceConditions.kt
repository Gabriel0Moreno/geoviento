package com.geovision.geoviento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geovision.geoviento.data.WindReport
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Tarjeta "Ojo con la tarde" de la demo: si las ráfagas de la tarde suben,
// avisa a qué hora y recomienda volar en la mañana.
@Composable
fun AfternoonWarning(report: WindReport, limit: Double) {
  val afternoon = report.hourly.filter {
    val h = it.time.substringAfter("T").substringBefore(":").toIntOrNull() ?: 0
    h in 13..18
  }
  val firstBad = afternoon.firstOrNull { it.gust10 >= limit * 0.75 }
  if (firstBad == null) return
  val hour = firstBad.time.substringAfter("T").substringBefore(":")
  Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))) {
    Column(Modifier.padding(12.dp)) {
      Text("Ojo con la tarde: el viento gira al noroeste y se acelera desde las $hour:00.", fontWeight = FontWeight.Bold)
      Text("Si tu trabajo es foto de terrenos o tour 360°, programa el vuelo antes del mediodía y deja la tarde para editar.", style = MaterialTheme.typography.bodySmall)
    }
  }
}

// Leyenda del semáforo de la demo, según el límite del dron elegido.
@Composable
fun SemaphoreLegend(limit: Double) {
  val green = limit * 0.75
  Column {
    Text("Verde ráfaga < %.1f m/s – vuela normal".format(green), style = MaterialTheme.typography.bodySmall)
    Text("Amarillo %.1f–%.1f m/s – precaución, el dron ligero sufre".format(green, limit), style = MaterialTheme.typography.bodySmall)
    Text("Rojo ráfaga ≥ %.1f m/s – no despegar".format(limit), style = MaterialTheme.typography.bodySmall)
  }
}

private fun hhmm(iso: String?): String {
  if (iso.isNullOrBlank()) return "—"
  return try {
    LocalTime.parse(iso.substringAfter("T").substringBefore("Z")).format(DateTimeFormatter.ofPattern("H:mm"))
  } catch (_: Exception) {
    iso.substringAfter("T").substringBefore(":")
  }
}

private fun daylight(sunrise: String?, sunset: String?): String {
  return try {
    if (sunrise.isNullOrBlank() || sunset.isNullOrBlank()) return "—"
    val s = LocalTime.parse(sunrise.substringAfter("T"))
    val e = LocalTime.parse(sunset.substringAfter("T"))
    val mins = java.time.Duration.between(s, e).toMinutes()
    "${mins / 60}h ${mins % 60} min de luz"
  } catch (_: Exception) {
    "—"
  }
}

// Condiciones en superficie de la demo: semáforo, humedad, presión,
// probabilidad de lluvia, UV, amanecer/atardecer y calidad del aire.
// (La dirección ya está en la brújula, no se repite aquí.)
@Composable
fun SurfaceConditions(report: WindReport, limit: Double) {
  val n = report.now
  val s = report.surface
  val a = report.air
  val level = flightLevel(n.gust10, limit)
  val levelColor = when (level) {
    FlightLevel.GREEN -> Color(0xFF1B7A3D)
    FlightLevel.YELLOW -> Color(0xFF8A6D00)
    FlightLevel.RED -> Color(0xFFB3261E)
  }
  val levelName = when (level) {
    FlightLevel.GREEN -> "VERDE"
    FlightLevel.YELLOW -> "AMARILLO"
    FlightLevel.RED -> "ROJO"
  }
  val maxPrecip = report.hourly.mapNotNull { it.precipProb }.maxOrNull()
  val maxPrecipHour = report.hourly.firstOrNull { it.precipProb == maxPrecip }?.time?.substringAfter("T")?.substringBefore(":")
  val uv = s?.uvIndex
  val uvText = when {
    uv == null -> "—"
    uv < 3 -> "Bajo"
    uv < 6 -> "Moderado"
    uv < 8 -> "Alto"
    uv < 11 -> "Muy alto"
    else -> "Extremo"
  }
  val aqi = a?.aqiUs
  val aqiText = when {
    aqi == null -> "—"
    aqi <= 50 -> "Buena"
    aqi <= 100 -> "Moderada"
    aqi <= 150 -> "Dañina para grupos sensibles"
    else -> "Dañina"
  }
  Text("Condiciones en superficie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
  Text("Ahora", style = MaterialTheme.typography.bodySmall)
  Spacer(Modifier.height(8.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SurfaceTile("SEMÁFORO AHORA", levelName, "%.1f m/s está %s del límite".format(n.gust10, if (level == FlightLevel.GREEN) "muy debajo" else "cerca"), Modifier.weight(1f), levelColor)
  }
  Spacer(Modifier.height(8.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SurfaceTile("HUMEDAD", s?.humidity?.let { "%.0f %%".format(it) } ?: "—", if ((s?.humidity ?: 0.0) >= 90) "Amanecer húmedo: revisa que el lente no se empañe" else "—", Modifier.weight(1f))
    SurfaceTile("PRESIÓN", s?.pressure?.let { "%.0f hPa".format(it) } ?: "—", "Presión en superficie", Modifier.weight(1f))
  }
  Spacer(Modifier.height(8.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SurfaceTile("PROB. LLUVIA", s?.precipProbNow?.let { "%.0f %%".format(it) } ?: "—", if (maxPrecip != null && maxPrecipHour != null) "Sube solo a %.0f%% a las %s:00".format(maxPrecip, maxPrecipHour) else "—", Modifier.weight(1f))
    SurfaceTile("ÍNDICE UV", uv?.let { "%.0f".format(it) } ?: "—", uvText, Modifier.weight(1f))
  }
  Spacer(Modifier.height(8.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SurfaceTile("AMANECER / ATARDECER", "${hhmm(s?.sunrise)} / ${hhmm(s?.sunset)}", daylight(s?.sunrise, s?.sunset), Modifier.weight(1f))
    SurfaceTile("CALIDAD DEL AIRE", aqi?.let { "%.0f ICA".format(it) } ?: "—", "$aqiText${a?.pm25?.let { " (PM2.5 %.0f)".format(it) } ?: ""}", Modifier.weight(1f))
  }
}

@Composable
private fun SurfaceTile(title: String, value: String, sub: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
  Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EEF2))) {
    Column(Modifier.padding(12.dp)) {
      Text(title, style = MaterialTheme.typography.bodySmall)
      Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor ?: Color.Unspecified)
      Text(sub, style = MaterialTheme.typography.bodySmall)
    }
  }
}
