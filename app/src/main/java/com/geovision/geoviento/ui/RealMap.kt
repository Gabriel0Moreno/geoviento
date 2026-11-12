package com.geovision.geoviento.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geovision.geoviento.data.ZONES
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import kotlin.math.cos
import kotlin.math.sin

// Mapa REAL con baldosas (MapLibre, estilo demotiles con datos OSM) y flechas
// de corriente animadas, como en la demo. Si el mapa no carga (sin internet
// o error del estilo), se muestra el esquema de respaldo que ya tenía la app.
// OJO: el SDK 10.2.0 todavía usa los paquetes viejos de Mapbox
// (com.mapbox.mapboxsdk.*); el paquete org.maplibre.android.* llegó en la 11.x.
// Por eso los imports son com.mapbox.mapboxsdk, no org.maplibre.android.
@Composable
fun RealMap(
  lat: Double, lon: Double,
  dirFrom: Double, gust: Double, limit: Double,
  playing: Boolean, intensity: Float,
  selectedZone: String?, onZoneSelected: (String?) -> Unit,
  fallback: @Composable () -> Unit
) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  var mapView by remember { mutableStateOf<MapView?>(null) }
  var mapboxMap by remember { mutableStateOf<MapboxMap?>(null) }
  var flowSource by remember { mutableStateOf<GeoJsonSource?>(null) }
  var flowLayer by remember { mutableStateOf<SymbolLayer?>(null) }
  var mapReady by remember { mutableStateOf(false) }
  var mapFailed by remember { mutableStateOf(false) }
  var setupError by remember { mutableStateOf(false) }

  // Rejilla de flechas 7x5 alrededor del centro, con progreso escalonado.
  val cols = 7
  val rows = 5
  val basePoints = remember(lat, lon) {
    List(rows * cols) { k ->
      val r = k / cols
      val c = k % cols
      Pair(lat + (r - 2) * 0.035, lon + (c - 3) * 0.045)
    }
  }
  val progress = remember { FloatArray(rows * cols) { (it * 0.037f) % 1f } }

  fun levelColor(): String = when (flightLevel(gust, limit)) {
    FlightLevel.GREEN -> "#1B7A3D"
    FlightLevel.YELLOW -> "#B8860B"
    FlightLevel.RED -> "#B3261E"
  }

  fun flowFeatures(): List<Feature> {
    val to = Math.toRadians((dirFrom + 180.0) % 360.0)
    val dx = sin(to) * 0.05
    val dy = cos(to) * 0.05
    return basePoints.mapIndexed { i, (bla, blo) ->
      val p = progress[i]
      val f = Feature.fromGeometry(Point.fromLngLat(blo + dx * p, bla + dy * p))
      f.addNumberProperty("rot", (dirFrom + 180.0) % 360.0)
      f
    }
  }

  fun updateFlow() {
    flowSource?.setGeoJson(FeatureCollection.fromFeatures(flowFeatures()))
    flowLayer?.setProperties(iconColor(levelColor()))
  }

  // Si el estilo no carga en 12 s, se usa el esquema de respaldo.
  LaunchedEffect(mapReady) {
    if (!mapReady) {
      delay(12_000)
      if (!mapReady) mapFailed = true
    }
  }

  // Animación de las flechas: avanzan con el viento y vuelven al inicio.
  LaunchedEffect(mapReady, playing, dirFrom, gust, intensity) {
    while (isActive) {
      delay(150)
      if (mapReady && playing) {
        val step = 0.025f * intensity * (1f + (gust / 10.0).toFloat())
        for (i in progress.indices) {
          progress[i] += step
          if (progress[i] > 1f) progress[i] -= 1f
        }
        updateFlow()
      }
    }
  }

  // Actualiza dirección y color cuando cambian los datos.
  LaunchedEffect(mapReady, dirFrom, gust) {
    if (mapReady) updateFlow()
  }

  DisposableEffect(lifecycle, mapView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> mapView?.onStart()
        Lifecycle.Event.ON_RESUME -> mapView?.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
        Lifecycle.Event.ON_STOP -> mapView?.onStop()
        Lifecycle.Event.ON_DESTROY -> mapView?.onDestroy()
        else -> {}
      }
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      mapView?.onDestroy()
    }
  }

  if (mapFailed || setupError) {
    fallback()
    return
  }

  AndroidView(
    factory = { ctx ->
      try {
        Mapbox.getInstance(ctx)
        MapView(ctx).apply {
          mapView = this
          onCreate(null)
          getMapAsync { map ->
            mapboxMap = map
            map.cameraPosition = CameraPosition.Builder()
              .target(LatLng(lat, lon)).zoom(11.0).build()
            map.setStyle("https://demotiles.maplibre.org/tiles/tiles.json", object : Style.OnStyleLoaded {
              override fun onStyleLoaded(style: Style) {
                try {
                  style.addImage("arrow", arrowBitmap())
                  val src = GeoJsonSource("flow", FeatureCollection.fromFeatures(flowFeatures()))
                  flowSource = src
                  style.addSource(src)
                  val layer = SymbolLayer("flow-layer", "flow").withProperties(
                    iconImage("arrow"),
                    iconRotate(Expression.get("rot")),
                    iconAllowOverlap(true),
                    iconSize(0.9f),
                    iconColor(levelColor())
                  )
                  flowLayer = layer
                  style.addLayer(layer)
                  // Marcadores numerados de las 5 zonas.
                  val zoneFeatures = ZONES.mapIndexed { i, z ->
                    val f = Feature.fromGeometry(Point.fromLngLat(z.lon, z.lat))
                    f.addStringProperty("name", z.name)
                    f
                  }
                  style.addSource(GeoJsonSource("zones", FeatureCollection.fromFeatures(zoneFeatures)))
                  ZONES.forEachIndexed { i, z ->
                    style.addImage("zone-${i + 1}", zoneBitmap(i + 1))
                    val zl = SymbolLayer("zone-layer-$i", "zones").withProperties(
                      iconImage("zone-${i + 1}"),
                      iconAllowOverlap(true),
                      iconSize(1.0f)
                    )
                    zl.withFilter(Expression.eq(Expression.get("name"), Expression.literal(z.name)))
                    style.addLayer(zl)
                  }
                  mapReady = true
                } catch (_: Exception) {
                  mapFailed = true
                }
              }
            })
            map.addOnMapClickListener { point ->
              val screen = map.projection.toScreenLocation(point)
              val layers = arrayOf("zone-layer-0", "zone-layer-1", "zone-layer-2", "zone-layer-3", "zone-layer-4")
              val found = map.queryRenderedFeatures(screen, *layers).firstOrNull()
              val name = found?.getStringProperty("name")
              onZoneSelected(name)
              true
            }
          }
        }
      } catch (_: Exception) {
        setupError = true
        android.view.View(ctx)
      }
    },
    modifier = Modifier.fillMaxWidth().height(300.dp)
  )
  if (!mapReady && !mapFailed) {
    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
      Text("Cargando mapa…")
    }
  }
}

