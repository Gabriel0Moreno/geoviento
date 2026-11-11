package com.geovision.geoviento.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geovision.geoviento.data.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WindViewModel(app: Application) : AndroidViewModel(app) {
  private val repo = WindRepository()
  private val context = getApplication<Application>()

  // La interfaz NUNCA abre vacía: al arrancar muestra datos guardados y, si no
  // hay, los datos de ejemplo de la demo (marcados como EJEMPLO). Los datos
  // en vivo los reemplazan en cuanto llegan.
  val report = MutableStateFlow<WindReport>(ReportCache.load(context) ?: exampleReport(16.75, -93.11))
  // Origen de lo que se muestra: "en vivo", "guardado" o "ejemplo".
  val origin = MutableStateFlow(if (ReportCache.load(context) != null) "guardado" else "ejemplo")
  val error = MutableStateFlow<String?>(null)
  val loading = MutableStateFlow(false)
  val diag = MutableStateFlow<List<DiagEntry>>(emptyList())
  val lastAttempt = MutableStateFlow<String?>(null)

  // Si algo inesperado escapa del try, termina en mensaje de error, nunca en cierre.
  private val handler = CoroutineExceptionHandler { _, e ->
    error.value = "No se pudo actualizar el viento (${e.message}). Revisa tu internet y toca Actualizar ahora."
    loading.value = false
  }

  fun refresh(lat: Double, lon: Double) {
    viewModelScope.launch(handler) {
      loading.value = true; error.value = null
      lastAttempt.value = "Último intento: %.4f, %.4f".format(lat, lon)
      try {
        val r = repo.now(lat, lon)
        report.value = r
        origin.value = "en vivo"
        ReportCache.save(context, r)
      } catch (e: Exception) {
        // El repositorio ya intentó Open-Meteo y met.no en paralelo; si llegamos aquí, fallaron los dos.
        error.value = "No se pudo actualizar el viento (${e.message}). Revisa tu internet y toca Actualizar ahora."
      }
      diag.value = repo.diag
      loading.value = false
    }
  }

  // Prueba directa de conexión a las dos fuentes, para el botón del diagnóstico.
  fun testConnection(lat: Double, lon: Double) {
    viewModelScope.launch(handler) {
      loading.value = true
      diag.value = repo.testConnection(lat, lon)
      lastAttempt.value = "Prueba de conexión: %.4f, %.4f".format(lat, lon)
      loading.value = false
    }
  }

  fun diagText(lat: Double, lon: Double): String {
    val sb = StringBuilder()
    sb.appendLine("Geoviento Mx — Diagnóstico")
    sb.appendLine("Ubicación: %.4f, %.4f".format(lat, lon))
    sb.appendLine("Origen de los datos que se ven: " + origin.value)
    sb.appendLine("Datos: " + (report.value.now.time ?: "—") + " • Fuente: " + report.value.source)
    sb.appendLine(lastAttempt.value ?: "Sin intentos todavía")
    if (diag.value.isEmpty()) sb.appendLine("Sin pruebas de conexión todavía. Toca 'Probar conexión'.")
    for (d in diag.value) {
      sb.appendLine((if (d.ok) "OK " else "FALLO ") + d.source + " — " + d.detail + " (" + d.ms + " ms)")
    }
    error.value?.let { sb.appendLine("Error: " + it) }
    return sb.toString()
  }
}
