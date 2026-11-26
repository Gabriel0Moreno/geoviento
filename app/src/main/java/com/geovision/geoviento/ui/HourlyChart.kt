package com.geovision.geoviento.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geovision.geoviento.data.HourlyPoint
import com.geovision.geoviento.data.SpeedUnit

// Gráfica de líneas: ráfaga (rojo) y viento sostenido (verde),
// con banda amarilla (75%-100% del límite) y zona roja (arriba del límite).
// La línea que manda es la de ráfagas: un dron ligero aguanta el viento
// sostenido, pero una ráfaga cerca de su límite lo desplaza, gasta batería
// en corregir y puede arruinar una toma 360°.
// v1.3.0: colores adaptados al tema oscuro y unidad de velocidad según
// las Preferencias (los cálculos siguen en m/s, solo se presenta distinto).
@Composable
fun HourlyLineChart(hourly: List<HourlyPoint>, limit: Double, unit: SpeedUnit = SpeedUnit.MS) {
  if (hourly.isEmpty()) return
  val points = hourly.take(24)
  val toUnit: (Double) -> Double = { v ->
    when (unit) {
      SpeedUnit.MS -> v
      SpeedUnit.KMH -> v * 3.6
      SpeedUnit.MPH -> v * 2.23694
    }
  }
  val maxGust = points.maxOf { toUnit(it.gust10) }
  val yMax = maxOf(toUnit(15.0), maxGust * 1.15)
  val yellowY = toUnit(limit * 0.75)
  val limitU = toUnit(limit)
  val gridVals = when (unit) {
    SpeedUnit.MS -> listOf(0.0, 5.0, 10.0, 15.0)
    SpeedUnit.KMH -> listOf(0.0, 10.0, 20.0, 30.0, 40.0)
    SpeedUnit.MPH -> listOf(0.0, 10.0, 20.0, 30.0)
  }
  val labelPaint = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#9DB2C4"); textSize = 26f }
  Text("Viento y ráfagas, hora por hora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Geo.Text)
  Text("La línea que manda es la de ráfagas: un dron ligero aguanta el viento sostenido, pero una ráfaga cerca de su límite lo desplaza y puede arruinar una toma 360°.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(8.dp))
  Canvas(Modifier.fillMaxWidth().height(220.dp)) {
    val w = size.width
    val h = size.height
    val padL = 34f
    val padB = 22f
    val plotW = w - padL - 8f
    val plotH = h - padB - 8f
    fun x(i: Int) = padL + (i.toFloat() / (points.size - 1).coerceAtLeast(1)) * plotW
    fun y(v: Double) = 8f + (1f - (v / yMax).toFloat().coerceIn(0f, 1f)) * plotH
    // Banda amarilla y zona roja.
    drawRect(Color(0x22E0A800), Offset(padL, y(limitU)), androidx.compose.ui.geometry.Size(plotW, y(yellowY) - y(limitU)))
    drawRect(Color(0x22E5484D), Offset(padL, 8f), androidx.compose.ui.geometry.Size(plotW, y(limitU) - 8f))
    // Rejilla y etiquetas de velocidad.
    for (v in gridVals) {
      if (v > yMax) continue
      drawLine(Color(0x33FFFFFF), Offset(padL, y(v)), Offset(w - 8f, y(v)))
      drawContext.canvas.nativeCanvas.drawText("%.0f".format(v), 4f, y(v) + 10f, labelPaint)
    }
    // Líneas de ráfaga y sostenido.
    val gustPath = Path().apply {
      points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(toUnit(p.gust10))) else lineTo(x(i), y(toUnit(p.gust10))) }
    }
    val windPath = Path().apply {
      points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(toUnit(p.speed10))) else lineTo(x(i), y(toUnit(p.speed10))) }
    }
    drawPath(gustPath, Geo.Red, style = Stroke(width = 5f))
    drawPath(windPath, Geo.Green, style = Stroke(width = 5f))
    points.forEachIndexed { i, p ->
      drawCircle(Geo.Red, 7f, Offset(x(i), y(toUnit(p.gust10))))
      drawCircle(Geo.Green, 5f, Offset(x(i), y(toUnit(p.speed10))))
    }
    // Etiquetas de hora cada 3 horas.
    points.forEachIndexed { i, p ->
      if (i % 3 == 0) {
        val label = p.time.substringAfter("T").substringBefore(":")
        drawContext.canvas.nativeCanvas.drawText("$label:00", x(i) - 18f, h - 4f, labelPaint)
      }
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("— Ráfaga (${unit.label})", color = Geo.Red, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    Text("— Viento sostenido (${unit.label})", color = Geo.Green, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
  }
  Text("Banda amarilla: %.1f–%.1f ${unit.label}; arriba de %.1f ${unit.label} zona roja.".format(yellowY, limitU, limitU), style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
}
