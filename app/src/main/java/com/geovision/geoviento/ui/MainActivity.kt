package com.geovision.geoviento.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.geovision.geoviento.BuildConfig
import com.geovision.geoviento.data.WindReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// Pestañas inferiores (v1.3.0): Inicio, Mapa, Pronóstico, Más.
private enum class Tab(val label: String, val kind: NavKind) {
  HOME("Inicio", NavKind.HOME),
  MAP("Mapa", NavKind.MAP),
  FORECAST("Pronóstico", NavKind.CHART),
  MORE("Más", NavKind.MORE)
}

// Subpantallas de Más y del banner de inicio (sin pestaña inferior).
private sealed class SubScreen {
  object Reasons : SubScreen()
  object Drones : SubScreen()
  object Prefs : SubScreen()
  object Pdf : SubScreen()
  object Appearance : SubScreen()
  object Sources : SubScreen()
  object Safety : SubScreen()
  object Privacy : SubScreen()
  object Support : SubScreen()
  object About : SubScreen()
}

private fun MoreDestination.toSubScreen(): SubScreen = when (this) {
  MoreDestination.DRONES -> SubScreen.Drones
  MoreDestination.PREFS -> SubScreen.Prefs
  MoreDestination.PDF -> SubScreen.Pdf
  MoreDestination.APPEARANCE -> SubScreen.Appearance
  MoreDestination.SOURCES -> SubScreen.Sources
  MoreDestination.SAFETY -> SubScreen.Safety
  MoreDestination.PRIVACY -> SubScreen.Privacy
  MoreDestination.SUPPORT -> SubScreen.Support
  MoreDestination.ABOUT -> SubScreen.About
}

