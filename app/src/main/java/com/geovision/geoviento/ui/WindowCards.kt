package com.geovision.geoviento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geovision.geoviento.data.HourlyPoint

private data class Run(val start: Int, val end: Int, val level: FlightLevel)
private data class WindowCard(val title: String, val chip: String, val color: Color, val text: String)

private fun hourLabel(time: String): String {
  val h = time.substringAfter("T").substringBefore(":")
  return if (h.length == 2) "$h:00" else time
}

// Tarjetas de ventanas de la demo, calculadas con los datos en vivo:
// ventana buena (racha verde más larga), transición (primer amarillo),
// evita volar (racha con la ráfaga máxima) y se calma (verde después del pico).
@Composable
fun WindowCards(hourly: List<HourlyPoint>, limit: Double) {
  val points = hourly.take(24)
  if (points.isEmpty()) return
  val runs = mutableListOf<Run>()
  var start = 0
  for (i in 1..points.size) {
    val prevLevel = flightLevel(points[i - 1].gust10, limit)
    val curLevel = if (i < points.size) flightLevel(points[i].gust10, limit) else null
    if (curLevel != prevLevel) {
      runs.add(Run(start, i - 1, prevLevel))
      start = i
    }
  }
  val greenRuns = runs.filter { it.level == FlightLevel.GREEN }
  val yellowRuns = runs.filter { it.level == FlightLevel.YELLOW }
  val redRuns = runs.filter { it.level == FlightLevel.RED }
  val bestGreen = greenRuns.maxByOrNull { it.end - it.start }
  val maxIdx = points.indices.maxByOrNull { points[it].gust10 } ?: 0
  val maxRun = runs.firstOrNull { maxIdx in it.start..it.end }
  val calmAfter = greenRuns.firstOrNull { it.start > (maxRun?.end ?: -1) }
  val cards = mutableListOf<WindowCard>()
  bestGreen?.let { r ->
    val minS = points.subList(r.start, r.end + 1).minOf { it.speed10 }
    val maxS = points.subList(r.start, r.end + 1).maxOf { it.speed10 }
    val label = if (r.end - r.start + 1 >= 24) "Todo el día" else "${hourLabel(points[r.start].time)}–${hourLabel(points[r.end].time)}"
    cards.add(WindowCard("Ventana buena · $label", "VERDE", Color(0xFF1B7A3D), "Viento de %.1f a %.1f m/s, sin ráfagas reportadas y con luz creciente. Ideal para foto de terrenos y recorridos.".format(minS, maxS)))
  }
  yellowRuns.firstOrNull()?.let { r ->
    val minG = points.subList(r.start, r.end + 1).minOf { it.gust10 }
    val maxG = points.subList(r.start, r.end + 1).maxOf { it.gust10 }
    cards.add(WindowCard("Transición · ${hourLabel(points[r.start].time)}–${hourLabel(points[r.end].time)}", "AMARILLO", Color(0xFF8A6D00), "Ráfagas de %.1f a %.1f m/s del noroeste. Vuelos cortos, cerca y sin alejar el dron: regresa con batería de sobra.".format(minG, maxG)))
  }
  maxRun?.let { r ->
    if (r.level != FlightLevel.GREEN) {
      val minG = points.subList(r.start, r.end + 1).minOf { it.gust10 }
      val maxG = points.subList(r.start, r.end + 1).maxOf { it.gust10 }
      val chip = if (r.level == FlightLevel.RED) "ROJO" else "AMARILLO ALTO"
      val advice = if (r.level == FlightLevel.RED) "No despegar: la ráfaga supera el límite de tu dron. Espera a que baje." else "Ráfagas de %.1f a %.1f m/s: precaución, el dron ligero sufre y la toma 360° se mueve.".format(minG, maxG)
      cards.add(WindowCard("Evita volar · ${hourLabel(points[r.start].time)}–${hourLabel(points[r.end].time)}", chip, if (r.level == FlightLevel.RED) Color(0xFFB3261E) else Color(0xFF8A6D00), advice))
    }
  }
  calmAfter?.let { r ->
    val minG = points.subList(r.start, r.end + 1).minOf { it.gust10 }
    val maxG = points.subList(r.start, r.end + 1).maxOf { it.gust10 }
    cards.add(WindowCard("Se calma · ${hourLabel(points[r.start].time)}–${hourLabel(points[r.end].time)}", "VERDE", Color(0xFF1B7A3D), "Las ráfagas bajan de %.1f a %.1f m/s. Solo aplica para vuelos cortos con luz suficiente.".format(maxG, minG)))
  }
  if (cards.isEmpty()) return
  Text("Ventanas del día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
  Spacer(Modifier.height(8.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    cards.take(2).forEach { c -> WindowCardBox(c, Modifier.weight(1f)) }
    if (cards.size == 1) Spacer(Modifier.weight(1f))
  }
  if (cards.size > 2) {
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      cards.drop(2).take(2).forEach { c -> WindowCardBox(c, Modifier.weight(1f)) }
      if (cards.size == 3) Spacer(Modifier.weight(1f))
    }
  }
}

@Composable
private fun WindowCardBox(c: WindowCard, modifier: Modifier = Modifier) {
  Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EEF2))) {
    Column(Modifier.padding(12.dp)) {
      Text(c.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(c.chip, color = c.color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
      Text(c.text, style = MaterialTheme.typography.bodySmall)
    }
  }
}
