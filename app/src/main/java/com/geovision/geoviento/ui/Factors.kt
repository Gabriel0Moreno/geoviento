package com.geovision.geoviento.ui

import androidx.compose.ui.graphics.Color
import com.geovision.geoviento.data.FlightPrefs
import com.geovision.geoviento.data.WindReport
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil

// Factores del diagnóstico "Motivos del estado de vuelo" (v1.3.0).
// Cada factor se evalúa con los datos en vivo y recibe un estado:
// OK (dentro del umbral), PENDING (pendiente / sin dato), WARN
// (precaución) o DANGER (peligro). El estado general es el peor de los
// factores que sí se pudieron evaluar; un "Pendiente" no bloquea el
// vuelo, solo avisa que ese factor no se pudo comprobar.
enum class FactorStatus { OK, PENDING, WARN, DANGER }

data class FlightFactor(val name: String, val detail: String, val status: FactorStatus)

fun factorChipLabel(status: FactorStatus): String = when (status) {
  FactorStatus.OK -> "Dentro del umbral"
  FactorStatus.PENDING -> "Pendiente"
  FactorStatus.WARN -> "Precaución"
  FactorStatus.DANGER -> "Peligro"
}

fun factorChipColor(status: FactorStatus): Color = when (status) {
  FactorStatus.OK -> Geo.Green
  FactorStatus.PENDING -> Geo.Muted
  FactorStatus.WARN -> Geo.Amber
  FactorStatus.DANGER -> Geo.Red
}

// Severidad para calcular el estado general (un Pendiente no bloquea).
private fun severity(s: FactorStatus): Int = when (s) {
  FactorStatus.OK -> 0
  FactorStatus.PENDING -> 0
  FactorStatus.WARN -> 1
  FactorStatus.DANGER -> 2
}

fun overallStatus(factors: List<FlightFactor>): FactorStatus {
  val worst = factors.maxOfOrNull { severity(it.status) } ?: 0
  return when (worst) {
    2 -> FactorStatus.DANGER
    1 -> FactorStatus.WARN
    else -> if (factors.any { it.status == FactorStatus.PENDING }) FactorStatus.PENDING else FactorStatus.OK
  }
}

fun overallTitle(status: FactorStatus): String = when (status) {
  FactorStatus.DANGER -> "No despegar"
  FactorStatus.WARN -> "Precaución"
  FactorStatus.PENDING -> "Revisa antes de volar"
  FactorStatus.OK -> "Se puede volar"
}

private fun windStatus(gust: Double, limit: Double): FactorStatus = when (flightLevel(gust, limit)) {
  FlightLevel.GREEN -> FactorStatus.OK
  FlightLevel.YELLOW -> FactorStatus.WARN
  FlightLevel.RED -> FactorStatus.DANGER
}

private fun dataAgeMinutes(report: WindReport): Long? {
  return try {
    val t = report.now.time ?: return null
    val dataTime = LocalDateTime.parse(t)
    val now = LocalDateTime.now(ZoneId.of("America/Mexico_City"))
    Duration.between(dataTime, now).toMinutes()
  } catch (_: Exception) {
    null
  }
}

