package com.geovision.geoviento.ui
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geovision.geoviento.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
class WindViewModel : ViewModel() {
  private val repo = WindRepository()
  val report = MutableStateFlow<WindReport?>(null)
  val error = MutableStateFlow<String?>(null)
  val loading = MutableStateFlow(false)
  fun refresh(lat: Double, lon: Double) {
    viewModelScope.launch { loading.value = true; error.value = null
      try { report.value = repo.now(lat, lon) } catch (e: Exception) { error.value = "No se pudo actualizar con Open-Meteo (${e.message}). Revisa internet o espera unos minutos; el límite diario por IP a veces se agota." }
      loading.value = false
    }
  }
}
