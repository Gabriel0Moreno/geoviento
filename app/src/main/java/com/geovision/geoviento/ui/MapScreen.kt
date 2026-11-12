package com.geovision.geoviento.ui

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Mapa de corrientes: ESQUEMA dibujado con Canvas (no son baldosas satelitales en
// vivo). Muestra las 5 zonas de Tuxtla, el Cañón del Sumidero y flechas animadas
// con la dirección real del viento. Tocar una zona muestra su nota.
// El mapa real con baldosas es un WebView (Leaflet + OSM) en WebMap.kt:
// el mapa nativo MapLibre tumbaba la app al cargarlo con un fallo que el
// handler de Java no atrapa. Este esquema es el respaldo si el WebView no
// carga (sin internet de mapas).

data class Zone(val name: String, val fx: Float, val fy: Float, val note: String)

val TUxtla_ZONES = listOf(
  Zone("Centro", 0.42f, 0.55f, "Zona urbana: el viento se frena entre edificios y cambia de dirección en las calles."),
  Zone("Real del Bosque", 0.28f, 0.78f, "Fraccionamiento al sur: viento parecido al de Centro, algo más limpio por las avenidas anchas."),
  Zone("Cañón del Sumidero", 0.72f, 0.28f, "El viento se canaliza entre las paredes del cañón y la ráfaga local puede subir ~45%. No vueles dentro del cañón con ráfaga alta."),
  Zone("Copoya", 0.58f, 0.88f, "Al sur, más alto: el aire sube por la ladera (ascenso) y puede empujar el dron hacia arriba."),
  Zone("Chiapa de Corzo", 0.86f, 0.62f, "Junto al río Grijalva: brisa del río por la tarde, con cambios bruscos de dirección.")
)

@Composable
fun CurrentsMap(
  dirFrom: Double, gust10: Double,
  playing: Boolean, intensity: Float,
  selectedZone: String?, onZoneSelected: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var sizePx by remember { mutableStateOf(IntSize.Zero) }
  val transition = rememberInfiniteTransition(label = "flujo")
  val flow by transition.animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(animation = tween(2200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
    label = "offset"
  )
  val offset = if (playing) flow else 0f
  // El viento "viene de" dirFrom y "va hacia" dirFrom + 180.
  val toRad = ((dirFrom + 180.0) % 360.0) * PI / 180.0
  val dx = sin(toRad).toFloat()
  val dy = (-cos(toRad)).toFloat()
  Canvas(
    modifier = modifier
      .onSizeChanged { sizePx = it }
      .pointerInput(Unit) {
        detectTapGestures { tap ->
          if (sizePx.width <= 0) return@detectTapGestures
          var best: Zone? = null; var bestD = Float.MAX_VALUE
          for (z in TUxtla_ZONES) {
            val zx = z.fx * sizePx.width; val zy = z.fy * sizePx.height
            val d = sqrt((tap.x - zx) * (tap.x - zx) + (tap.y - zy) * (tap.y - zy))
            if (d < bestD) { bestD = d; best = z }
          }
          if (best != null && bestD < 140f) onZoneSelected(best.name)
        }
      }
  ) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFE8F0E4))
    // Franja del Cañón del Sumidero (diagonal).
    drawLine(Color(0xFF8FA8BF), Offset(w * 0.55f, 0f), Offset(w * 0.95f, h * 0.55f), strokeWidth = h * 0.16f)
    drawLine(Color(0xFF5B7A99), Offset(w * 0.55f, 0f), Offset(w * 0.95f, h * 0.55f), strokeWidth = h * 0.03f)
    // Flechas de corriente en la dirección real del viento, con animación de flujo.
    val arrowColor = if (gust10 >= 10.7) Color(0xFFB3261E) else if (gust10 >= 8.0) Color(0xFF8A6D00) else Color(0xFF1B7A3D)
    val starts = listOf(Offset(w * 0.15f, h * 0.30f), Offset(w * 0.35f, h * 0.55f), Offset(w * 0.60f, h * 0.75f))
    for ((i, s0) in starts.withIndex()) {
      // Cada flecha avanza y vuelve: el desplazamiento depende de la animación.
      val shift = offset * w * 0.10f * intensity
      val s = Offset(s0.x + dx * shift - dx * (i * 8f), s0.y + dy * shift - dy * (i * 8f))
      val len = w * 0.18f * intensity
      val e = Offset(s.x + dx * len, s.y + dy * len)
      drawLine(arrowColor, s, e, strokeWidth = 6f)
      val ang = kotlin.math.atan2((e.y - s.y).toDouble(), (e.x - s.x).toDouble())
      val a1 = ang + 2.6; val a2 = ang - 2.6
      drawLine(arrowColor, e, Offset((e.x + cos(a1) * 28).toFloat(), (e.y + sin(a1) * 28).toFloat()), strokeWidth = 6f)
      drawLine(arrowColor, e, Offset((e.x + cos(a2) * 28).toFloat(), (e.y + sin(a2) * 28).toFloat()), strokeWidth = 6f)
    }
    // Zonas (la seleccionada se marca con anillo).
    val paint = Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 30f }
    for (z in TUxtla_ZONES) {
      val p = Offset(w * z.fx, h * z.fy)
      if (z.name == selectedZone) {
        drawCircle(Color(0xFFFFC107), radius = 22f, center = p)
      }
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
