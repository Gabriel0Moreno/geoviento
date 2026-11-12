package com.geovision.geoviento.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geovision.geoviento.data.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class WindViewModel(app: Application) : AndroidViewModel(app) {
  private val repo = WindRepository()
  private val context = getApplication<Application>()

  // La interfaz NUNCA abre vacía: al arrancar muestra datos guardados y, si no
  // hay, los datos de ejemplo de la demo (marcados como EJEMPLO). Los datos
  // en vivo los reemplazan en cuanto llegan.
  private val initialReport: WindReport? = ReportCache.load(context)
  val report = MutableStateFlow<WindReport>(initialReport ?: exampleReport(16.75, -93.11))
  // Origen de lo que se muestra: "en vivo", "guardado" o "ejemplo".
  val origin = MutableStateFlow(if (initialReport != null) "guardado" else "ejemplo")
  val error = MutableStateFlow<String?>(null)
  val loading = MutableStateFlow(false)
  val diag = MutableStateFlow<List<DiagEntry>>(emptyList())
  val lastAttempt = MutableStateFlow<String?>(null)

  // Dron elegido (semáforo e informe PDF). Por defecto el DJI Lito X1.
  val drone = MutableStateFlow(DRONES[0])

  // Sección del informe PDF: día consultado, su reporte y la referencia del archivo.
  val dayReport = MutableStateFlow<DayReport?>(null)
  val dayLoading = MutableStateFlow(false)
  val dayError = MutableStateFlow<String?>(null)
  // Referencia del archivo: [mín, máx de ráfaga diaria 7 días, dir mín, dir máx].
  val weekRef = MutableStateFlow<List<Double>>(emptyList())
  val places = MutableStateFlow<List<PlaceResult>>(emptyList())
  // Búsqueda de lugar para la UBICACIÓN PRINCIPAL (arriba): lista aparte
  // de la del informe PDF para no mezclar los resultados.
  val mainPlaces = MutableStateFlow<List<PlaceResult>>(emptyList())

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

  // Consulta los datos de un día para el informe PDF (hoy = pronóstico,
  // días pasados = archivo ERA5) y la referencia de los últimos 7 días.
  fun consultDay(lat: Double, lon: Double, date: LocalDate) {
    viewModelScope.launch(handler) {
      dayLoading.value = true; dayError.value = null
      try {
        dayReport.value = repo.dayReport(lat, lon, date)
        weekRef.value = repo.weekReference(lat, lon)
      } catch (e: Exception) {
        dayError.value = "No se pudo consultar ese día (${e.message})."
      }
      dayLoading.value = false
    }
  }

  fun searchPlaces(query: String) {
    viewModelScope.launch(handler) {
      if (query.length < 3) { places.value = emptyList(); return@launch }
      places.value = repo.searchPlace(query)
    }
  }

  fun searchMainPlaces(query: String) {
    viewModelScope.launch(handler) {
      if (query.length < 3) { mainPlaces.value = emptyList(); return@launch }
      mainPlaces.value = repo.searchPlace(query)
    }
  }

  fun today(): LocalDate = LocalDate.now(ZoneId.of("America/Mexico_City"))

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
