// Mapa REAL con calles en un WebView (Leaflet + baldosas OpenStreetMap).
//
// El mapa nativo de MapLibre 10.2.0 tumbaba la app en el moto g75 al
// cargarlo ("Geoviento Mx continúa fallando" tras "Cargando mapa...") con
// un fallo NATIVO que el handler de Java no atrapa: nunca dejó
// geoviento-crash.txt. El WebView no tiene código nativo propio, así que
// no puede tumbar la app de esa forma; si la página no carga se muestra
// el esquema Canvas de antes. La demo web usa este mismo mapa Leaflet y
// ya funciona en el teléfono.
//
// Por qué en la v1.1.6 el mapa se veía en blanco: Leaflet se cargaba de un
// solo CDN (unpkg); si ese CDN no responde, la página nunca termina de
// cargar, el evento `load` nunca se dispara y el mapa queda en blanco sin
// error. Ahora hay DOS CDN (unpkg y jsdelivr) y una verificación con
// tiempo límite desde Kotlin: si Leaflet no existe a los 8 s, cae al
// esquema. Además onRenderProcessGone evita que un fallo del renderizador
// del WebView cierre la app: muestra el esquema en su lugar.
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.geovision.geoviento.data.ZONES
import org.json.JSONArray
import org.json.JSONObject

class ZoneBridge(
  private val onZone: (String) -> Unit,
  private val onError: () -> Unit
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebMap(
  lat: Double, lon: Double, dir: Double, gust: Double, limit: Double,
  playing: Boolean, intensity: Float, selectedZone: String?, onZone: (String) -> Unit,
  fallback: @Composable () -> Unit
) {
  var webView by remember { mutableStateOf<WebView?>(null) }
  var pageLoaded by remember { mutableStateOf(false) }
  var webFailed by remember { mutableStateOf(false) }
  val zonesJson = remember {
    JSONArray().apply {
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
  val html = remember(zonesJson, limit) {
    buildWebMapHtml(zonesJson, lat, lon, dir, gust, limit)
  }

  if (webFailed) {
    fallback()
    return
  }
  Box(Modifier.fillMaxWidth().height(300.dp)) {
    AndroidView(
      factory = { ctx ->
        WebView(ctx).apply {
          settings.javaScriptEnabled = true
          settings.domStorageEnabled = true
          addJavascriptInterface(ZoneBridge(onZone) { webFailed = true }, "Android")
          webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
              pageLoaded = true
              view.evaluateJavascript("updateWind($dir, $gust)", null)
              // Verificación con tiempo límite: si el CDN se quedó colgado,
              // la página "termina" pero Leaflet nunca existió. A los 8 s
              // se comprueba y, si no hay Leaflet, cae al esquema.
              Handler(Looper.getMainLooper()).postDelayed({
                if (!webFailed) {
                  view.evaluateJavascript("typeof L") { result ->
                    if (result == "\"undefined\"") webFailed = true
                  }
                }
              }, 8000)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
              if (request.isForMainFrame) webFailed = true
            }

            // Si el proceso del WebView muere, no se cierra la app: se
            // muestra el esquema y listo.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
              webFailed = true
              return true
            }
          }
          loadDataWithBaseURL("https://tile.openstreetmap.org/", html, "text/html", "utf-8", null)
          webView = this
        }
      },
      modifier = Modifier.fillMaxWidth().height(300.dp)
    )
    if (!pageLoaded) {
      Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        Text("Cargando mapa…")
      }
    }
  }
  LaunchedEffect(pageLoaded, dir, gust) {
    if (pageLoaded) webView?.evaluateJavascript("updateWind($dir, $gust)", null)
  }
  LaunchedEffect(pageLoaded, playing) {
    if (pageLoaded) webView?.evaluateJavascript("setPaused(${!playing})", null)
  }
  LaunchedEffect(pageLoaded, intensity) {
    if (pageLoaded) webView?.evaluateJavascript("setIntensity($intensity)", null)
  }
  LaunchedEffect(pageLoaded, selectedZone) {
    if (pageLoaded) {
      val arg = if (selectedZone == null) "null" else "'$selectedZone'"
      webView?.evaluateJavascript("selectZone($arg)", null)
    }
  }
}

