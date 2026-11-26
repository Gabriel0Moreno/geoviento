// Mapa REAL con calles en un WebView (Leaflet + baldosas).
//
// El mapa nativo de MapLibre 10.2.0 tumbaba la app en el moto g75 al
// cargarlo ("Geoviento Mx continúa fallando" tras "Cargando mapa...") con
// un fallo NATIVO que el handler de Java no atrapa: nunca dejó
// geoviento-crash.txt. El WebView no tiene código nativo propio, así que
// no puede tumbar la app de esa forma; si la página no carga se muestra
// el esquema Canvas de antes. La demo web usa este mismo mapa Leaflet y
// ya funciona en el teléfono.
//
// v1.2.4: en la v1.2.3 el mapa SEGUÍA en blanco con Leaflet local y sin
// caer al esquema (captura de Gabo 01:04, versión 1.2.3 confirmada en
// Ajustes: área blanca, sin "Cargando mapa…" ni esquema). La comprobación
// por JavaScript no sirve si la página ni siquiera se dibujó: en una
// página rota el callback de evaluateJavascript puede no dispararse
// nunca. Cambios: (1) la página ya no se carga con loadDataWithBaseURL
// (una cadena de ~165 KB que el WebView puede no tragar) sino desde un
// archivo en cacheDir con loadUrl("file://..."); (2) la verificación de
// 8 s mira primero una señal NATIVA (contentHeight == 0 → la página no
// se dibujó → esquema) y luego por JavaScript (Leaflet, __mapReady y la
// altura real del contenedor #map); (3) hay una línea de diagnóstico
// visible bajo el mapa con el resultado de la comprobación, para saber
// en el teléfono qué falló sin adivinar.
//
// v1.3.0: capa base oscura CartoDB Dark Matter (a juego con el tema
// oscuro de la app) con respaldo automático a OSM si las teselas oscuras
// fallan (a partir de 4 errores de tesela se cambia la base y se anota
// en el diagnóstico; el mapa NO cae al esquema por eso). Capa de radar
// de lluvia animada (RainViewer, API pública sin llave): los frames se
// piden en Kotlin (WindRepository.fetchRadarFrames) y se pasan por
// setRadarFrames; la animación cicla los frames cada 700 ms con
// play/pausa. Si no hay frames, el radar no se muestra y nada falla.
// La capa "Clima" es un overlay de la app (nubosidad/precipitación de
// Open-Meteo), no una tesela: el satélite de RainViewer venía vacío en
// la respuesta del 2026-11-26 y no se puede depender de él.
//
// v1.4.0 (Gabo: "se ve horrible el mapa"): la base oscura se veía como
// un vacío negro con 3 flechas amarillas sueltas. Nueva base CLARA con
// relieve real (OpenTopoMap, con cadena de respaldo OpenTopoMap →
// CartoDB Voyager → OSM, por conteo de errores de tesela como antes).
// Las 14 flechas "➤" aleatorias que derivaban con animación SE
// ELIMINARON: ahora hay barbas de viento REALES, una por punto de la
// malla 5x5 de Open-Meteo (WindRepository.fetchWindGrid), estáticas, con
// la dirección y la velocidad de cada punto para la hora elegida; si la
// malla falla, no se dibuja nada (nunca se inventa viento espacial).
// Marcadores de zona más discretos para el mapa claro (círculo teal
// oscuro con número blanco, etiqueta clara) y un punto central en el
// lugar elegido. El diagnóstico sale por onStatus para que MapTab lo
// dibuje bajo el mapa. Todo lo demás sigue igual: verificación de 8 s,
// radar animado, respaldo al esquema Canvas, nada de código nativo.
package com.geovision.geoviento.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.geovision.geoviento.data.ZONES
import org.json.JSONArray
import org.json.JSONObject

