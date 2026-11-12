# Geoviento Mx — Monitor de viento para dron (Android)

Nombre de la app: **Geoviento Mx**. Icono: logo oficial de GeoVisión Aérea Chiapas (simplificado a color, 1600x1600), sin redibujar, en `app/src/main/res/mipmap-*/ic_launcher.png` + icono adaptativo en `mipmap-anydpi-v26`.

App para Gabo / GeoVisión Aérea Chiapas. Antes de volar, muestra viento y ráfagas en tu ubicación, en el mapa con relieve, y un semáforo de vuelo para dron sub-250 g.

## v1.1.0 (2026-11-11): la interfaz completa de la demo, con todas sus funcionalidades
Condiciones en superficie (humedad, presión, lluvia, UV, amanecer/atardecer, calidad del aire), gráfica de líneas hora por hora, tarjetas de ventanas del día, mapa REAL con baldosas (WebView Leaflet + OpenStreetMap) y flechas animadas + 5 zonas numeradas, diagrama de relieve, calculadora por altura con perfil logarítmico, checklist con datos del día, e informe PDF completo (buscar lugar, día de hoy o pasado con archivo ERA5, hora, altura y selector de dron). El semáforo sigue el límite del dron elegido.

## Datos reales (verificado 2026-11-11)
- **Principal: Open-Meteo Forecast API, gratis y sin API key.** Da viento a 10 m, 80 m y 120 m (altura de dron) y ráfagas SOLO a 10 m, pronóstico por hora 3 días. OJO: Open-Meteo NO tiene `wind_gusts_80m` ni `wind_gusts_120m` (pedirlos da HTTP 400, bug corregido en v1.0.1); la ráfaga a 120 m se estima como gust10 * (speed120 / speed10).
  `https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m,wind_speed_80m,wind_direction_80m,wind_speed_120m,wind_direction_120m&hourly=wind_speed_10m,wind_direction_10m,wind_gusts_10m,wind_speed_80m,wind_direction_80m,wind_speed_120m,wind_direction_120m&wind_speed_unit=ms&forecast_days=3&timezone=America/Mexico_City`
- **Respaldo: api.met.no locationforecast 2.0** (gratis, sin key, solo viento medio, sin ráfagas; la app lo consulta de verdad si Open-Meteo falla, y estima la ráfaga como viento * 1.4). Verificado en vivo en Tuxtla (16.75, -93.11, 534 m): 22.5 °C, viento 2.5 m/s desde 346°.
- **Mapa y relieve: WebView con Leaflet + baldosas OpenStreetMap, doble CDN (unpkg y jsdelivr).** Mapa real con calles, flechas de corriente animadas según la dirección y la ráfaga de la hora elegida, y las 5 zonas numeradas (Centro, Real del Bosque, Cañón del Sumidero, Copoya, Chiapa de Corzo). Si Leaflet no carga en 8 s o el WebView falla, se muestra el esquema Canvas de antes. Sin key de Google. (Antes: MapLibre nativo, eliminado en la 1.1.5 porque tumbaba la app al cargarlo con un fallo nativo sin archivo de error.)
- **Corrientes:** rejilla de flechas animadas sobre el mapa real (dirección del pronóstico, color según el semáforo). Es interpolación de pronóstico por modelo, no medición con anemómetro. No inventa sensores.
- **Calidad del aire: Open-Meteo Air Quality API** (gratis, sin key): AQI de EE. UU. y PM2.5.
- **Días pasados y lugares: Open-Meteo Archive API (ERA5)** para el informe PDF de días anteriores, y **Open-Meteo Geocoding API** para buscar lugares por nombre. Todo gratis y sin key.

## Semáforo (según el dron elegido; por defecto DJI Lito X1 <249 g, límite 10.7 m/s)
- VERDE: ráfaga < 75% del límite (Lito X1: < 8 m/s) — cómodo para volar.
- AMARILLO: 75% a 100% del límite (Lito X1: 8 a 10.7 m/s) — precaución, vuela bajo y cerca, vigila batería.
- ROJO: >= límite (Lito X1: >= 10.7 m/s) — no volar. Manda la ráfaga, no el viento medio.
- Otros drones en la app: lista completa de DJI con su límite publicado (Neo 8, Mini SE 8.5, Mavic Mini 8, Mini 2 SE / Mini 2 / Mini 3 / Mini 3 Pro / Mini 4 Pro / Mini 4K / Flip 10.7, Air 2S / Mavic Air 2 10.7, Air 3 / Air 3S 12, Mavic 2 Pro/Zoom 10, Mavic 3 / Classic / Pro / Enterprise 12, Avata / Avata 2 10.7, FPV 13.5, Phantom 4 Pro 10, Inspire 2 10, Inspire 3 14, Matrice 30/30T 15, Matrice 300 RTK 15, Matrice 350 RTK 12, Matrice 4T/4E 12); el semáforo y el informe PDF usan el límite elegido.
- Ubicación principal cambiable arriba (búsqueda por nombre o GPS); las zonas, el relieve y el cañón solo se muestran cerca de Tuxtla.
- Cierre inesperado: queda guardado SIEMPRE en un txt (interno de la app + copia en Descargas), con versión, Android, teléfono, último estado y el error completo. No quitar en versiones futuras.
- Actualización: GPS + refresco cada 15 min + botón Actualizar.