class MainActivity : ComponentActivity() {
  // Tuxtla por defecto si aún no hay GPS: 16.75, -93.11
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // PERMANENTE (Gabo, 2026-11-11): si la app se cierra de forma
    // inesperada, el error exacto queda guardado en un txt, SIEMPRE. No
    // quitar en versiones futuras. Guarda en el almacenamiento interno de
    // la app (ahí siempre se puede escribir, con nombre por fecha para no
    // pisar el anterior) y una copia en Descargas. OJO: esto solo atrapa
    // errores de la app en Java/Kotlin; un fallo NATIVO (como el del mapa
    // MapLibre, que nunca dejó archivo) no lo puede atrapar nadie desde
    // aquí, por eso el mapa ya no usa código nativo.
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
      try {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "geoviento-crash-$stamp.txt"
        val text = buildString {
          appendLine("Geoviento Mx — cierre inesperado")
          appendLine("Fecha: $stamp")
          appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
          appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
          appendLine("Teléfono: ${Build.MANUFACTURER} ${Build.MODEL}")
          appendLine("Hilo: ${thread.name}")
          appendLine("Último estado: ${CrashLog.lastState}")
          appendLine("Error:")
          appendLine(Log.getStackTraceString(e))
        }
        val dir = File(filesDir, "crashes").apply { mkdirs() }
        File(dir, fileName).writeText(text)
        try {
          val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
          }
          val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
          uri?.let { contentResolver.openOutputStream(it)?.use { os -> os.write(text.toByteArray()) } }
        } catch (_: Exception) { /* la copia interna ya quedó guardada */ }
      } catch (_: Exception) { /* si no se puede guardar, el cierre sigue su curso */ }
      previousHandler?.uncaughtException(thread, e)
    }
    val fused = LocationServices.getFusedLocationProviderClient(this)
    var onPermission: ((Boolean) -> Unit)? = null
    val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      onPermission?.invoke(granted)
    }
    setContent {
      // Fábrica explícita: el ViewModel necesita la Application y la fábrica
      // por defecto no siempre lo crea bien; sin esto la app se cerraba
      // apenas al abrir (pantalla del logo y de regreso al cajón).
      val vm: WindViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          @Suppress("UNCHECKED_CAST")
          return WindViewModel(application) as T
        }
      })
      val rep by vm.report.collectAsState()
      val origin by vm.origin.collectAsState()
      val err by vm.error.collectAsState()
      val loading by vm.loading.collectAsState()
      val diag by vm.diag.collectAsState()
      val lastAttempt by vm.lastAttempt.collectAsState()
      val drone by vm.drone.collectAsState()
      val prefs by vm.prefs.collectAsState()
      val terrain by vm.terrain.collectAsState()
      val terrainLoading by vm.terrainLoading.collectAsState()
      val radarFrames by vm.radarFrames.collectAsState()
      val windGrid by vm.windGrid.collectAsState()
      val gridFailed by vm.windGridFailed.collectAsState()
      val context = LocalContext.current
      val scope = rememberCoroutineScope()
      val focusManager = LocalFocusManager.current
      var lat by remember { mutableStateOf(16.75) }
      var lon by remember { mutableStateOf(-93.11) }
      // Nombre del lugar principal (arriba): cambia con la búsqueda o el GPS.
      var placeName by remember { mutableStateOf("Tuxtla Gutiérrez, Chiapas") }
      var mainQuery by remember { mutableStateOf("") }
      val mainPlaces by vm.mainPlaces.collectAsState()
      var hasPermission by remember { mutableStateOf(false) }
      // Navegación (v1.3.0): 4 pestañas + subpantallas.
      var tab by remember { mutableStateOf(Tab.HOME) }
      var sub by remember { mutableStateOf<SubScreen?>(null) }

      fun useGps() {
        if (hasPermission) {
          loadGps(fused, { la, lo -> lat = la; lon = lo; placeName = "Mi ubicación actual" }, { vm.refresh(lat, lon) })
        } else {
          perm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
      }

      // Permiso de ubicación después del primer frame (no en onCreate),
      // y la primera carga con la ubicación por defecto de Tuxtla.
      // Se quita el foco (y el teclado) por si el campo de búsqueda del
      // informe lo agarró solo al abrir: el teclado no debe salir al entrar.
      LaunchedEffect(Unit) {
        focusManager.clearFocus()
        onPermission = { granted ->
          hasPermission = granted
          if (granted) useGps()
        }
        perm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        vm.refresh(lat, lon)
      }
      // Actualización automática cada 15 min con la ubicación actual (un solo ciclo).
      LaunchedEffect(Unit) {
        while (true) { delay(15 * 60 * 1000L); vm.refresh(lat, lon) }
      }

      // Estado: checklist pre-vuelo (6 puntos como máximo).
      var checklist by remember { mutableStateOf(List(6) { false }) }

      // Último estado conocido, para el txt de cierre (ver onCreate).
      LaunchedEffect(rep, origin) {
        CrashLog.lastState = "origen=$origin, fuente=${rep.source}, hora=${rep.now.time}, ráfaga=${rep.now.gust10} m/s, lugar=$lat,$lon ($placeName)"
      }

      // El perfil del relieve sigue al lugar y al viento (v1.2.3): se pide
      // cuando llegan datos nuevos del viento (que es cuando cambia el
      // lugar o gira el viento).
      LaunchedEffect(rep) {
        vm.refreshTerrain(lat, lon, rep.now.dir10)
      }

      // La interfaz SIEMPRE se ve: si aún no hay datos en vivo, se muestra el
      // ejemplo de la demo (marcado) o los datos guardados. Nunca pantalla blanca.
      val report: WindReport = rep
      // Límite efectivo: el del dron elegido con el margen de seguridad de
      // las Preferencias (0% por defecto = el límite tal cual, como siempre).
      val limit = effectiveLimit(drone.limit, prefs.windMarginPct)
      // Las zonas y el cañón son de Tuxtla: solo se muestran cuando el
      // lugar principal está cerca de Tuxtla. El relieve ahora es real
      // para cualquier lugar (v1.2.3), así que ya no va en este filtro.
      val isTuxtla = abs(lat - 16.75) < 0.5 && abs(lon - (-93.11)) < 0.5

      // Atrás del sistema vuelve de la subpantalla a su pestaña.
      BackHandler(enabled = sub != null) { sub = null }

      GeovientoTheme {
        Scaffold(
          containerColor = Geo.Bg,
          bottomBar = {
            if (sub == null) {
              NavigationBar(containerColor = Geo.Card) {
                Tab.values().forEach { t ->
                  NavigationBarItem(
                    selected = tab == t,
                    onClick = { tab = t },
                    icon = { NavGlyph(t.kind, tab == t) },
                    label = { Text(t.label, color = if (tab == t) Geo.Accent else Geo.Muted) },
                    colors = NavigationBarItemDefaults.colors(
                      indicatorColor = Geo.Card2,
                      selectedIconColor = Geo.Accent,
                      unselectedIconColor = Geo.Muted
                    )
                  )
                }
              }
            }
          }
        ) { padding ->
          // Tocar en cualquier parte vacía esconde el teclado.
          Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
              .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusManager.clearFocus() }
          ) {
            when (val currentSub = sub) {
              null -> when (tab) {
                Tab.HOME -> HomeScreen(
                  report = report,
                  origin = origin,
                  placeName = placeName,
                  lat = lat,
                  lon = lon,
                  drone = drone,
                  limit = limit,
                  prefs = prefs,
                  query = mainQuery,
                  onQueryChange = { mainQuery = it },
                  onSearch = { vm.searchMainPlaces(mainQuery); focusManager.clearFocus() },
                  onUseGps = { useGps() },
                  searchResults = mainPlaces,
                  onPickPlace = { p ->
                    placeName = if (p.detail.isBlank()) p.name else "${p.name}, ${p.detail}"
                    lat = p.lat; lon = p.lon
                    vm.mainPlaces.value = emptyList()
                    vm.refresh(lat, lon)
                    focusManager.clearFocus()
                  },
                  hasPermission = hasPermission,
                  loading = loading,
                  error = err,
                  onRefresh = { vm.refresh(lat, lon) },
                  onOpenReasons = { sub = SubScreen.Reasons },
                  onOpenDrones = { sub = SubScreen.Drones },
                  onOpenPrefs = { sub = SubScreen.Prefs },
                  checked = checklist,
                  onToggleCheck = { i -> checklist = checklist.mapIndexed { j, v -> if (j == i) !v else v } },
                  onAltitudeChange = {}
                )
                Tab.MAP -> MapTab(
                  report = report,
                  lat = lat,
                  lon = lon,
                  placeName = placeName,
                  limit = limit,
                  prefs = prefs,
                  isTuxtla = isTuxtla,
                  terrain = terrain,
                  terrainLoading = terrainLoading,
                  radarFrames = radarFrames,
                  onRequestRadar = { vm.refreshRadar() },
                  windGrid = windGrid,
                  gridFailed = gridFailed,
                  onRequestGrid = { vm.refreshWindGrid(lat, lon) },
                  onBackToHome = { tab = Tab.HOME },
                  // Descarga del informe PDF del punto y la hora elegidos
                  // en el mapa: mismo generador que Más > Informe PDF
                  // (PdfReport.generate), con la altura planeada de las
                  // Preferencias y el dron elegido.
                  onDownloadPdf = { hour ->
                    scope.launch {
                      val uri = withContext(Dispatchers.IO) {
                        PdfReport.generate(context, report, vm.dayReport.value, lat, lon, placeName, hour, prefs.plannedAltitudeM, drone, vm.weekRef.value, checklist.all { it })
                      }
                      if (uri != null) {
                        Toast.makeText(context, "Informe guardado en Descargas", Toast.LENGTH_LONG).show()
                        try {
                          val open = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                          context.startActivity(open)
                        } catch (_: Exception) { /* sin visor de PDF: el archivo ya quedó en Descargas */ }
                      } else {
                        Toast.makeText(context, "No se pudo generar el PDF", Toast.LENGTH_LONG).show()
                      }
                    }
                  }
                )
                Tab.FORECAST -> ForecastScreen(
                  report = report,
                  placeName = placeName,
                  limit = limit,
                  prefs = prefs
                )
                Tab.MORE -> MoreScreen(onOpen = { dest -> sub = dest.toSubScreen() })
              }
              else -> {
                TextButton(onClick = { sub = null }) { Text("‹ Atrás", color = Geo.Accent) }
                when (currentSub) {
                  SubScreen.Reasons -> ReasonsScreen(report, origin, prefs, placeName == "Mi ubicación actual", drone.limit)
                  SubScreen.Drones -> DronesScreen(drone) { vm.drone.value = it }
                  SubScreen.Prefs -> PrefsScreen(prefs, drone.limit) { vm.updatePrefs(it) }
                  SubScreen.Pdf -> PdfReportScreen(vm, report, lat, lon, placeName, drone, checklist)
                  SubScreen.Appearance -> AppearanceScreen(
                    placeName = placeName,
                    onUseGps = { useGps() },
                    onBackToTuxtla = {
                      lat = 16.75; lon = -93.11
                      placeName = "Tuxtla Gutiérrez, Chiapas"
                      vm.refresh(lat, lon)
                    }
                  )
                  SubScreen.Sources -> SourcesScreen(
                    diag = diag,
                    lastAttempt = lastAttempt,
                    error = err,
                    loading = loading,
                    onTestConnection = { vm.testConnection(lat, lon) },
                    onCopyDiag = {
                      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Geoviento", vm.diagText(lat, lon)))
                      Toast.makeText(context, "Diagnóstico copiado, pégalo en el chat", Toast.LENGTH_LONG).show()
                    }
                  )
                  SubScreen.Safety -> SafetyScreen()
                  SubScreen.Privacy -> PrivacyScreen()
                  SubScreen.Support -> SupportScreen()
                  SubScreen.About -> AboutScreen()
                }
              }
            }
          }
        }
      }
    }
  }

  private fun loadGps(fused: com.google.android.gms.location.FusedLocationProviderClient, onLoc: (Double, Double) -> Unit, onDone: () -> Unit) {
    try {
      fused.lastLocation
        .addOnSuccessListener { it?.let { onLoc(it.latitude, it.longitude); onDone() } }
        .addOnFailureListener { /* sin GPS: se queda Tuxtla por defecto */ }
    } catch (_: SecurityException) {}
  }
}

// Último estado conocido de la app, para el txt de cierre (ver onCreate).
object CrashLog {
  @Volatile var lastState: String = "sin datos todavía"
}
