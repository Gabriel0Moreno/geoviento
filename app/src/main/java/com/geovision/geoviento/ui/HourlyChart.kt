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

// Gráfica de líneas de la demo: ráfaga (rojo) y viento sostenido (verde),
// con banda amarilla (75%-100% del límite) y zona roja (arriba del límite).
// La línea que manda es la de ráfagas: un dron ligero aguanta el viento
// sostenido, pero una ráfaga cerca de su límite lo desplaza, gasta batería
// en corregir y puede arruinar una toma 360°.
@Composable
fun HourlyLineChart(hourly: List<HourlyPoint>, limit: Double) {
  if (hourly.isEmpty()) return
  val points = hourly.take(24)
  val maxGust = points.maxOf { it.gust10 }
  val yMax = maxOf(15.0, maxGust * 1.15)
  val yellowY = limit * 0.75
  Text("Viento y ráfagas, hora por hora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
  Text("La línea que manda es la de ráfagas: un dron ligero aguanta el viento sostenido, pero una ráfaga cerca de su límite lo desplaza y puede arruinar una toma 360°.", style = MaterialTheme.typography.bodySmall)
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
    drawRect(Color(0x33FFC107), Offset(padL, y(limit)), androidx.compose.ui.geometry.Size(plotW, y(yellowY) - y(limit)))
    drawRect(Color(0x33B3261E), Offset(padL, 8f), androidx.compose.ui.geometry.Size(plotW, y(limit) - 8f))
    // Rejilla y etiquetas de velocidad.
    for (v in listOf(0.0, 5.0, 10.0, 15.0)) {
      if (v > yMax) continue
      drawLine(Color(0x22000000), Offset(padL, y(v)), Offset(w - 8f, y(v)))
      drawContext.canvas.nativeCanvas.drawText("%.0f".format(v), 4f, y(v) + 10f, android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 26f })
    }
    // Líneas de ráfaga y sostenido.
    val gustPath = Path().apply {
      points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.gust10)) else lineTo(x(i), y(p.gust10)) }
    }
    val windPath = Path().apply {
      points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.speed10)) else lineTo(x(i), y(p.speed10)) }
    }
    drawPath(gustPath, Color(0xFFB3261E), style = Stroke(width = 5f))
    drawPath(windPath, Color(0xFF1B7A3D), style = Stroke(width = 5f))
    points.forEachIndexed { i, p ->
      drawCircle(Color(0xFFB3261E), 7f, Offset(x(i), y(p.gust10)))
      drawCircle(Color(0xFF1B7A3D), 5f, Offset(x(i), y(p.speed10)))
    }
    // Etiquetas de hora cada 3 horas.
    points.forEachIndexed { i, p ->
      if (i % 3 == 0) {
        val label = p.time.substringAfter("T").substringBefore(":")
        drawContext.canvas.nativeCanvas.drawText("$label:00", x(i) - 18f, h - 4f, android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 26f })
      }
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("— Ráfaga (m/s)", color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    Text("— Viento sostenido (m/s)", color = Color(0xFF1B7A3D), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
  }
  Text("Banda amarilla: %.1f–%.1f; arriba de %.1f zona roja.".format(yellowY, limit, limit), style = MaterialTheme.typography.bodySmall)
}