class ZoneBridge(
  private val onZone: (String) -> Unit,
  private val onError: () -> Unit,
  private val onBase: (String) -> Unit = {},
  private val onJsError: (String) -> Unit = {}
) {
  private val main = Handler(Looper.getMainLooper())

  @JavascriptInterface
  fun onZoneSelected(name: String) {
    main.post { onZone(name) }
  }

  @JavascriptInterface
  fun onMapError() {
    main.post { onError() }
  }

  // La base no cargó y el mapa cambió solo a la siguiente de la cadena:
  // solo se anota, no es un error fatal y el mapa sigue.
  @JavascriptInterface
  fun onBaseLayer(name: String) {
    main.post { onBase(name) }
  }

  // Un error de JavaScript dentro de la página (por ejemplo, algo que
  // tumbó initMap y dejó __mapReady en falso): se reporta en la línea
  // de diagnóstico para saber exactamente qué falló en el teléfono.
  @JavascriptInterface
  fun onJsError(msg: String) {
    main.post { onJsError(msg) }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebMap(
  lat: Double, lon: Double, selectedZone: String?, onZone: (String) -> Unit,
  fallback: @Composable () -> Unit,
  radarFrames: List<String> = emptyList(),
  radarVisible: Boolean = false,
  radarPlaying: Boolean = false,
  barbsVisible: Boolean = true,
  barbHour: Int = -1,
  gridJson: String? = null,
  showZones: Boolean = true,
  modifier: Modifier = Modifier,
  onStatus: (String) -> Unit = {}
) {
  var webView by remember { mutableStateOf<WebView?>(null) }
  var pageLoaded by remember { mutableStateOf(false) }
  var webFailed by remember { mutableStateOf(false) }
  // Qué capa base está activa (OpenTopoMap por defecto; Voyager u OSM si falló).
  var baseName by remember { mutableStateOf("OpenTopoMap") }
  // Leaflet local (assets): se lee una sola vez; si fallara, la página
  // no tendría L y la verificación de 8 s caería al esquema.
  val context = LocalContext.current
  val leafletJs = remember { try { context.assets.open("leaflet.js").bufferedReader().readText() } catch (_: Exception) { "" } }
  val leafletCss = remember { try { context.assets.open("leaflet.css").bufferedReader().readText() } catch (_: Exception) { "" } }
  // Zonas de Tuxtla: solo se dibujan cuando el lugar es Tuxtla
  // (showZones). Fuera de Tuxtla la lista va vacía: el mapa no debe
  // mostrar el Cañón ni Centro en otro estado (bug visto en Cd.
  // Victoria, 2026-11-26).
  val zonesJson = remember(showZones) {
    if (!showZones) "[]" else JSONArray().apply {
      ZONES.forEachIndexed { i, z ->
        put(JSONObject().apply {
          put("n", i + 1)
          put("name", z.name)
          put("lat", z.lat)
          put("lon", z.lon)
          put("elev", z.elevM)
          put("tag", z.tag)
        })
      }
    }.toString()
  }
  val html = remember(zonesJson, lat, lon, leafletJs, leafletCss) {
    buildWebMapHtml(zonesJson, lat, lon, leafletCss, leafletJs)
  }

  if (webFailed) {
    fallback()
    return
  }
  Box(modifier) {
    AndroidView(
      factory = { ctx ->
        WebView(ctx).apply {
          settings.javaScriptEnabled = true
          settings.domStorageEnabled = true
          settings.allowFileAccess = true
          settings.allowUniversalAccessFromFileURLs = true
          addJavascriptInterface(ZoneBridge(
            onZone = onZone,
            onError = { onStatus("Mapa: error de la página → esquema"); webFailed = true },
            onBase = { name -> baseName = name; onStatus("Mapa: base cambiada a $name (respaldo automático)") },
            onJsError = { msg -> onStatus("Mapa: error JS: $msg") }
          ), "Android")
          webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
              pageLoaded = true
              onStatus("Mapa: página cargada, comprobando…")
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
              if (request.isForMainFrame) {
                onStatus("Mapa: error al cargar la página → esquema")
                webFailed = true
              }
            }

            // Si el proceso del WebView muere, no se cierra la app: se
            // muestra el esquema y listo.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
              onStatus("Mapa: el visor falló → esquema")
              webFailed = true
              return true
            }
          }
          // Carga desde archivo (v1.2.4): loadDataWithBaseURL con ~165 KB
          // puede no tragárselo el WebView y dejar la página en blanco sin
          // error. El archivo se reescribe en cada creación del WebView.
          val loadedFromFile = try {
            val f = java.io.File(ctx.cacheDir, "geoviento-map.html")
            f.writeText(html)
            loadUrl("file://${f.absolutePath}")
            true
          } catch (_: Exception) {
            false
          }
          if (!loadedFromFile) {
            loadDataWithBaseURL("https://tile.openstreetmap.org/", html, "text/html", "utf-8", null)
          }
          // Verificación con tiempo límite DESDE LA CREACIÓN: primero una
          // señal NATIVA (contentHeight == 0 → la página no se dibujó) y
          // luego por JavaScript (Leaflet, __mapReady y alto del contenedor).
          Handler(Looper.getMainLooper()).postDelayed({
            if (!webFailed) {
              if (contentHeight == 0) {
                onStatus("Mapa: la página no se dibujó (alto 0) → esquema")
                webFailed = true
              } else {
                try {
                  evaluateJavascript("""(typeof L !== 'undefined') + '|' + (window.__mapReady === true) + '|' + (document.getElementById('map') ? document.getElementById('map').clientHeight : -1)""") { result ->
                    val parts = (result ?: "").trim('"').split("|")
                    val hasL = parts.getOrNull(0) == "true"
                    val ready = parts.getOrNull(1) == "true"
                    val h = parts.getOrNull(2)?.toIntOrNull() ?: -1
                    // OJO: el texto se arma con paréntesis. Antes, sin
                    // ellos, el if/else se tragaba el resto del mensaje y
                    // la línea solo decía "Mapa: Leaflet ✓" aunque el
                    // mapa no estuviera listo o midiera 0 px (así se
                    // escondió el fallo del 26 nov).
                    val statusText = "Mapa: Leaflet " + (if (hasL) "✓" else "✗") + " · listo " + (if (ready) "✓" else "✗") + " · alto " + h + " px · base " + baseName
                    onStatus(statusText)
                    if (!hasL || !ready || h <= 50) webFailed = true
                  }
                } catch (_: Exception) {
                  onStatus("Mapa: no se pudo comprobar → esquema")
                  webFailed = true
                }
              }
            }
          }, 8000)
          webView = this
        }
      },
      modifier = Modifier.fillMaxSize()
    )
    if (!pageLoaded) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Cargando mapa…", color = Geo.Muted)
      }
    }
  }
  LaunchedEffect(pageLoaded, selectedZone) {
    if (pageLoaded) {
      val arg = if (selectedZone == null) "null" else "'$selectedZone'"
      webView?.evaluateJavascript("selectZone($arg)", null)
    }
  }
  // La malla de viento llega por Kotlin cuando la consulta termina
  // (después de que la página ya cargó): se empuja con setGrid y el JS
  // dibuja las barbas para la hora actual. Cambiar de hora solo llama
  // a setBarbHour, sin volver a pedir la malla.
  LaunchedEffect(pageLoaded, gridJson) {
    if (pageLoaded && gridJson != null) {
      webView?.evaluateJavascript("setGrid($gridJson)", null)
    }
  }
  LaunchedEffect(pageLoaded, barbHour) {
    if (pageLoaded) webView?.evaluateJavascript("setBarbHour($barbHour)", null)
  }
  LaunchedEffect(pageLoaded, barbsVisible) {
    if (pageLoaded) webView?.evaluateJavascript("setBarbsVisible($barbsVisible)", null)
  }
  LaunchedEffect(pageLoaded, lat, lon) {
    if (pageLoaded) webView?.evaluateJavascript("setCenter($lat, $lon)", null)
  }
  // Capas (v1.3.0): las rutas de los frames son /v2/radar/<hex>, sin
  // comillas ni caracteres raros, así que el JSON se puede interpolar
  // directo en la llamada JS.
  LaunchedEffect(pageLoaded, radarFrames) {
    if (pageLoaded && radarFrames.isNotEmpty()) {
      val json = JSONArray().apply { radarFrames.forEach { put(it) } }.toString()
      webView?.evaluateJavascript("setRadarFrames($json)", null)
    }
  }
  LaunchedEffect(pageLoaded, radarVisible) {
    if (pageLoaded) webView?.evaluateJavascript("setRadarVisible($radarVisible)", null)
  }
  LaunchedEffect(pageLoaded, radarPlaying) {
    if (pageLoaded) webView?.evaluateJavascript("setRadarPlaying($radarPlaying)", null)
  }
}

