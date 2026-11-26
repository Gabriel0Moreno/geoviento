package com.geovision.geoviento.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geovision.geoviento.data.SpeedUnit
import com.geovision.geoviento.data.WindReport
import kotlin.math.roundToInt

// Calculadora de viento a altura de vuelo de la demo: elige la hora del vuelo
// y la altura, y estima viento y ráfaga con el perfil logarítmico
// (terreno con obstáculos, z0 = 0.3 m). Es una estimación para decidir, no
// una medición: si el resultado cae en amarillo, verifica en sitio con un
// ascenso corto y controlado.
@Composable
fun AltitudeCalculator(report: WindReport, limit: Double, onAltitudeChange: (Int) -> Unit, unit: SpeedUnit = SpeedUnit.MS) {
  val points = report.hourly.take(24)
  var hourIdx by remember { mutableStateOf(0) }
  var altitude by remember { mutableStateOf(60) }
  val chosen = points.getOrNull(hourIdx)
  val factor = logWindFactor(altitude.toDouble())
  Text("Viento a altura de vuelo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Geo.Text)
  Text("ESTIMACIÓN · PERFIL LOGARÍTMICO", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Muted)
  Text("El viento crece con la altura porque el suelo lo frena. Esta calculadora estima el viento y la ráfaga a la altura que planeas volar, usando el pronóstico de esa hora en superficie y un perfil para terreno con obstáculos.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(8.dp))
  Text("Hora del vuelo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    points.forEachIndexed { i, p ->
      val h = p.time.substringAfter("T").substringBefore(":")
      FilterChip(
        selected = i == hourIdx,
        onClick = { hourIdx = i },
        label = { Text("$h:00") }
      )
    }
  }
  Spacer(Modifier.height(8.dp))
  Text("Altura de vuelo: $altitude m", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Slider(
    value = altitude.toFloat(),
    onValueChange = { altitude = it.roundToInt().coerceIn(10, 120); onAltitudeChange(altitude) },
    valueRange = 10f..120f
  )
  Text("10 m    60 m    120 m máx. (regla general)", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  if (chosen != null) {
    val windH = chosen.speed10 * factor
    val gustH = chosen.gust10 * factor
    val level = flightLevel(gustH, limit)
    val levelColor = levelTextColor(level)
    Text("Factor de crecimiento aplicado: ×%.2f respecto al viento a 10 m. En terreno despejado crece menos; entre cerros y edificios, más.".format(factor), style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
    Spacer(Modifier.height(8.dp))
    Text(formatSpeed(windH, unit), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = levelColor)
    val hourLabel = chosen.time.substringAfter("T").substringBefore(":")
    Text("A las $hourLabel:00 y $altitude m: viento ${formatSpeed(windH, unit)}, ráfaga estimada ${formatSpeed(gustH, unit)}.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
    Text("Margen respecto al límite de ${formatSpeed(limit, unit)}: ${formatSpeed(limit - gustH, unit)}.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
    Text(level.label, color = levelColor, fontWeight = FontWeight.Bold)
    Text("RÁFAGA ESTIMADA A ESA ALTURA: ${formatSpeed(gustH, unit)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  }
}

// Checklist previo al vuelo de la demo, con los textos calculados con los
// datos de hoy (ventana de la mañana, diferencia viento/ráfaga, dirección
// del flujo de la tarde y humedad).
@Composable
fun PreflightChecklist(report: WindReport, limit: Double, checked: List<Boolean>, onToggle: (Int) -> Unit, unit: SpeedUnit = SpeedUnit.MS) {
  val points = report.hourly.take(24)
  val morning = points.filter {
    val h = it.time.substringAfter("T").substringBefore(":").toIntOrNull() ?: 0
    h in 6..11
  }
  val morningMax = morning.maxOfOrNull { it.gust10 } ?: 0.0
  val maxDiff = points.maxByOrNull { it.gust10 - it.speed10 }
  val maxDiffHour = maxDiff?.time?.substringAfter("T")?.substringBefore(":") ?: "—"
  val afternoonDir = points.firstOrNull {
    (it.time.substringAfter("T").substringBefore(":").toIntOrNull() ?: 0) >= 15
  }?.dir10 ?: report.now.dir10
  val toDir = compass((afternoonDir + 180) % 360)
  val fromDir = compass(afternoonDir)
  val humidity = report.surface?.humidity
  // El punto del cañón y la meseta solo aplica cerca de Tuxtla.
  val nearTuxtla = kotlin.math.abs(report.lat - 16.75) < 0.5 && kotlin.math.abs(report.lon - (-93.11)) < 0.5
  val items = buildList {
    add("Elige la mañana" to "Entre 06:00 y 12:00 el viento se mantiene con ráfagas de hasta ${formatSpeed(morningMax, unit)}. Deja los vuelos de la tarde solo si bajan las ráfagas.")
    add("Revisa la ráfaga, no solo el viento" to "Hoy la diferencia llega a ${formatSpeed((maxDiff?.gust10 ?: 0.0) - (maxDiff?.speed10 ?: 0.0), unit)} a las $maxDiffHour:00 (${formatSpeed(maxDiff?.speed10 ?: 0.0, unit)} sostenido vs ${formatSpeed(maxDiff?.gust10 ?: 0.0, unit)} de ráfaga): ese empujón es el que desplaza el dron.")
    add("Plan de regreso con viento en contra" to "Por la tarde el flujo va hacia el $toDir (viene del $fromDir): si vuelas en contra del flujo, el regreso es a favor; si vuelas a favor, reserva batería extra.")
    add("Lente seca al amanecer" to if ((humidity ?: 0.0) >= 85) "Humedad %.0f%% al inicio del día: limpia el lente justo antes de despegar y guarda el dron en su estuche entre vuelos.".format(humidity) else "Humedad %.0f%%: revisa el lente antes de despegar.".format(humidity ?: 0.0))
    add("Ascenso de prueba de 20 segundos" to "Sube a 20-30 m, suelta los controles y observa cuánto corre el dron antes de alejarte. Si pelea, baja y espera otra ventana.")
    if (nearTuxtla) add("Cuidado cerca del cañón y la meseta" to "Canalización del norte y turbulencia a sotavento de Copoya: mantén distancia del filo y las paredes con viento del norte.")
  }
  Text("Antes de despegar hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Geo.Text)
  Text("CON LOS DATOS DE ESTE DÍA", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Muted)
  items.forEachIndexed { i, (title, text) ->
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
      Checkbox(checked = checked.getOrElse(i) { false }, onCheckedChange = { onToggle(i) })
      Column {
        Text(title, fontWeight = FontWeight.Bold, color = Geo.Text)
        Text(text, style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      }
    }
  }
}
