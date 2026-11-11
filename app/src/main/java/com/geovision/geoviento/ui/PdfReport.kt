package com.geovision.geoviento.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.geovision.geoviento.data.WindReport
import java.io.File
import java.io.FileOutputStream

// Informe técnico de vuelo en PDF, como en la demo web, pero generado en el
// teléfono con los datos en vivo (sin depender de internet ni de CDN).
// Se guarda en Descargas y devuelve su Uri para abrirlo.
object PdfReport {
  fun generate(context: Context, report: WindReport, lat: Double, lon: Double, altitudeM: Int, checklistOk: Boolean): Uri? {
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
      line("Geoviento Mx — Informe técnico de vuelo", title); y += 8f
      line("Dron: DJI Lito X1 (<249 g, límite de ráfaga 10.7 m/s)", head)
      line("Ubicación: Tuxtla Gutiérrez, %.4f, %.4f".format(lat, lon))
      line("Fecha y hora de los datos: " + (report.now.time ?: "—"))
      line("Fuente: " + report.source)
      y += 8f
      val n = report.now
      val level = flightLevel(n.gust10)
      line("Veredicto: " + level.label, head)
      line(level.advice)
      line("Viento 10 m: %.1f m/s (%.0f km/h), viene del %s (%.0f°)".format(n.speed10, kmh(n.speed10), compass(n.dir10), n.dir10))
      line("Ráfaga 10 m: %.1f m/s (%.0f km/h)".format(n.gust10, kmh(n.gust10)))
      line("Viento 120 m: " + (n.speed120?.let { "%.1f m/s".format(it) } ?: "—") + "  •  Ráfaga 120 m (est.): " + (n.gust120Est?.let { "%.1f m/s".format(it) } ?: "—"))
      line("Temperatura: " + (n.temp?.let { "%.1f °C".format(it) } ?: "—"))
      line("Altura elegida: $altitudeM m")
      line("En el Cañón del Sumidero la ráfaga local puede subir ~45%: %.1f m/s.".format(n.gust10 * 1.45))
      y += 8f
      line("Ráfaga hora por hora (próximas 24 h):", head)
      for (p in report.hourly.take(24)) {
        line("  " + p.time.substringAfter("T").substringBefore(":") + ":00  viento %.1f m/s  ráfaga %.1f m/s".format(p.speed10, p.gust10))
        if (y > 800f) break
      }
      y += 8f
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
}
