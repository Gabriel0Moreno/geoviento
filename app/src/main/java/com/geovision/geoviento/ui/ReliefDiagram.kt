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
import com.geovision.geoviento.data.TerrainProfile

// Cómo se mueve el aire sobre el relieve.
//
// v1.2.3: el corte ya NO es el esquema fijo de Tuxtla. Es el perfil REAL
// del lugar elegido: 21 alturas de la API de elevación de Open-Meteo sobre
// un corte de 30 km en el eje del viento (de donde viene hacia donde va el
// aire). En Tuxtla se conservan además las 3 explicaciones del valle y el
// cañón, que son conocimiento local verificado; el esquema viejo solo se
// usa si el perfil real no cargó y el lugar es Tuxtla.
@Composable
fun ReliefSection(terrain: TerrainProfile?, terrainLoading: Boolean, isTuxtla: Boolean) {
  Text("Cómo se mueve el aire hoy sobre el relieve", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
  if (terrain != null) {
    val from = (terrain.axisDirTo + 180) % 360
    Text("CORTE REAL · VIENTO ${compass(from)} → ${compass(terrain.axisDirTo)} · 30 KM", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    Text("Corte del terreno en la dirección del viento, de donde viene el aire hacia donde va. Las alturas son reales (Open-Meteo, elevación del terreno), no un dibujo: es el relieve que el aire tiene que cruzar sobre este lugar.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    RealProfileCanvas(terrain)
    Spacer(Modifier.height(8.dp))
    Text("El lugar está a %.0f m. %s: el terreno va de %.0f m a %.0f m (desnivel %.0f m en 30 km).".format(terrain.elevAtLoc, terrain.kind, terrain.minElev, terrain.maxElev, terrain.range), style = MaterialTheme.typography.bodySmall)
    if (terrain.maxClimb >= 150) {
      Text("La subida más fuerte del corte es de %.0f m en 1.5 km: el aire se ve obligado a subir ahí y detrás del filo se arremolina. No vueles pegado a esa ladera.".format(terrain.maxClimb), style = MaterialTheme.typography.bodySmall)
    }
    if (isTuxtla) {
      Spacer(Modifier.height(8.dp))
      TuxtlaTexts()
    } else {
      Spacer(Modifier.height(8.dp))
      Text("Si tu ruta cruza el punto más alto del corte (%.0f m), súbelo con margen: en los pasos estrechos el aire se acelera y detrás de los filos cae y se arremolina.".format(terrain.maxElev), style = MaterialTheme.typography.bodySmall)
    }
  } else if (isTuxtla) {
    Text("ESQUEMA EDUCATIVO · VIENTO N-O", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    Text("Corte esquemático del valle, de norte a sur: el flujo dominante entra por la boca del Cañón del Sumidero, cruza la ciudad y choca con la Meseta de Copoya. No es un mapa ni una medición: es el patrón que debes anticipar al planear la ruta.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    TuxtlaSchematic()
    Spacer(Modifier.height(8.dp))
    TuxtlaTexts()
  } else {
    Text(if (terrainLoading) "Cargando el relieve de este lugar…" else "No se pudo cargar el relieve de este lugar (revisa tu internet y toca Actualizar ahora).", style = MaterialTheme.typography.bodySmall)
  }
}

// Perfil real del terreno: polígono verde con la altura de cada muestra,
// un punto ámbar en el lugar elegido y las etiquetas de mínimo, máximo y
// distancia. El eje X es el corte (-15 a +15 km); el eje Y, la altura.
@Composable
private fun RealProfileCanvas(terrain: TerrainProfile) {
  Canvas(Modifier.fillMaxWidth().height(180.dp)) {
    val w = size.width
    val h = size.height
    val min = terrain.minElev
    val span = terrain.range.coerceAtLeast(1.0)
    fun x(km: Double) = (w * ((km + 15.0) / 30.0)).toFloat()
    fun y(e: Double) = (h * 0.92f - (h * 0.72f * ((e - min) / span)).toFloat())
    val path = Path().apply {
      moveTo(x(terrain.points.first().km), y(terrain.points.first().elevM))
      terrain.points.forEach { lineTo(x(it.km), y(it.elevM)) }
      lineTo(x(15.0), h)
      lineTo(x(-15.0), h)
      close()
    }
    drawPath(path, Color(0xFF2E5B34))
    drawPath(path, Color(0xFF1B7A3D), style = Stroke(width = 4f))
    // Puntos de aire sobre el perfil, en la dirección del flujo.
    for (i in 0..11) {
      val p = terrain.points[(i * 20 / 11).coerceIn(0, 20)]
      drawCircle(Color(0xFFBFE3C6), 5f, Offset(x(p.km), y(p.elevM) - 34f))
    }
    // Marca del lugar elegido.
    val cx = x(0.0)
    val cy = y(terrain.elevAtLoc)
    drawCircle(Color(0xFFFFC107), 11f, Offset(cx, cy))
    drawCircle(Color.White, 4.5f, Offset(cx, cy))
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 26f; isFakeBoldText = true }
    val dark = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 24f; isFakeBoldText = true }
    val maxP = terrain.points.maxByOrNull { it.elevM }
    val minP = terrain.points.minByOrNull { it.elevM }
    if (maxP != null) drawContext.canvas.nativeCanvas.drawText("MÁX %.0f m".format(maxP.elevM), x(maxP.km).coerceIn(0f, w - 130f), (y(maxP.elevM) - 12f).coerceAtLeast(26f), paint)
    if (minP != null) drawContext.canvas.nativeCanvas.drawText("MÍN %.0f m".format(minP.elevM), x(minP.km).coerceIn(0f, w - 130f), (y(minP.elevM) + 34f).coerceAtMost(h - 8f), paint)
    drawContext.canvas.nativeCanvas.drawText("AQUÍ · %.0f m".format(terrain.elevAtLoc), (cx + 16f).coerceAtMost(w - 150f), (cy - 16f).coerceAtLeast(26f), paint)
    drawContext.canvas.nativeCanvas.drawText("−15 km", 8f, h - 6f, dark)
    drawContext.canvas.nativeCanvas.drawText("+15 km", w - 78f, h - 6f, dark)
  }
}

// Las 3 explicaciones del valle de Tuxtla (conocimiento local verificado).
@Composable
private fun TuxtlaTexts() {
  Text("1 · Canalización en el cañón", fontWeight = FontWeight.Bold)
  Text("Un paso estrecho entre paredes acelera el aire (efecto Venturi). Si el viento regional sopla del norte, dentro del cañón puede ser claramente más fuerte que en la ciudad.", style = MaterialTheme.typography.bodySmall)
  Text("2 · Flujo sobre el valle", fontWeight = FontWeight.Bold)
  Text("Sobre la mancha urbana el aire se frena con el suelo y los edificios: hay más viento a 60-120 m de altura que a ras de piso. Por eso el despegue en calma no garantiza un vuelo tranquilo.", style = MaterialTheme.typography.bodySmall)
  Text("3 · Ascenso y sotavento en Copoya", fontWeight = FontWeight.Bold)
  Text("El viento enfrenta la ladera, sube (bueno para sostener el dron) y justo detrás del filo se forman remolinos descendentes. Evita volar pegado a la cara protegida de la meseta.", style = MaterialTheme.typography.bodySmall)
}

// Esquema viejo del valle (solo si el perfil real no cargó y es Tuxtla).
@Composable
private fun TuxtlaSchematic() {
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
}
