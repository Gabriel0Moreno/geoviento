package com.geovision.geoviento.ui
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geovision.geoviento.data.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
class WindViewModel : ViewModel() {
  private val repo = WindRepository()
  val report = MutableStateFlow<WindReport?>(null)
  val error = MutableStateFlow<String?>(null)
  val loading = MutableStateFlow(false)
  // Si algo inesperado escapa del try, termina en mensaje de error, nunca en cierre.
  private val handler = CoroutineExceptionHandler { _, e ->
    error.value = "No se pudo actualizar el viento (${e.message}). Revisa tu internet y toca Actualizar ahora."
    loading.value = false
  }
  fun refresh(lat: Double, lon: Double) {
    viewModelScope.launch(handler) {
      loading.value = true; error.value = null
      try {
        report.value = repo.now(lat, lon)
      } catch (e: Exception) {
        // El repositorio ya intentó Open-Meteo y luego met.no; si llegamos aquí, fallaron los dos.
        error.value = "No se pudo actualizar el viento (${e.message}). Revisa tu internet y toca Actualizar ahora."
      }
      loading.value = false
    }
  }
}
