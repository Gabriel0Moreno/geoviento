package com.geovision.geoviento.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.geovision.geoviento.data.DayReport
import com.geovision.geoviento.data.DroneOption
import com.geovision.geoviento.data.WindReport
import java.io.File
import java.io.FileOutputStream

// Informe técnico de vuelo en PDF, como en la demo web, pero generado en el
// teléfono con los datos consultados (pronóstico para hoy, archivo ERA5 para
// días pasados). Se guarda en Descargas y devuelve su Uri para abrirlo.
object PdfReport {
  fun generate(
    context: Context,
    report: WindReport,
    day: DayReport?,
    lat: Double, lon: Double,
    placeName: String,
    hour: String,
    altitudeM: Int,
    drone: DroneOption,
    weekRef: List<Double>,
    checklistOk: Boolean
  ): Uri? {
    return try {
      val doc = PdfDocument()
      val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
      val page = doc.startPage(pageInfo)
      val canvas = page.canvas
      val title = Paint().apply { textSize = 22f; isFakeBoldText = true }
      val head = Paint().apply { textSize = 15f; isFakeBoldText = true }
      val body = Paint().apply { textSize = 12f }
      var y = 60f
      fun line(text: String, paint: Paint = body) { canvas.drawText(text, 40f, y, paint); y += 22f }
      val hourly = day?.hourly ?: report.hourly
      val chosen = hourly.firstOrNull { it.time.substringAfter("T").startsWith(hour) } ?: hourly.firstOrNull()
      val factor = logWindFactor(altitudeM.toDouble())
      line("Geoviento Mx — Informe técnico de vuelo", title); y += 8f
      line("Dron: ${drone.name} (límite de ráfaga ${drone.limit} m/s)", head)
      line("Ubicación: $placeName, %.4f, %.4f".format(lat, lon))
      line("Día del informe: " + (day?.date ?: "hoy") + "  •  Hora del vuelo: $hour  •  Altura planeada: $altitudeM m")
      line("Fuente: " + (day?.source ?: report.source))
      y += 8f
      if (chosen != null) {
        val level = flightLevel(chosen.gust10, drone.limit)
        line("Veredicto a las $hour: " + level.label, head)
        line(level.advice)
        line("Viento 10 m: %.1f m/s (%.0f km/h), viene del %s (%.0f°)".format(chosen.speed10, kmh(chosen.speed10), compass(chosen.dir10), chosen.dir10))
        line("Ráfaga 10 m: %.1f m/s (%.0f km/h)".format(chosen.gust10, kmh(chosen.gust10)))
        line("El aire se mueve hacia el %s.".format(compass((chosen.dir10 + 180) % 360)))
        line("Viento estimado a $altitudeM m: %.1f m/s (factor logarítmico ×%.2f)".format(chosen.speed10 * factor, factor))
        line("Ráfaga estimada a $altitudeM m: %.1f m/s".format(chosen.gust10 * factor))
        // La nota del cañón solo aplica cerca de Tuxtla.
        if (lat in 15.75..17.75 && lon in -94.11..-92.11) {
          line("En el Cañón del Sumidero la ráfaga local puede subir ~45%%: %.1f m/s.".format(chosen.gust10 * 1.45))
        }
        line("Temperatura a esa hora: " + (chosen.temp10?.let { "%.1f °C".format(it) } ?: "—"))
      }
      y += 8f
      line("Tabla hora por hora:", head)
      for (p in hourly.take(24)) {
        line("  " + p.time.substringAfter("T").substringBefore(":") + ":00  viento %.1f m/s  ráfaga %.1f m/s  dir %s".format(p.speed10, p.gust10, compass(p.dir10)))
        if (y > 790f) break
      }
      y += 8f
      if (weekRef.size >= 4) {
        line("Referencia rápida del archivo: en los últimos 7 días la ráfaga máxima diaria fue de %.1f a %.1f m/s, con viento dominante del %.0f° al %.0f°.".format(weekRef[0], weekRef[1], weekRef[2], weekRef[3]), head)
        y += 8f
      }
      line("Checklist antes de despegar: " + if (checklistOk) "COMPLETO" else "INCOMPLETO (revisa batería, regreso a casa, zona despejada y clima)", head)
      line("Generado por Geoviento Mx, GeoVisión Aérea Chiapas.")
      doc.finishPage(page)
      val name = "geoviento-informe-vuelo.pdf"
      val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
          put(MediaStore.Downloads.DISPLAY_NAME, name)
          put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
          put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val u = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (u != null) context.contentResolver.openOutputStream(u)?.use { doc.writeTo(it) }
        u
      } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(dir, name)
        FileOutputStream(file).use { doc.writeTo(it) }
        Uri.fromFile(file)
      }
      doc.close()
      uri
    } catch (_: Exception) {
      null
    }
  }

  // Compatibilidad: informe rápido con los datos en vivo actuales.
  fun generate(context: Context, report: WindReport, lat: Double, lon: Double, altitudeM: Int, checklistOk: Boolean): Uri? {
    val hour = report.now.time?.substringAfter("T")?.substringBefore(":") ?: "08:00"
    return generate(context, report, null, lat, lon, "Tuxtla Gutiérrez", hour, altitudeM, com.geovision.geoviento.data.DRONES[0], emptyList(), checklistOk)
  }
}
