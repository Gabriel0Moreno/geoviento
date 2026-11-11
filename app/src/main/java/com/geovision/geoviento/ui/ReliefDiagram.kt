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

// Corte esquemático del valle de la demo (norte a sur): el flujo dominante
// entra por la boca del Cañón del Sumidero, cruza la ciudad y choca con la
// Meseta de Copoya. No es un mapa ni una medición: es el patrón que debes
// anticipar al planear la ruta.
@Composable
fun ReliefSection() {
  Text("Cómo se mueve el aire hoy sobre el relieve", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
  Text("ESQUEMA EDUCATIVO · VIENTO N-O", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
  Text("Corte esquemático del valle, de norte a sur: el flujo dominante entra por la boca del Cañón del Sumidero, cruza la ciudad y choca con la Meseta de Copoya. No es un mapa ni una medición: es el patrón que debes anticipar al planear la ruta.", style = MaterialTheme.typography.bodySmall)
  Spacer(Modifier.height(8.dp))
  Canvas(Modifier.fillMaxWidth().height(150.dp)) {
    val w = size.width
    val h = size.height
    val terrain = Path().apply {
      moveTo(0f, h * 0.30f)
      lineTo(w * 0.18f, h * 0.30f)
      lineTo(w * 0.30f, h * 0.78f)
      lineTo(w * 0.68f, h * 0.78f)
      lineTo(w * 0.82f, h * 0.38f)
      lineTo(w, h * 0.38f)
      lineTo(w, h)
      lineTo(0f, h)
      close()
    }
    drawPath(terrain, Color(0xFF2E5B34))
    drawPath(terrain, Color(0xFF1B7A3D), style = Stroke(width = 4f))
    // Flujo de aire: puntos que cruzan el valle.
    for (i in 0..14) {
      val x = w * (0.06f + i * 0.06f)
      val y = h * (0.42f + 0.10f * kotlin.math.sin(i * 0.9f))
      drawCircle(Color(0xFFBFE3C6), 5f, Offset(x, y))
    }
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 28f; isFakeBoldText = true }
    drawContext.canvas.nativeCanvas.drawText("BOCA DEL SUMIDERO", 8f, h * 0.22f, paint)
    drawContext.canvas.nativeCanvas.drawText("VALLE DE TUXTLA", w * 0.38f, h * 0.70f, paint)
    drawContext.canvas.nativeCanvas.drawText("MESETA DE COPOYA", w * 0.68f, h * 0.30f, paint)
  }
  Spacer(Modifier.height(8.dp))
  Text("1 · Canalización en el cañón", fontWeight = FontWeight.Bold)
  Text("Un paso estrecho entre paredes acelera el aire (efecto Venturi). Si el viento regional sopla del norte, dentro del cañón puede ser claramente más fuerte que en la ciudad.", style = MaterialTheme.typography.bodySmall)
  Text("2 · Flujo sobre el valle", fontWeight = FontWeight.Bold)
  Text("Sobre la mancha urbana el aire se frena con el suelo y los edificios: hay más viento a 60-120 m de altura que a ras de piso. Por eso el despegue en calma no garantiza un vuelo tranquilo.", style = MaterialTheme.typography.bodySmall)
  Text("3 · Ascenso y sotavento en Copoya", fontWeight = FontWeight.Bold)
  Text("El viento enfrenta la ladera, sube (bueno para sostener el dron) y justo detrás del filo se forman remolinos descendentes. Evita volar pegado a la cara protegida de la meseta.", style = MaterialTheme.typography.bodySmall)
}