## Cómo compilar el APK
1. Abre esta carpeta en Android Studio (Hedgehog o más reciente).
2. Deja que Gradle sincronice. No necesitas ninguna clave.
3. Build > Build App Bundle(s) / APK(s) > Build APK(s).
4. Instala el APK en tu Android y concede ubicación.

## Archivos
- `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`
- `app/src/main/AndroidManifest.xml` (INTERNET, ACCESS_FINE_LOCATION)
- `data/OpenMeteoApi.kt`, `data/MetNoApi.kt`, `data/WindRepository.kt`, `data/Models.kt`
- `ui/MainActivity.kt`, `ui/WindViewModel.kt`, `ui/FlightSafety.kt`, `ui/MapScreen.kt`

## Estado de compilación (11 nov 2026, VM de Muse)
NO se pudo compilar la APK en esta VM. Un intento limpio, sin reintentos en bucle:

**Lo que SÍ se instaló y quedó funcionando en la VM:**
- JDK 17 Temurin 17.0.21 en `~/jdk17` (`java -version` OK).
- Android SDK en `~/Android/Sdk`: cmdline-tools 12.0, platforms;android-34
  (android.jar verificado), build-tools 34.0.0 (aapt2 2.19 verificado),
  platform-tools, licencias aceptadas. Nota: `sdkmanager` no funciona aquí
  porque el proxy de salida rompe su cliente HTTP de Java; los paquetes se
  descargaron directo con curl desde dl.google.com y se instalaron a mano.
- Gradle 8.7 en `~/gradle-8.7` (el proyecto ya trae `gradlew` + wrapper 8.7).
- `local.properties` apunta a `sdk.dir=/home/hatch/Android/Sdk` (solo válido
  en esta VM; Android Studio en tu PC lo genera solo).

**Error exacto del build (único intento: `gradle assembleDebug --no-daemon -x test`):**
```
FAILURE: Could not receive a message from the daemon.
Caused by: org.gradle.internal.remote.internal.MessageIOException:
  Could not read message from '/127.0.0.1:43469'.
Caused by: java.lang.IllegalArgumentException: Unexpected type tag 109 found.
  at org.gradle.internal.serialize.DefaultSerializerRegistry$TaggedTypeSerializer.read
```
El daemon de Gradle arranca bien (se ve en su log), acepta la conexión en
127.0.0.1, pero el cliente no puede leer sus mensajes y lo elimina del
registro. Loopback TCP sí funciona en esta VM (probado con Python), así que
es un problema del canal cliente-daemon de Gradle en este entorno
sandbox (también se descartó RAM: falla igual con -Xmx512m, y con
GRADLE_USER_HOME unificado tras detectar que Java usa user.home=/root
mientras el shell usa HOME=/home/hatch). El código de la app no llegó a
compilarse: falló antes de descargar dependencias o compilar nada.

**Alternativas (en orden):**
1. **Android Studio en tu PC (recomendado):** abre esta carpeta, deja que
   sincronice Gradle, Build > Build APK(s). No necesitas claves.
2. **GitHub Actions:** sube el proyecto a un repo y usa el workflow
   estándar `android.yml` (setup-java 17 + gradle assembleDebug); el APK
   queda como artifact descargable.
3. Reintentar en otra VM/servidor con Gradle funcional.

## Estado de compilación
- 2026-11-11: en esta VM no se pudo compilar (daemon de Gradle falla con "Unexpected type tag 109", build nunca llegó al código). JDK 17, SDK 34 y Gradle 8.7 sí quedaron instalados aquí.
- GitHub Actions (`/.github/workflows/android.yml`) compila la APK en cada push a main. Descarga: Actions > run > Artifacts > geoviento-debug-apk. Con tag `vX.Y.Z` además crea un Release con la APK.
- Versión actual: 1.2.3 (versionCode 17). Para actualizar, ver `docs/ACTUALIZACIONES.md` y `CHANGELOG.md`. Al instalar una APK nueva de Actions, desinstalar primero la anterior: cada build se firma con una clave distinta.
