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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geovision.geoviento.data.FlightPrefs
import com.geovision.geoviento.data.WindReport
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Pantalla de pronóstico por hora (v1.3.0): temperatura grande,
// condición, resto del día, pestañas Hoy/Mañana/+2 días (el pronóstico
// trae 72 h), tira por hora con prob. de lluvia y punto de estado,
// banner de horas con peligro, tarjetas "Evita despegar en esta hora",
// y la gráfica + ventanas que ya tenía la app.
@Composable
fun ForecastScreen(report: WindReport, placeName: String, limit: Double, prefs: FlightPrefs) {
  val points = report.hourly
  val todayStr = (report.now.time ?: "").substringBefore("T")
  // Días del pronóstico, agrupados por fecha (hoy, mañana, +2 días).
  val byDay = points.groupBy { it.time.substringBefore("T") }.toList().take(3)
  var dayIdx by remember { mutableStateOf(0) }
  val safeDayIdx = dayIdx.coerceIn(0, (byDay.size - 1).coerceAtLeast(0))
  val dayLabel = byDay.getOrNull(safeDayIdx)?.first ?: todayStr
  val dayPoints = byDay.getOrNull(safeDayIdx)?.second ?: emptyList()

  Text("Pronóstico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Text(placeName, style = MaterialTheme.typography.bodyMedium, color = Geo.Muted)
  Spacer(Modifier.height(8.dp))

  // Temperatura grande + condición + resto de hoy.
  val todayPoints = byDay.firstOrNull()?.second ?: points.take(24)
  val tempsToday = todayPoints.mapNotNull { it.temp10 }
  val minT = tempsToday.minOrNull()
  val maxT = tempsToday.maxOrNull()
  GeoCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          report.now.temp?.let { formatTemp(it, prefs.tempUnit) } ?: "—",
          style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = Geo.Text
        )
        Column {
          Text(conditionText(report), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Geo.Text)
          Text(
            if (minT != null && maxT != null) "Resto de hoy ${formatTemp(minT, prefs.tempUnit)}–${formatTemp(maxT, prefs.tempUnit)}"
            else "Sin rango de temperatura para hoy",
            style = MaterialTheme.typography.bodySmall, color = Geo.Muted
          )
        }
      }
    }
  }
  Spacer(Modifier.height(12.dp))

  // Pestañas por día.
  if (byDay.size > 1) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      byDay.forEachIndexed { i, (date, _) ->
        FilterChip(
          selected = safeDayIdx == i,
          onClick = { dayIdx = i },
          label = { Text(dayTabLabel(date, i)) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Geo.Accent, selectedLabelColor = Geo.Text,
            containerColor = Geo.Card, labelColor = Geo.Muted
          )
        )
      }
    }
    Spacer(Modifier.height(12.dp))
  }

  // Banner de horas con peligro del día elegido.
  val dangerHours = dayPoints.filter {
    flightLevel(it.gust10, limit) == FlightLevel.RED || (it.visibilityM != null && it.visibilityM < 1000)
  }
  if (dangerHours.isNotEmpty()) {
    val first = dangerHours.first()
    val last = dangerHours.last()
    GeoCard(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(12.dp)) {
        Text(
          "Hay horas con peligro ${hourLabel(first.time)}–${hourLabel(last.time)}",
          fontWeight = FontWeight.Bold, color = Geo.WarnText
        )
        Text(
          "Visibilidad modelada menor de 1 km o ráfaga sobre el límite. Evita despegar en esas horas.",
          style = MaterialTheme.typography.bodySmall, color = Geo.WarnText
        )
      }
    }
    Spacer(Modifier.height(12.dp))
  }

  // Tira por hora del día elegido.
  SectionTitle("Hora por hora · $dayLabel")
  Spacer(Modifier.height(8.dp))
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    dayPoints.forEach { p ->
      val lvl = flightLevel(p.gust10, limit)
      GeoCard {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(hourLabel(p.time), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Text)
          Text(p.temp10?.let { formatTemp(it, prefs.tempUnit) } ?: "—", style = MaterialTheme.typography.bodySmall, color = Geo.Text)
          Text("●", color = levelTextColor(lvl), style = MaterialTheme.typography.bodySmall)
          Text(formatSpeed(p.gust10, prefs.speedUnit), style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
          Text(p.precipProb?.let { "%.0f%% lluvia".format(it) } ?: "—", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
        }
      }
    }
  }
  Spacer(Modifier.height(12.dp))

  // Tarjetas "Evita despegar en esta hora".
  dangerHours.forEach { p ->
    GeoCard(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(12.dp)) {
        Text("Evita despegar en esta hora · ${hourLabel(p.time)}", fontWeight = FontWeight.Bold, color = Geo.Red)
        Text(
          "Ráfaga ${formatSpeed(p.gust10, prefs.speedUnit)}" +
            (p.visibilityM?.let { " · visibilidad ${formatVisibility(it)}" } ?: "") +
            (p.precipProb?.let { " · lluvia %.0f%%".format(it) } ?: "") + ".",
          style = MaterialTheme.typography.bodySmall, color = Geo.Muted
        )
      }
    }
    Spacer(Modifier.height(8.dp))
  }

  // Gráfica y ventanas (las de siempre, con la unidad elegida).
  HourlyLineChart(report.hourly, limit, prefs.speedUnit)
  Spacer(Modifier.height(12.dp))
  WindowCards(report.hourly, limit, prefs.speedUnit)
}

// Etiqueta de pestaña: Hoy / Mañana / "sáb 28/11" en español.
private fun dayTabLabel(date: String, index: Int): String {
  if (index == 0) return "Hoy"
  if (index == 1) return "Mañana"
  return try {
    val d = LocalDate.parse(date)
    val fmt = DateTimeFormatter.ofPattern("EEE dd/MM", Locale("es", "MX"))
    fmt.format(d)
  } catch (_: Exception) {
    date
  }
}
