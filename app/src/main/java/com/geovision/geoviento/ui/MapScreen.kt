package com.geovision.geoviento.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Mapa de corrientes: ESQUEMA dibujado con Canvas (no son baldosas satelitales en
// vivo). Muestra las 5 zonas de Tuxtla, el Cañón del Sumidero y flechas con la
// dirección real del viento. El mapa con relieve y baldosas MapLibre queda para
// una siguiente versión; este esquema no se cae sin internet de mapas.
object MapStyle {
  const val STYLE = "https://demotiles.maplibre.org/style.json"
  const val TERRAIN_URL = "https://demotiles.maplibre.org/terrain-tiles/tiles.json"
}

private data class Zone(val name: String, val fx: Float, val fy: Float)

@Composable
fun CurrentsMap(dirFrom: Double, gust10: Double, modifier: Modifier = Modifier) {
  val zones = listOf(
    Zone("Centro", 0.42f, 0.55f),
    Zone("Real del Bosque", 0.28f, 0.78f),
    Zone("Cañón del Sumidero", 0.72f, 0.28f),
    Zone("Copoya", 0.58f, 0.88f),
    Zone("Chiapa de Corzo", 0.86f, 0.62f)
  )
  // El viento "viene de" dirFrom y "va hacia" dirFrom + 180.
  val toRad = ((dirFrom + 180.0) % 360.0) * PI / 180.0
  val dx = sin(toRad).toFloat()
  val dy = (-cos(toRad)).toFloat()
  Canvas(modifier = modifier) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFE8F0E4))
    // Franja del Cañón del Sumidero (diagonal).
    drawLine(Color(0xFF8FA8BF), Offset(w * 0.55f, 0f), Offset(w * 0.95f, h * 0.55f), strokeWidth = h * 0.16f)
    drawLine(Color(0xFF5B7A99), Offset(w * 0.55f, 0f), Offset(w * 0.95f, h * 0.55f), strokeWidth = h * 0.03f)
    // Flechas de corriente en la dirección real del viento.
    val arrowColor = if (gust10 >= 10.7) Color(0xFFB3261E) else if (gust10 >= 8.0) Color(0xFF8A6D00) else Color(0xFF1B7A3D)
    val starts = listOf(Offset(w * 0.15f, h * 0.30f), Offset(w * 0.35f, h * 0.55f), Offset(w * 0.60f, h * 0.75f))
    for (s in starts) {
      val e = Offset(s.x + dx * w * 0.18f, s.y + dy * h * 0.18f)
      drawLine(arrowColor, s, e, strokeWidth = 6f)
      // Punta de flecha.
      val ang = kotlin.math.atan2((e.y - s.y).toDouble(), (e.x - s.x).toDouble())
      val a1 = ang + 2.6; val a2 = ang - 2.6
      drawLine(arrowColor, e, Offset((e.x + cos(a1) * 28).toFloat(), (e.y + sin(a1) * 28).toFloat()), strokeWidth = 6f)
      drawLine(arrowColor, e, Offset((e.x + cos(a2) * 28).toFloat(), (e.y + sin(a2) * 28).toFloat()), strokeWidth = 6f)
    }
    // Zonas.
    val paint = Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 30f }
    for (z in zones) {
      val p = Offset(w * z.fx, h * z.fy)
      drawCircle(Color(0xFF0A2E4D), radius = 12f, center = p)
      drawCircle(Color.White, radius = 5f, center = p)
      drawIntoCanvas { it.nativeCanvas.drawText(z.name, p.x + 18f, p.y + 10f, paint) }
    }
  }
}

@Composable
fun WindCompass(dirFrom: Double, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier.size(120.dp)) {
    val c = center; val r = size.minDimension / 2f - 8f
    drawCircle(Color(0xFFE8EEF2), radius = r, center = c)
    drawCircle(Color(0xFF0A2E4D), radius = r, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
    // Flecha: apunta hacia donde VA el viento (dirFrom + 180).
    rotate((dirFrom + 180.0).toFloat(), pivot = c) {
      drawLine(Color(0xFFB3261E), Offset(c.x, c.y + r * 0.7f), Offset(c.x, c.y - r * 0.7f), strokeWidth = 8f)
      drawCircle(Color(0xFFB3261E), radius = 10f, center = c)
    }
    val paint = Paint().apply { color = android.graphics.Color.BLACK; textSize = 34f; textAlign = Paint.Align.CENTER }
    drawIntoCanvas {
      it.nativeCanvas.drawText("N", c.x, c.y - r + 34f, paint)
      it.nativeCanvas.drawText("S", c.x, c.y + r - 8f, paint)
    }
  }
}