// Los 10 factores, en el mismo orden de la pantalla de motivos.
// droneLimit es el límite nominal del dron elegido (10.7 m/s para los
// <249 g); el margen de las preferencias se aplica sobre ese límite.
fun computeFactors(
  report: WindReport,
  origin: String,
  prefs: FlightPrefs,
  gpsPlace: Boolean,
  droneLimit: Double = 10.7
): List<FlightFactor> {
  val n = report.now
  val effLimit = effectiveLimit(droneLimit, prefs.windMarginPct)
  val out = mutableListOf<FlightFactor>()

  // 1) Espacio aéreo: GeoViento no verifica el espacio aéreo en vivo
  // (igual que DroneCheck: "Próximamente"). Se deja en Pendiente y los
  // enlaces SENEAM/AFAC están en Más > Fuentes de datos.
  out.add(
    FlightFactor(
      "Espacio aéreo",
      "Sin verificación en esta versión — verifica el espacio aéreo antes de volar (enlaces en Fuentes de datos).",
      FactorStatus.PENDING
    )
  )

  // 2) Vigencia del clima: ejemplo/guardado/vivo y antigüedad del dato.
  val age = dataAgeMinutes(report)
  out.add(
    when (origin) {
      "ejemplo" -> FlightFactor(
        "Vigencia del clima",
        "Datos de EJEMPLO de la demo, no son el clima de ahora. No vueles con ellos.",
        FactorStatus.DANGER
      )
      "guardado" -> FlightFactor(
        "Vigencia del clima",
        "Datos GUARDADOS de la última vez que cargó; pueden estar viejos. La app intenta actualizar en vivo.",
        FactorStatus.WARN
      )
      else -> when {
        age == null -> FlightFactor("Vigencia del clima", "Datos en vivo, sin hora de consulta para verificar la antigüedad.", FactorStatus.PENDING)
        age <= 120 -> FlightFactor("Vigencia del clima", "Datos en vivo de hace $age min.", FactorStatus.OK)
        else -> FlightFactor("Vigencia del clima", "Datos en vivo de hace más de 2 h ($age min). Toca Actualizar ahora.", FactorStatus.WARN)
      }
    }
  )

  // 3) Viento modelado a 10 m (la ráfaga manda).
  out.add(
    FlightFactor(
      "Viento modelado a 10 m",
      "Ráfaga %.1f m/s, viento %.1f m/s (límite efectivo %.1f m/s).".format(n.gust10, n.speed10, effLimit),
      windStatus(n.gust10, effLimit)
    )
  )

  // 4) Viento a la altura prevista (120 m por defecto, o la de Preferencias).
  val alt = prefs.plannedAltitudeM
  val gustAlt = n.gust120Est
  out.add(
    if (gustAlt == null) FlightFactor(
      "Viento a la altura prevista ($alt m)",
      "Sin estimación de ráfaga a $alt m para esta hora.",
      FactorStatus.PENDING
    ) else FlightFactor(
      "Viento a la altura prevista ($alt m)",
      "Ráfaga estimada %.1f m/s a $alt m (límite efectivo %.1f m/s).".format(gustAlt, effLimit),
      windStatus(gustAlt, effLimit)
    )
  )

  // 5) Precipitación y tormentas (probabilidad de la hora actual).
  // Open-Meteo no da rayos en el pronóstico gratuito: se dice tal cual.
  val pp = report.surface?.precipProbNow
  out.add(
    when {
      pp == null -> FlightFactor("Precipitación y tormentas", "Sin probabilidad de lluvia para esta hora. Sin datos de rayos.", FactorStatus.PENDING)
      pp < 30 -> FlightFactor("Precipitación y tormentas", "Probabilidad de lluvia %.0f%%. Sin datos de rayos.".format(pp), FactorStatus.OK)
      pp < 60 -> FlightFactor("Precipitación y tormentas", "Probabilidad de lluvia %.0f%%: lleva protección y vigila el cielo.".format(pp), FactorStatus.WARN)
      else -> FlightFactor("Precipitación y tormentas", "Probabilidad de lluvia %.0f%%: no despegar con lluvia.".format(pp), FactorStatus.DANGER)
    }
  )

  // 6) Visibilidad: peligro si < 1 km.
  val vis = n.visibilityM
  out.add(
    when {
      vis == null -> FlightFactor("Visibilidad", "Sin dato de visibilidad para esta hora.", FactorStatus.PENDING)
      vis < 1000 -> FlightFactor("Visibilidad", "Visibilidad %s: menor de 1 km, no se ve el dron.".format(formatVisibility(vis)), FactorStatus.DANGER)
      vis < 5000 -> FlightFactor("Visibilidad", "Visibilidad %s: limitada, mantén el dron a la vista.".format(formatVisibility(vis)), FactorStatus.WARN)
      else -> FlightFactor("Visibilidad", "Visibilidad %s.".format(formatVisibility(vis)), FactorStatus.OK)
    }
  )

  // 7) Temperatura de operación (rango DJI 0–40 °C).
  val temp = n.temp
  out.add(
    when {
      temp == null -> FlightFactor("Temperatura de operación", "Sin temperatura actual.", FactorStatus.PENDING)
      temp in 0.0..40.0 -> FlightFactor("Temperatura de operación", "%.1f °C, dentro del rango de operación (0–40 °C).".format(temp), FactorStatus.OK)
      else -> FlightFactor("Temperatura de operación", "%.1f °C, fuera del rango de operación (0–40 °C).".format(temp), FactorStatus.DANGER)
    }
  )

  // 8) Ubicación del punto de despegue.
  out.add(
    if (gpsPlace) FlightFactor(
      "Ubicación del punto de despegue",
      "Ubicación por GPS: el pronóstico es del punto exacto.",
      FactorStatus.OK
    ) else FlightFactor(
      "Ubicación del punto de despegue",
      "Ubicación por nombre: el pronóstico es del lugar buscado, confirma el viento en sitio antes de despegar.",
      FactorStatus.PENDING
    )
  )

  // 9) Duración prevista: ¿la ventana aguanta los minutos del vuelo?
  val mins = prefs.flightMinutes
  val hoursNeeded = ceil(mins / 60.0).toInt().coerceAtLeast(1)
  val window = report.hourly.take(hoursNeeded)
  out.add(
    when {
      window.isEmpty() -> FlightFactor("Duración prevista ($mins min)", "Sin pronóstico por hora para revisar la ventana.", FactorStatus.PENDING)
      window.any { it.gust10 >= effLimit } -> FlightFactor(
        "Duración prevista ($mins min)",
        "Dentro de los próximos $mins min hay horas con ráfaga sobre el límite. Vuela más corto o espera.",
        FactorStatus.WARN
      )
      else -> FlightFactor(
        "Duración prevista ($mins min)",
        "La ventana aguanta los $mins min del vuelo.",
        FactorStatus.OK
      )
    }
  )

  // 10) Actividad geomagnética (Kp, NOAA SWPC). Tormenta desde Kp 5.
  val kp = report.kp?.value
  out.add(
    when {
      kp == null -> FlightFactor("Actividad geomagnética (Kp)", "Índice Kp no disponible (NOAA SWPC no respondió). No bloquea el vuelo.", FactorStatus.PENDING)
      kp < 4.0 -> FlightFactor("Actividad geomagnética (Kp)", "Kp %.2f, por debajo del umbral de tormenta (NOAA SWPC).".format(kp), FactorStatus.OK)
      kp < 5.0 -> FlightFactor("Actividad geomagnética (Kp)", "Kp %.2f, cerca del umbral de tormenta (Kp 5). Vigila el GPS.".format(kp), FactorStatus.WARN)
      else -> FlightFactor("Actividad geomagnética (Kp)", "Kp %.2f, tormenta geomagnética: el GPS puede fallar.".format(kp), FactorStatus.DANGER)
    }
  )

  return out
}
