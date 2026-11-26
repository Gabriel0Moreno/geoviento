package com.geovision.geoviento.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Identidad visual propia de Geoviento Mx (v1.3.0): tema oscuro en la
// familia de azul marino, con el azul profundo de GeoVisión (#0A2E4D) como
// base. No es una copia de DroneCheck: misma familia de layout (tarjetas
// oscuras redondeadas, navegación inferior), piel propia.
object Geo {
  val Bg = Color(0xFF0A1622)        // fondo casi negro, azul marino
  val Card = Color(0xFF152A3D)      // tarjeta oscura
  val Card2 = Color(0xFF1B3247)     // tarjeta secundaria / seleccionada
  val Text = Color(0xFFF2F6F9)      // texto principal
  val Muted = Color(0xFF9DB2C4)     // texto secundario gris azulado
  val Accent = Color(0xFF2BB3A3)    // teal propio para selección y enlaces
  val Green = Color(0xFF4CAF7D)     // verde legible sobre oscuro
  val GreenDark = Color(0xFF1B7A3D) // verde de marca (fondos)
  val Amber = Color(0xFFE0A800)     // ámbar legible sobre oscuro
  val AmberDark = Color(0xFF8A6D00)
  val Red = Color(0xFFE5484D)       // rojo legible sobre oscuro
  val RedDark = Color(0xFFB3261E)
  val WarnBg = Color(0xFF33270A)    // fondo ámbar oscuro (avisos)
  val WarnText = Color(0xFFFFD54D)
  val ErrorBg = Color(0xFF3D1512)   // fondo rojo oscuro (errores)
  val ErrorText = Color(0xFFFF8A80)
  val SelectedGreenBg = Color(0xFF1E4D3A) // zona seleccionada
}

// Color de texto del semáforo según el nivel, legible sobre fondo oscuro.
fun levelTextColor(level: FlightLevel): Color = when (level) {
  FlightLevel.GREEN -> Geo.Green
  FlightLevel.YELLOW -> Geo.Amber
  FlightLevel.RED -> Geo.Red
}

@Composable
fun GeovientoTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = darkColorScheme(
      primary = Geo.Accent,
      secondary = Geo.Accent,
      background = Geo.Bg,
      surface = Geo.Card,
      surfaceVariant = Geo.Card2,
      onBackground = Geo.Text,
      onSurface = Geo.Text,
      onSurfaceVariant = Geo.Muted,
      error = Geo.Red
    ),
    content = content
  )
}

// Tarjeta oscura estándar de la app (esquinas redondeadas, sin borde).
@Composable
fun GeoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Geo.Card),
    content = { content() }
  )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    modifier = modifier,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = Geo.Text
  )
}

// Chip pequeño de estado (Dentro del umbral / Peligro / Pendiente).
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.18f))
  ) {
    Text(
      text,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
      color = color,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

// Glifos de la navegación inferior, dibujados con Canvas para no depender
// de ningún paquete de iconos extra: casa, mapa con pin, gráfica,
// calendario (Agenda, v1.5.0) y "más".
enum class NavKind { HOME, MAP, CHART, AGENDA, MORE }

@Composable
fun NavGlyph(kind: NavKind, selected: Boolean, modifier: Modifier = Modifier) {
  val color = if (selected) Geo.Accent else Geo.Muted
  Canvas(modifier = modifier.size(24.dp)) {
    val w = size.width
    val h = size.height
    val s = Stroke(width = 2.5f)
    when (kind) {
      NavKind.HOME -> {
        val roof = Path().apply {
          moveTo(2f, h * 0.55f); lineTo(w / 2f, 2f); lineTo(w - 2f, h * 0.55f)
        }
        drawPath(roof, color, style = s)
        drawLine(color, Offset(4f, h * 0.5f), Offset(4f, h - 2f), strokeWidth = 2.5f)
        drawLine(color, Offset(w - 4f, h * 0.5f), Offset(w - 4f, h - 2f), strokeWidth = 2.5f)
        drawLine(color, Offset(4f, h - 2f), Offset(w - 4f, h - 2f), strokeWidth = 2.5f)
      }
      NavKind.MAP -> {
        drawCircle(color, radius = 6f, center = Offset(w / 2f, h * 0.32f), style = s)
        val pin = Path().apply {
          moveTo(w / 2f - 6f, h * 0.36f)
          lineTo(w / 2f, h * 0.85f)
          lineTo(w / 2f + 6f, h * 0.36f)
        }
        drawPath(pin, color, style = s)
        drawCircle(color, radius = 2f, center = Offset(w / 2f, h * 0.32f))
      }
      NavKind.CHART -> {
        val line = Path().apply {
          moveTo(2f, h * 0.7f); lineTo(w * 0.35f, h * 0.45f); lineTo(w * 0.6f, h * 0.6f); lineTo(w - 2f, h * 0.2f)
        }
        drawPath(line, color, style = s)
        drawCircle(color, radius = 2.5f, center = Offset(w - 2f, h * 0.2f))
        drawLine(color, Offset(2f, h - 2f), Offset(w - 2f, h - 2f), strokeWidth = 2f)
      }
      NavKind.AGENDA -> {
        // Calendario: rectángulo redondeado con dos pestañas arriba y
        // dos renglones de texto.
        drawRoundRect(color, Offset(3f, 5f), Size(w - 6f, h - 7f), CornerRadius(3f, 3f), style = s)
        drawLine(color, Offset(3f, 9.5f), Offset(w - 3f, 9.5f), strokeWidth = 2.5f)
        drawLine(color, Offset(w * 0.32f, 2f), Offset(w * 0.32f, 6f), strokeWidth = 2.5f)
        drawLine(color, Offset(w * 0.68f, 2f), Offset(w * 0.68f, 6f), strokeWidth = 2.5f)
        drawLine(color, Offset(7f, 14f), Offset(w - 7f, 14f), strokeWidth = 2f)
        drawLine(color, Offset(7f, 18f), Offset(w - 7f, 18f), strokeWidth = 2f)
      }
      NavKind.MORE -> {
        for (i in 0..2) drawCircle(color, radius = 3f, center = Offset(w * (0.2f + 0.3f * i), h / 2f))
      }
    }
  }
}