// HTML del mapa: Leaflet + OSM, 5 zonas numeradas (tocar una avisa a la
// app) y flechas que se mueven con el viento. Sin template literals de JS
// (${...}) para no chocar con las plantillas de Kotlin.
private fun buildWebMapHtml(zonesJson: String, lat: Double, lon: Double, dir: Double, gust: Double, limit: Double): String {
  val raw = """
<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css">
<style>html,body{margin:0;height:100%}#map{height:100%;width:100%}.znum{background:#0A2E4D;color:#fff;border:2px solid #fff;border-radius:50%;width:26px;height:26px;line-height:22px;text-align:center;font-weight:bold;font-size:14px;box-shadow:0 1px 4px rgba(0,0,0,.4)}.znum.sel{background:#B3261E}.zlab{background:rgba(255,255,255,.92);border-radius:4px;padding:1px 5px;font-size:11px;font-weight:bold;white-space:nowrap}.arr{font-size:22px;line-height:1;text-shadow:0 0 3px #fff,0 0 3px #fff}</style>
<script>
function __leafletFallback(){
  if(typeof L !== 'undefined'){ initMap(); return; }
  var s=document.createElement('script');
  s.src='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js';
  s.onload=function(){ initMap(); };
  s.onerror=function(){ if(window.Android) Android.onMapError(); };
  document.head.appendChild(s);
}
</script>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" onerror="__leafletFallback()"></script>
</head><body><div id="map"></div>
<script>
var ZONES = __ZONES__;
var DIR = __DIR__; var GUST = __GUST__; var LIMIT = __LIMIT__;
var paused = false; var intensity = 1;
var map = null; var zoneMarkers = {}; var arrows = [];
function flowColor(g){ return g < LIMIT*0.75 ? '#1B7A3D' : (g < LIMIT ? '#B8860B' : '#B3261E'); }
function initMap(){
  if(typeof L === 'undefined' || map !== null) return;
  map = L.map('map', {zoomControl:false}).setView([__LAT__, __LON__], 12);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom:19, attribution:'© OpenStreetMap'}).addTo(map);
  ZONES.forEach(function(z){
    var m = L.marker([z.lat, z.lon], {icon: L.divIcon({className:'', html:'<div class="znum" id="zn-'+z.n+'">'+z.n+'</div>', iconSize:[26,26], iconAnchor:[13,13]})}).addTo(map);
    m.bindTooltip(z.name, {permanent:true, direction:'top', offset:[0,-14], className:'zlab'});
    m.on('click', function(){ if(window.Android) Android.onZoneSelected(z.name); selectZone(z.name); });
    zoneMarkers[z.name] = m;
  });
  makeArrows();
  requestAnimationFrame(tick);
}
function makeArrows(){
  var i, lat, lon;
  for(i=0;i<14;i++){
    lat = 16.62 + Math.random()*0.22; lon = -93.28 + Math.random()*0.34;
    var m = L.marker([lat,lon], {icon: L.divIcon({className:'', html:'<div class="arr">➤</div>', iconSize:[22,22], iconAnchor:[11,11]}), interactive:false}).addTo(map);
    arrows.push({m:m, lat:lat, lon:lon});
  }
  paintArrows();
}
function paintArrows(){
  if(map === null) return;
  var c = flowColor(GUST);
  arrows.forEach(function(a){
    var el = a.m.getElement(); if(!el) return;
    var d = el.querySelector('.arr'); if(!d) return;
    d.style.color = c;
    d.style.transform = 'rotate(' + (DIR - 90) + 'deg)';
    d.style.opacity = (0.35 + 0.65*intensity).toFixed(2);
    d.style.fontSize = (16 + 10*intensity) + 'px';
  });
}
var last = 0;
function tick(ts){
  requestAnimationFrame(tick);
  if(map === null || paused) return;
  if(!last) last = ts; var dt = Math.min(50, ts-last); last = ts;
  var toRad = DIR*Math.PI/180;
  var step = dt*0.00002*(0.5 + GUST/10);
  arrows.forEach(function(a){
    a.lat += Math.cos(toRad)*step; a.lon += Math.sin(toRad)*step/Math.cos(16.75*Math.PI/180);
    if(a.lat<16.60||a.lat>16.86||a.lon<-93.30||a.lon>-93.04){ a.lat=16.62+Math.random()*0.22; a.lon=-93.28+Math.random()*0.34; }
    a.m.setLatLng([a.lat, a.lon]);
  });
}
function updateWind(d, g){ DIR=d; GUST=g; paintArrows(); }
function setPaused(b){ paused=b; }
function setIntensity(x){ intensity=x; paintArrows(); }
function selectZone(name){
  Object.keys(zoneMarkers).forEach(function(k){
    var el = zoneMarkers[k].getElement(); if(!el) return;
    var d = el.querySelector('.znum'); if(d) d.classList.toggle('sel', k===name);
  });
}
if(typeof L !== 'undefined'){ initMap(); } else { __leafletFallback(); }
window.addEventListener('load', function(){
  setTimeout(function(){ if(typeof L === 'undefined' && window.Android) Android.onMapError(); }, 4000);
});
</script></body></html>
"""
  return raw
    .replace("__ZONES__", zonesJson)
    .replace("__LAT__", lat.toString())
    .replace("__LON__", lon.toString())
    .replace("__DIR__", dir.toString())
    .replace("__GUST__", gust.toString())
    .replace("__LIMIT__", limit.toString())
}
