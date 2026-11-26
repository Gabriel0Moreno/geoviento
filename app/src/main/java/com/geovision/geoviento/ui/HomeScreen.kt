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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.geovision.geoviento.data.DroneOption
import com.geovision.geoviento.data.FlightPrefs
import com.geovision.geoviento.data.PlaceResult
import com.geovision.geoviento.data.WindReport

// Condición resumida a partir de los datos modelados (misma lógica que
// la pestaña Pronóstico): niebla si la visibilidad es menor de 1 km.
fun conditionText(report: WindReport): String {
  val vis = report.now.visibilityM
  val pp = report.surface?.precipProbNow
  val cloud = report.surface?.cloudCover
  return when {
    vis != null && vis < 1000 -> "Niebla"
    pp != null && pp >= 50 -> "Lluvia probable"
    cloud != null && cloud >= 70 -> "Nublado"
    cloud != null && cloud >= 30 -> "Parcialmente nublado"
    cloud != null -> "Despejado"
    else -> "—"
  }
}

// Pantalla de inicio (v1.3.0): encabezado con lugar y condición,
// búsqueda "¿A dónde vamos a volar?", banner de origen, semáforo
// (tocable → Motivos), tarjetas 2×2, mejores horas, Kp, plan de vuelo
// y las secciones que ya tenía la app (superficie, brújula, calculadora,
// checklist, leyenda, error y actualización).
@Composable
fun HomeScreen(
  report: WindReport,
  origin: String,
  placeName: String,
  lat: Double,
  lon: Double,
  drone: DroneOption,
  limit: Double,
  prefs: FlightPrefs,
  query: String,
  onQueryChange: (String) -> Unit,
  onSearch: () -> Unit,
  onUseGps: () -> Unit,
  searchResults: List<PlaceResult>,
  onPickPlace: (PlaceResult) -> Unit,
  hasPermission: Boolean,
  loading: Boolean,
  error: String?,
  onRefresh: () -> Unit,
  onOpenReasons: () -> Unit,
  onOpenDrones: () -> Unit,
  onOpenPrefs: () -> Unit,
  checked: List<Boolean>,
  onToggleCheck: (Int) -> Unit,
  onAltitudeChange: (Int) -> Unit
) {
  val n = report.now
  val factors = computeFactors(report, origin, prefs, placeName == "Mi ubicación actual", drone.limit)
  val overall = overallStatus(factors)
  val worstFactor = factors.filter { it.status == FactorStatus.DANGER || it.status == FactorStatus.WARN }
    .maxByOrNull { if (it.status == FactorStatus.DANGER) 2 else 1 }

  // Encabezado: nombre, lugar y condición.
  Text("Geoviento Mx", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Geo.Text)
  Text("$placeName · ${conditionText(report)}", style = MaterialTheme.typography.bodyMedium, color = Geo.Muted)
  Text("${"%.3f".format(lat)}° N, ${"%.3f".format(-lon)}° O · ${report.surface?.elevation?.let { "%.0f".format(it) } ?: "—"} m s. n. m.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Text("Datos consultados: " + (report.now.time ?: "—") + "  •  Fuente: " + report.source, style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(12.dp))

  // Búsqueda: "¿A dónde vamos a volar?"
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    placeholder = { Text("¿A dónde vamos a volar?", color = Geo.Muted) },
    modifier = Modifier.fillMaxWidth(),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = { onSearch() }),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = Geo.Text, unfocusedTextColor = Geo.Text,
      focusedBorderColor = Geo.Accent, unfocusedBorderColor = Geo.Muted,
      cursorColor = Geo.Accent
    )
  )
  Spacer(Modifier.height(8.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = onSearch, colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent, contentColor = Geo.Text)) { Text("Buscar") }
    OutlinedButton(onClick = onUseGps) { Text("Mi ubicación") }
  }
  searchResults.forEach { p ->
    TextButton(onClick = { onPickPlace(p) }) {
      Text("${p.name} — ${p.detail}", color = Geo.Accent)
    }
  }
  if (!hasPermission) Text("Sin permiso de ubicación: se usan las coordenadas de Tuxtla.", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  if (loading) Text("Actualizando…", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
  Spacer(Modifier.height(12.dp))

  // Aviso de origen de los datos (siempre visible).
  when (origin) {
    "ejemplo" -> GeoCard(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(12.dp)) {
        Text("EJEMPLO — Datos de la demo de ayer", fontWeight = FontWeight.Bold, color = Geo.WarnText)
        Text("Esto NO es el viento de ahora. La interfaz se ve completa para que la revises mientras la app intenta traer datos en vivo. No vueles con estos datos.", style = MaterialTheme.typography.bodySmall, color = Geo.WarnText)
      }
    }
    "guardado" -> GeoCard(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(12.dp)) {
        Text("Datos GUARDADOS de la última vez que sí cargó", fontWeight = FontWeight.Bold, color = Geo.WarnText)
        Text("Pueden estar viejos. La app está intentando actualizar en vivo.", style = MaterialTheme.typography.bodySmall, color = Geo.WarnText)
      }
    }
    else -> Text("Datos EN VIVO", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Green)
  }
  Spacer(Modifier.height(12.dp))

  // Banner de estado (tocable → Motivos del estado de vuelo).
  GeoCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "Vuelo ahora: ${overallTitle(overall)}",
          style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = factorChipColor(overall), modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onOpenReasons) { Text("¿Por qué?", color = Geo.Accent) }
      }
      Text(
        when {
          worstFactor != null -> "${worstFactor.name}: ${worstFactor.detail}"
          overall == FactorStatus.PENDING -> "Hay factores pendientes de comprobar. Toca «¿Por qué?» para verlos todos."
          else -> "Todos los factores están dentro del umbral. Verifica el espacio aéreo antes de volar."
        },
        color = Geo.Text, style = MaterialTheme.typography.bodyMedium
      )
      Text("La ráfaga manda, no el viento medio.", color = Geo.Muted, style = MaterialTheme.typography.bodySmall)
    }
  }
  Spacer(Modifier.height(12.dp))

  // Tarjetas de datos 2×2.
  val maxPrecipToday = report.hourly.take(24).mapNotNull { it.precipProb }.maxOrNull()
  val maxPrecipHour = report.hourly.take(24).firstOrNull { it.precipProb == maxPrecipToday }?.time
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    HomeDataCard("VIENTO", formatSpeed(n.speed10, prefs.speedUnit), "a 10 m", Modifier.weight(1f))
    HomeDataCard("RÁFAGAS", formatSpeed(n.gust10, prefs.speedUnit), "a 10 m", Modifier.weight(1f))
  }
  Spacer(Modifier.height(8.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    HomeDataCard("VISIBILIDAD", formatVisibility(n.visibilityM), if (n.visibilityM != null && n.visibilityM < 1000) "menor de 1 km" else "ahora", Modifier.weight(1f))
    HomeDataCard(
      "PROB. DE LLUVIA",
      report.surface?.precipProbNow?.let { "%.0f%%".format(it) } ?: "—",
      if (maxPrecipToday != null && maxPrecipHour != null) "máx. %.0f%% a las %s".format(maxPrecipToday, hourLabel(maxPrecipHour)) else "ahora",
      Modifier.weight(1f)
    )
  }
  Spacer(Modifier.height(12.dp))

  // Mejores horas de hoy.
  BestHoursStrip(report, limit, prefs)
  Spacer(Modifier.height(12.dp))

  // Índice Kp.
  GeoCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Índice Kp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Geo.Text, modifier = Modifier.weight(1f))
        Text(report.kp?.value?.let { "%.2f".format(it) } ?: "No disponible", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Geo.Text)
      }
      Text("NOAA SWPC · Indicador global", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      Text(
        run {
          val kpVal = report.kp?.value
          when {
            kpVal == null -> "Sin dato — no bloquea el vuelo."
            kpVal < 4.0 -> "Por debajo del umbral de tormenta."
            kpVal < 5.0 -> "Cerca del umbral de tormenta (Kp 5). Vigila el GPS."
            else -> "Tormenta geomagnética: el GPS puede fallar."
          }
        },
        style = MaterialTheme.typography.bodySmall, color = Geo.Muted
      )
    }
  }
  Spacer(Modifier.height(12.dp))

  // Plan de vuelo.
  GeoCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
      Text("Plan de vuelo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Geo.Text)
      Text("${drone.name} · límite ${formatSpeed(drone.limit, prefs.speedUnit)} de ráfaga", style = MaterialTheme.typography.bodyMedium, color = Geo.Text)
      Text("Duración prevista: ${prefs.flightMinutes} min · ${prefs.operation.label} · Altura: ${prefs.plannedAltitudeM} m", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      Text("Espacio aéreo · Próximamente", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onOpenDrones, colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent, contentColor = Geo.Text)) { Text("Cambiar dron") }
        OutlinedButton(onClick = onOpenPrefs) { Text("Preferencias") }
      }
    }
  }
  Spacer(Modifier.height(12.dp))

  AfternoonWarning(report, limit)
  Spacer(Modifier.height(12.dp))
  SurfaceConditions(report)
  Spacer(Modifier.height(12.dp))

  // Brújula: hacia dónde va el aire.
  GeoCard(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      WindCompass(n.dir10)
      Column {
        Text("El aire va hacia el ${compass((n.dir10 + 180) % 360)}", fontWeight = FontWeight.Bold, color = Geo.Text)
        Text("Viene del ${compass(n.dir10)} (${"%.0f".format(n.dir10)}°)", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
        Text("Viento ${formatSpeed(n.speed10, prefs.speedUnit)} · Ráfaga ${formatSpeed(n.gust10, prefs.speedUnit)}", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      }
    }
  }
  Spacer(Modifier.height(12.dp))

  // Viento a 10 / 80 / 120 m (líneas que tenía la app).
  GeoCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
      Text("Viento por altura", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Geo.Text)
      Text("10 m: ${formatSpeed(n.speed10, prefs.speedUnit)} (viene del ${compass(n.dir10)})", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      Text("80 m: " + (n.speed80?.let { formatSpeed(it, prefs.speedUnit) + " (viene del ${compass(n.dir80 ?: 0.0)})" } ?: "—"), style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      Text("120 m: " + (n.speed120?.let { formatSpeed(it, prefs.speedUnit) + " (viene del ${compass(n.dir120 ?: 0.0)})" } ?: "—"), style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
      n.gust120Est?.let { Text("Ráfaga estimada a 120 m: ${formatSpeed(it, prefs.speedUnit)} (est.)", style = MaterialTheme.typography.bodySmall, color = Geo.Muted) }
    }
  }
  Spacer(Modifier.height(12.dp))

  AltitudeCalculator(report, limit, onAltitudeChange, prefs.speedUnit)
  Spacer(Modifier.height(12.dp))
  PreflightChecklist(report, limit, checked, onToggleCheck, prefs.speedUnit)
  Spacer(Modifier.height(12.dp))
  SemaphoreLegend()
  Spacer(Modifier.height(12.dp))

  error?.let {
    Text(it, color = Geo.ErrorText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
  }
  Button(onClick = onRefresh, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Geo.Accent, contentColor = Geo.Text)) {
    Text(if (loading) "Actualizando…" else "Actualizar ahora")
  }
  Spacer(Modifier.height(16.dp))
  Text(
    "Geoviento Mx v${com.geovision.geoviento.BuildConfig.VERSION_NAME} · Se actualiza solo cada 15 min.",
    style = MaterialTheme.typography.bodySmall, color = Geo.Muted
  )
}

@Composable
private fun HomeDataCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
  GeoCard(modifier) {
    Column(Modifier.padding(12.dp)) {
      Text(label, color = Geo.Muted, style = MaterialTheme.typography.labelSmall)
      Text(value, color = Geo.Text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(sub, color = Geo.Muted, style = MaterialTheme.typography.bodySmall)
    }
  }
}

// Tira de mejores horas de hoy: un punto por hora con el color del
// semáforo, y el texto de la mejor ventana (o que no hay ventana buena).
@Composable
private fun BestHoursStrip(report: WindReport, limit: Double, prefs: FlightPrefs) {
  val points = report.hourly.take(24)
  if (points.isEmpty()) return
  // Racha verde más larga de hoy.
  var bestStart = -1; var bestLen = 0; var curStart = -1; var curLen = 0
  points.forEachIndexed { i, p ->
    if (flightLevel(p.gust10, limit) == FlightLevel.GREEN) {
      if (curStart < 0) { curStart = i; curLen = 1 } else curLen++
      if (curLen > bestLen) { bestLen = curLen; bestStart = curStart }
    } else { curStart = -1; curLen = 0 }
  }
  Column {
    SectionTitle("Mejores horas de hoy")
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      points.forEach { p ->
        val lvl = flightLevel(p.gust10, limit)
        GeoCard {
          Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hourLabel(p.time), style = MaterialTheme.typography.bodySmall, color = Geo.Text)
            Text("●", color = levelTextColor(lvl), style = MaterialTheme.typography.bodySmall)
            Text(formatSpeed(p.gust10, prefs.speedUnit), style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
          }
        }
      }
    }
    Spacer(Modifier.height(4.dp))
    Text(
      if (bestStart >= 0) "Mejor ventana: ${hourLabel(points[bestStart].time)}–${hourLabel(points[(bestStart + bestLen - 1).coerceAtMost(points.size - 1)].time)} (${bestLen} h en verde)."
      else "Sin ventana favorable confirmada.",
      style = MaterialTheme.typography.bodySmall, color = Geo.Muted
    )
  }
}