// HTML del mapa: Leaflet + base clara OpenTopoMap (respaldo CartoDB
// Voyager y luego OSM), 5 zonas numeradas (tocar una avisa a la app),
// barbas de viento reales de la malla de Open-Meteo y capa de radar
// RainViewer animada. Sin template literals de JS (${...}) para no
// chocar con las plantillas de Kotlin.
private fun buildWebMapHtml(zonesJson: String, lat: Double, lon: Double, leafletCss: String, leafletJs: String): String {
  val raw = """
<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>__LEAFLET_CSS__</style>
<style>html,body{margin:0;height:100%;background:#E9E7DE}#map{height:100vh;width:100%;background:#E9E7DE}.znum{background:#0A2E4D;color:#fff;border:2px solid #fff;border-radius:50%;width:24px;height:24px;line-height:20px;text-align:center;font-weight:bold;font-size:13px;box-shadow:0 1px 4px rgba(0,0,0,.35)}.znum.sel{background:#B3261E}.zlab{background:rgba(255,255,255,.93);color:#0A2E4D;border-radius:4px;padding:1px 5px;font-size:11px;font-weight:bold;white-space:nowrap;box-shadow:0 1px 3px rgba(0,0,0,.25)}.cdot{width:14px;height:14px;border-radius:50%;background:#2BB3A3;border:2.5px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)}</style>
<script>__LEAFLET_JS__</script>
</head><body><div id="map"></div>
<script>
var ZONES = __ZONES__;
var GRID = null; var barbHour = -1; var barbsVisible = true;
var map = null; var zoneMarkers = {}; var centerMarker = null; var barbLayer = null;
var baseLayers = []; var baseIdx = 0; var baseErrors = 0;
var radarPaths = []; var radarLayer = null; var radarIdx = 0; var radarTimer = null; var radarVisible = false; var radarPlaying = false;
// Cualquier error de JS en la página se reporta a la app para que la
// línea de diagnóstico diga exactamente qué falló (antes el fallo era
// invisible: la línea solo decía "Leaflet ✓").
window.onerror = function(m, s, l){ if(window.Android) Android.onJsError(m + ' @' + l); };
function initMap(){
  if(typeof L === 'undefined' || map !== null) return;
  map = L.map('map', {zoomControl:false}).setView([__LAT__, __LON__], 12);
  var topo = L.tileLayer('https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png', {maxZoom:17, attribution:'&copy; OpenStreetMap contributors, SRTM | style: &copy; OpenTopoMap (CC-BY-SA)'});
  var voyager = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png', {maxZoom:19, attribution:'&copy; OpenStreetMap contributors &copy; CARTO'});
  var osm = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom:19, attribution:'&copy; OpenStreetMap contributors'});
  baseLayers = [{l:topo, n:'OpenTopoMap'}, {l:voyager, n:'Voyager'}, {l:osm, n:'OSM'}];
  [topo, voyager, osm].forEach(function(layer){
    layer.on('tileerror', function(){
      if(baseIdx >= 2) return;
      baseErrors++;
      if(baseErrors >= 4){
        map.removeLayer(layer);
        baseIdx++; baseErrors = 0;
        baseLayers[baseIdx].l.addTo(map);
        if(window.Android) Android.onBaseLayer(baseLayers[baseIdx].n);
      }
    });
  });
  topo.addTo(map);
  centerMarker = L.marker([__LAT__, __LON__], {icon: L.divIcon({className:'', html:'<div class="cdot"></div>', iconSize:[14,14], iconAnchor:[7,7]}), interactive:false}).addTo(map);
  ZONES.forEach(function(z){
    var m = L.marker([z.lat, z.lon], {icon: L.divIcon({className:'', html:'<div class="znum" id="zn-'+z.n+'">'+z.n+'</div>', iconSize:[24,24], iconAnchor:[12,12]})}).addTo(map);
    m.bindTooltip(z.name, {permanent:true, direction:'top', offset:[0,-13], className:'zlab'});
    m.on('click', function(){ if(window.Android) Android.onZoneSelected(z.name); selectZone(z.name); });
    zoneMarkers[z.name] = m;
  });
  barbLayer = L.layerGroup().addTo(map);
  renderBarbs();
  window.__mapReady = true;
  // Si el contenedor aún no tenía tamaño al crear el mapa, Leaflet se
  // queda en 0 px: medio segundo después se le pone al div la altura
  // real del visor (window.innerHeight) y se le pide recalcular. Sin
  // esto, en el teléfono el mapa medía "alto 0 px" y la comprobación de
  // 8 s lo tumbaba al esquema (visto en Cd. de México, 2026-11-26).
  setTimeout(function(){
    var d = document.getElementById('map');
    if(d) d.style.height = window.innerHeight + 'px';
    if(map !== null) map.invalidateSize();
  }, 500);
}
function barbTicks(speed){
  if(speed < 2) return [];
  if(speed < 5) return ['s'];
  if(speed < 8) return ['l'];
  if(speed < 12) return ['l','s'];
  if(speed < 17) return ['l','l'];
  return ['l','l','s'];
}
function barbSvg(dir, speed){
  var ticks = barbTicks(speed);
  var inner = '';
  if(ticks.length === 0){
    inner = '<circle cx="14" cy="14" r="3" fill="#1E6FB8"/>';
  } else {
    inner = '<line x1="14" y1="26" x2="14" y2="4" stroke="#1E6FB8" stroke-width="2"/>';
    for(var i=0;i<ticks.length;i++){
      var y = 6 + i*4;
      var len = ticks[i] === 'l' ? 9 : 5;
      inner += '<line x1="14" y1="' + y + '" x2="' + (14+len) + '" y2="' + (y + len*0.55).toFixed(1) + '" stroke="#1E6FB8" stroke-width="2"/>';
    }
  }
  return '<svg width="28" height="28" viewBox="0 0 28 28"><g transform="rotate(' + dir + ' 14 14)">' + inner + '</g></svg>';
}
function renderBarbs(){
  if(map === null || barbLayer === null) return;
  barbLayer.clearLayers();
  if(!barbsVisible || GRID === null || barbHour < 0) return;
  for(var i=0;i<GRID.points.length;i++){
    var p = GRID.points[i];
    if(barbHour >= p.dir.length || barbHour >= p.speed.length) continue;
    var d = p.dir[barbHour]; var s = p.speed[barbHour];
    if(d === null || d === undefined || s === null || s === undefined) continue;
    var m = L.marker([p.lat, p.lon], {icon: L.divIcon({className:'', html: barbSvg(d, s), iconSize:[28,28], iconAnchor:[14,14]}), interactive:false});
    barbLayer.addLayer(m);
  }
}
function setGrid(g){ GRID = g; renderBarbs(); }
function setBarbHour(i){ barbHour = i; renderBarbs(); }
function setBarbsVisible(b){ barbsVisible = b; renderBarbs(); }
function setCenter(la, lo){
  if(map === null) return;
  map.setView([la, lo], map.getZoom());
  if(centerMarker !== null) centerMarker.setLatLng([la, lo]);
}
function radarUrl(i){ return 'https://tilecache.rainviewer.com' + radarPaths[i] + '/256/{z}/{x}/{y}/2/1_1.png'; }
function ensureRadarLayer(){
  if(map === null || radarPaths.length === 0) return;
  if(radarLayer !== null){ radarLayer.setUrl(radarUrl(radarIdx)); return; }
  radarLayer = L.tileLayer(radarUrl(radarIdx), {opacity:0.6, zIndex:5, attribution:'&copy; RainViewer'}).addTo(map);
}
function startRadarTimer(){
  stopRadarTimer();
  radarTimer = setInterval(function(){
    if(radarPaths.length === 0) return;
    radarIdx = (radarIdx + 1) % radarPaths.length;
    if(radarLayer) radarLayer.setUrl(radarUrl(radarIdx));
  }, 700);
}
function stopRadarTimer(){ if(radarTimer !== null){ clearInterval(radarTimer); radarTimer = null; } }
function setRadarFrames(paths){
  radarPaths = paths || []; radarIdx = 0;
  if(radarVisible) { ensureRadarLayer(); if(radarLayer) radarLayer.addTo(map); if(radarPlaying) startRadarTimer(); }
}
function setRadarVisible(b){
  radarVisible = b;
  if(map === null) return;
  if(b){ ensureRadarLayer(); if(radarLayer) radarLayer.addTo(map); if(radarPlaying) startRadarTimer(); }
  else { stopRadarTimer(); if(radarLayer) map.removeLayer(radarLayer); }
}
function setRadarPlaying(b){
  radarPlaying = b;
  if(b && radarVisible && radarPaths.length > 0) startRadarTimer(); else stopRadarTimer();
}
function selectZone(name){
  Object.keys(zoneMarkers).forEach(function(k){
    var el = zoneMarkers[k].getElement(); if(!el) return;
    var d = el.querySelector('.znum'); if(d) d.classList.toggle('sel', k===name);
  });
}
initMap();
window.addEventListener('load', function(){
  setTimeout(function(){ if(map === null && window.Android) Android.onMapError(); }, 4000);
});
</script></body></html>
"""
  return raw
    .replace("__ZONES__", zonesJson)
    .replace("__LEAFLET_CSS__", leafletCss)
    .replace("__LEAFLET_JS__", leafletJs)
    .replace("__LAT__", lat.toString())
    .replace("__LON__", lon.toString())
}
