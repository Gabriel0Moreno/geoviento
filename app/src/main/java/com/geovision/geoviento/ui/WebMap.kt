// Mapa REAL con calles en un WebView (Leaflet + baldosas OpenStreetMap).
//
// El mapa nativo de MapLibre 10.2.0 tumbaba la app en el moto g75 al
// cargarlo ("Geoviento Mx continúa fallando" tras "Cargando mapa...") con
// un fallo NATIVO que el handler de Java no atrapa: nunca dejó
// geoviento-crash.txt. El WebView no tiene código nativo propio, así que
// no puede tumbar la app de esa forma; si la página no carga (sin
// internet o CDN caído) se muestra el esquema Canvas de antes.
// La demo web usa este mismo mapa Leaflet y ya funciona en el teléfono.
package com.geovision.geoviento.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
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
import com.geovision.geoviento.data.Tuxtla_ZONES
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
      Tuxtla_ZONES.forEachIndexed { i, z ->
        put(JSONObject().apply {
          put("n", i + 1)
          put("name", z.name)
          put("lat", z.lat)
          put("lon", z.lon)
          put("elev", z.elev)
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
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
              if (request.isForMainFrame) webFailed = true
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
<style>html,body{margin:0;height:100%}#map{height:100%;width:100%}.znum{background:#0A2E4D;color:#fff;border:2px solid #fff;border-radius:50%;width:26px;height:26px;line-height:22px;text-align:center;font-weight:bold;font-size:14px;box-shadow:0 1px 4px rgba(0,0,0,.4)}.znum.sel{background:#B3261E}.zlab{background:rgba(255,255,255,.92);border-radius:4px;padding:1px 5px;font-size:11px;font-weight:bold;white-space:nowrap}.arr{font-size:22px;line-height:1;text-shadow:0 0 3px #fff,0 0 3px #fff}</style>
</head><body><div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
var ZONES = __ZONES__;
var DIR = __DIR__; var GUST = __GUST__; var LIMIT = __LIMIT__;
var paused = false; var intensity = 1;
var map = L.map('map', {zoomControl:false}).setView([__LAT__, __LON__], 12);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom:19, attribution:'© OpenStreetMap'}).addTo(map);
var zoneMarkers = {};
ZONES.forEach(function(z){
  var m = L.marker([z.lat, z.lon], {icon: L.divIcon({className:'', html:'<div class="znum" id="zn-'+z.n+'">'+z.n+'</div>', iconSize:[26,26], iconAnchor:[13,13]})}).addTo(map);
  m.bindTooltip(z.name, {permanent:true, direction:'top', offset:[0,-14], className:'zlab'});
  m.on('click', function(){ if(window.Android) Android.onZoneSelected(z.name); selectZone(z.name); });
  zoneMarkers[z.name] = m;
});
function flowColor(g){ return g < LIMIT*0.75 ? '#1B7A3D' : (g < LIMIT ? '#B8860B' : '#B3261E'); }
var arrows = [];
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
makeArrows();
var last = 0;
function tick(ts){
  requestAnimationFrame(tick);
  if(paused) return;
  if(!last) last = ts; var dt = Math.min(50, ts-last); last = ts;
  var toRad = DIR*Math.PI/180;
  var step = dt*0.00002*(0.5 + GUST/10);
  arrows.forEach(function(a){
    a.lat += Math.cos(toRad)*step; a.lon += Math.sin(toRad)*step/Math.cos(16.75*Math.PI/180);
    if(a.lat<16.60||a.lat>16.86||a.lon<-93.30||a.lon>-93.04){ a.lat=16.62+Math.random()*0.22; a.lon=-93.28+Math.random()*0.34; }
    a.m.setLatLng([a.lat, a.lon]);
  });
}
requestAnimationFrame(tick);
function updateWind(d, g){ DIR=d; GUST=g; paintArrows(); }
function setPaused(b){ paused=b; }
function setIntensity(x){ intensity=x; paintArrows(); }
function selectZone(name){
  Object.keys(zoneMarkers).forEach(function(k){
    var el = zoneMarkers[k].getElement(); if(!el) return;
    var d = el.querySelector('.znum'); if(d) d.classList.toggle('sel', k===name);
  });
}
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