private fun arrowBitmap(): Bitmap {
  val size = 64
  val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bmp)
  val paint = Paint().apply { color = Color.WHITE; isAntiAlias = true; style = Paint.Style.FILL }
  // Flecha apuntando al norte (la rotación la da el viento).
  val shaftW = 10f
  canvas.drawRect(size / 2f - shaftW / 2f, size * 0.45f, size / 2f + shaftW / 2f, size * 0.92f, paint)
  val head = android.graphics.Path().apply {
    moveTo(size / 2f, size * 0.05f)
    lineTo(size * 0.85f, size * 0.55f)
    lineTo(size * 0.15f, size * 0.55f)
    close()
  }
  canvas.drawPath(head, paint)
  return bmp
}

private fun zoneBitmap(number: Int): Bitmap {
  val size = 56
  val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bmp)
  val paint = Paint().apply { color = Color.parseColor("#1B7A3D"); isAntiAlias = true; style = Paint.Style.FILL }
  canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)
  val textPaint = Paint().apply {
    color = Color.WHITE; isAntiAlias = true; textSize = 30f; isFakeBoldText = true
    textAlign = Paint.Align.CENTER
  }
  val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
  canvas.drawText(number.toString(), size / 2f, y, textPaint)
  return bmp
}

// Tarjetas de las 5 zonas de la demo, debajo del mapa.
@Composable
fun ZoneCards(selectedZone: String?, onZoneSelected: (String?) -> Unit) {
  ZONES.forEachIndexed { i, z ->
    val selected = selectedZone == z.name
    Card(
      Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE0F0E4) else Color(0xFFE8EEF2)),
      onClick = { onZoneSelected(if (selected) null else z.name) }
    ) {
      Column(Modifier.padding(12.dp)) {
        Row {
          Text("${i + 1}  ${z.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
          Text("${z.elevM} m", style = MaterialTheme.typography.bodySmall)
        }
        Text(z.note, style = MaterialTheme.typography.bodySmall)
        Text(z.tag, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF1B7A3D))
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}
