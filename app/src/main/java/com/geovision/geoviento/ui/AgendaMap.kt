// Mapa general de la Agenda (v1.5.0): un WebView Leaflet con base
// clara OpenTopoMap (misma cadena de respaldo que el mapa principal:
// OpenTopoMap → CartoDB Voyager → OSM) y UN pin numerado por cada lugar
// agendado. Al tocar un pin se avisa a la app con el id de la fecha.
// Sin mapa nativo, como siempre. OBLIGATORIO aquí también el arreglo
// de la 1.4.2: #map con height:100vh y a los 500 ms ponerle la altura
// real del visor (window.innerHeight) + invalidateSize; en este teléfono
// un contenedor en 0 px tumba el mapa al respaldo.
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.geovision.geoviento.data.AgendaItem
import org.json.JSONArray
import org.json.JSONObject

class AgendaBridge(
  private val onPin: (String) -> Unit,
  private val onError: () -> Unit,
  private val onBase: (String) -> Unit = {},
  private val onJsError: (String) -> Unit = {}
) {
  private val main = Handler(Looper.getMainLooper())

  @JavascriptInterface
  fun onPinSelected(id: String) {
    main.post { onPin(id) }
  }

  @JavascriptInterface
  fun onMapError() {
    main.post { onError() }
  }

  @JavascriptInterface
  fun onBaseLayer(name: String) {
    main.post { onBase(name) }
  }

  @JavascriptInterface
  fun onJsError(msg: String) {
    main.post { onJsError(msg) }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AgendaMap(
  items: List<AgendaItem>,
  selectedId: String?,
  onPin: (String) -> Unit,
  modifier: Modifier = Modifier,
  onStatus: (String) -> Unit = {}
) {
  var webView by remember { mutableStateOf<WebView?>(null) }
  var pageLoaded by remember { mutableStateOf(false) }
  var webFailed by remember { mutableStateOf(false) }
  var baseName by remember { mutableStateOf("OpenTopoMap") }
  val context = LocalContext.current
  val leafletJs = remember { try { context.assets.open("leaflet.js").bufferedReader().readText() } catch (_: Exception) { "" } }
  val leafletCss = remember { try { context.assets.open("leaflet.css").bufferedReader().readText() } catch (_: Exception) { "" } }

  // Pines numerados por fecha (orden cronológico). El JSON se arma con
  // JSONObject para que los nombres con comillas no rompan el JS.
  val pinsJson = remember(items) {
    JSONArray().apply {
      items.sortedBy { it.date + "T" + it.time }.forEachIndexed { i, item ->
        put(JSONObject().apply {
          put("id", item.id)
          put("n", i + 1)
          put("lat", item.lat)
          put("lon", item.lon)
          put("label", item.placeName)
        })
      }
    }.toString()
  }
  val html = remember(pinsJson, leafletJs, leafletCss) {
    buildAgendaMapHtml(pinsJson, leafletCss, leafletJs)
  }

  if (webFailed) {
    Box(modifier, contentAlignment = Alignment.Center) {
      Text("Mapa no disponible sin conexión", color = Geo.Muted)
    }
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
          addJavascriptInterface(AgendaBridge(
            onPin = onPin,
            onError = { onStatus("Agenda: error de la página → sin mapa"); webFailed = true },
            onBase = { name -> baseName = name; onStatus("Agenda: base cambiada a $name (respaldo automático)") },
            onJsError = { msg -> onStatus("Agenda: error JS: $msg") }
          ), "Android")
          webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
              pageLoaded = true
              onStatus("Agenda: página cargada, comprobando…")
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
              if (request.isForMainFrame) {
                onStatus("Agenda: error al cargar la página → sin mapa")
                webFailed = true
              }
            }

            // Si el proceso del WebView muere, no se cierra la app: se
            // muestra el aviso y listo.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
              onStatus("Agenda: el visor falló → sin mapa")
              webFailed = true
              return true
            }
          }
          // Carga desde archivo, como el mapa principal (v1.2.4): el
          // archivo es propio ("geoviento-agenda-map.html") para no
          // pisar el del mapa principal.
          val loadedFromFile = try {
            val f = java.io.File(ctx.cacheDir, "geoviento-agenda-map.html")
            f.writeText(html)
            loadUrl("file://${f.absolutePath}")
            true
          } catch (_: Exception) {
            false
          }
          if (!loadedFromFile) {
            loadDataWithBaseURL("https://tile.openstreetmap.org/", html, "text/html", "utf-8", null)
          }
          // Verificación con tiempo límite DESDE LA CREACIÓN, igual que
          // el mapa principal: primero la señal NATIVA (contentHeight
          // == 0 → la página no se dibujó) y luego por JavaScript
          // (Leaflet, __mapReady y la altura real del contenedor #map).
          Handler(Looper.getMainLooper()).postDelayed({
            if (!webFailed) {
              if (contentHeight == 0) {
                onStatus("Agenda: la página no se dibujó (alto 0) → sin mapa")
                webFailed = true
              } else {
                try {
                  evaluateJavascript("""(typeof L !== 'undefined') + '|' + (window.__mapReady === true) + '|' + (document.getElementById('map') ? document.getElementById('map').clientHeight : -1)""") { result ->
                    val parts = (result ?: "").trim('"').split("|")
                    val hasL = parts.getOrNull(0) == "true"
                    val ready = parts.getOrNull(1) == "true"
                    val h = parts.getOrNull(2)?.toIntOrNull() ?: -1
                    // OJO: el texto se arma con paréntesis, como en el
                    // mapa principal; sin ellos el if/else se traga el
                    // resto del mensaje (bug de la 1.4.1).
                    val statusText = "Agenda: Leaflet " + (if (hasL) "✓" else "✗") + " · listo " + (if (ready) "✓" else "✗") + " · alto " + h + " px · base " + baseName
                    onStatus(statusText)
                    if (!hasL || !ready || h <= 50) webFailed = true
                  }
                } catch (_: Exception) {
                  onStatus("Agenda: no se pudo comprobar → sin mapa")
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
  LaunchedEffect(pageLoaded, selectedId) {
    if (pageLoaded) {
      val arg = if (selectedId == null) "null" else "'$selectedId'"
      webView?.evaluateJavascript("selectPin($arg)", null)
    }
  }
  // Los pines llegan por Kotlin cuando la agenda cambia (después de
  // que la página ya cargó): se empujan con setPins y el JS los dibuja
  // y ajusta el encuadre. La primera carga ya los trae en el HTML.
  LaunchedEffect(pageLoaded, pinsJson) {
    if (pageLoaded) webView?.evaluateJavascript("setPins($pinsJson)", null)
  }
}

// HTML del mapa de la agenda: Leaflet + base clara OpenTopoMap
// (respaldo Voyager y luego OSM), pines numerados (tocar uno avisa a
// la app). Sin template literals de JS (${...}) para no chocar con las
// plantillas de Kotlin.
private fun buildAgendaMapHtml(pinsJson: String, leafletCss: String, leafletJs: String): String {
  val raw = """
<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>__LEAFLET_CSS__</style>
<style>html,body{margin:0;height:100%;background:#E9E7DE}#map{height:100vh;width:100%;background:#E9E7DE}.pnum{background:#0A2E4D;color:#fff;border:2px solid #fff;border-radius:50%;width:26px;height:26px;line-height:22px;text-align:center;font-weight:bold;font-size:14px;box-shadow:0 1px 4px rgba(0,0,0,.35)}.pnum.sel{background:#B3261E}.plab{background:rgba(255,255,255,.93);color:#0A2E4D;border-radius:4px;padding:1px 5px;font-size:11px;font-weight:bold;white-space:nowrap;box-shadow:0 1px 3px rgba(0,0,0,.25)}</style>
<script>__LEAFLET_JS__</script>
</head><body><div id="map"></div>
<script>
var PINS = __PINS__;
var map = null; var pinLayer = null; var pinMarkers = {};
var baseLayers = []; var baseIdx = 0; var baseErrors = 0;
// Cualquier error de JS en la página se reporta a la app para que la
// línea de diagnóstico diga exactamente qué falló.
window.onerror = function(m, s, l){ if(window.Android) Android.onJsError(m + ' @' + l); };
function esc(s){ return (s + '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
function initMap(){
  if(typeof L === 'undefined' || map !== null) return;
  var center = [16.75, -93.11]; var zoom = 12;
  if(PINS.length > 0){ center = [PINS[0].lat, PINS[0].lon]; }
  map = L.map('map', {zoomControl:false}).setView(center, zoom);
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
  pinLayer = L.layerGroup().addTo(map);
  renderPins();
  window.__mapReady = true;
  // Arreglo 1.4.2 (OBLIGATORIO también aquí): el div mide 100vh y
  // medio segundo después se le pone la altura real del visor
  // (window.innerHeight) y se le pide recalcular; sin esto, en el
  // teléfono el mapa medía "alto 0 px" y la comprobación de 8 s lo
  // tumbaba al respaldo.
  setTimeout(function(){
    var d = document.getElementById('map');
    if(d) d.style.height = window.innerHeight + 'px';
    if(map !== null) map.invalidateSize();
  }, 500);
}
function renderPins(){
  if(map === null || pinLayer === null) return;
  pinLayer.clearLayers(); pinMarkers = {};
  for(var i=0;i<PINS.length;i++){
    (function(p){
      var m = L.marker([p.lat, p.lon], {icon: L.divIcon({className:'', html:'<div class="pnum" id="pn-' + p.id + '">' + p.n + '</div>', iconSize:[26,26], iconAnchor:[13,13]})}).addTo(pinLayer);
      m.bindTooltip(esc(p.label), {permanent:true, direction:'top', offset:[0,-14], className:'plab'});
      m.on('click', function(){ if(window.Android) Android.onPinSelected(p.id); selectPin(p.id); });
      pinMarkers[p.id] = m;
    })(PINS[i]);
  }
  if(PINS.length > 1){
    var bounds = [];
    for(var j=0;j<PINS.length;j++){ bounds.push([PINS[j].lat, PINS[j].lon]); }
    map.fitBounds(bounds, {padding:[30,30]});
  } else if(PINS.length === 1){
    map.setView([PINS[0].lat, PINS[0].lon], 12);
  }
}
function setPins(pins){
  PINS = pins || [];
  renderPins();
}
function selectPin(id){
  Object.keys(pinMarkers).forEach(function(k){
    var el = pinMarkers[k].getElement(); if(!el) return;
    var d = el.querySelector('.pnum'); if(d) d.classList.toggle('sel', k===id);
  });
}
initMap();
window.addEventListener('load', function(){
  setTimeout(function(){ if(map === null && window.Android) Android.onMapError(); }, 4000);
});
</script></body></html>
"""
  return raw
    .replace("__PINS__", pinsJson)
    .replace("__LEAFLET_CSS__", leafletCss)
    .replace("__LEAFLET_JS__", leafletJs)
}
