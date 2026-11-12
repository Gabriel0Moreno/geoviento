# Changelog — GeoViento

## [1.2.2] - 2026-11-12
- MAPA (por fin): en la 1.2.1 seguía en blanco. Causa real: la
  verificación de 8 s estaba dentro de onPageFinished, que NUNCA se
  dispara si la página se queda colgada esperando el CDN (el <script>
  de unpkg en el <head> bloquea el parseo). Ahora Leaflet 1.9.4 ya no
  viene de ningún CDN: leaflet.js y leaflet.css van DENTRO de la app
  (app/src/main/assets/) y se incrustan en la página, así que el mapa
  arranca siempre que haya datos; solo las baldosas de calles siguen
  viniendo de OpenStreetMap. La verificación de 8 s ahora se programa
  al CREAR el WebView, no al terminar la página, y comprueba que
  Leaflet arrancó (window.__mapReady); si no, cae al esquema.
- PDF COMPLETO: el informe era de UNA sola página y la tabla hora por
  hora se cortaba a las ~18 filas y la referencia y el checklist
  quedaban FUERA de la página. Ahora el PDF pagina: abre una página
  nueva cuando el contenido ya no cabe y sale todo, aunque ocupe dos
  páginas.
- CAMBIO DE UBICACIÓN SIN PERDER INFO: las zonas, el relieve y el
  cañón son de Tuxtla y no pueden describir otra ciudad, pero ahora en
  otro lugar aparece una nota que lo explica en vez de que la
  información "desaparezca" sin más. Además las líneas de giro de
  viento ya no dicen fijo "noroeste" / "gira al NO" (patrón de
  Tuxtla): ahora salen de los datos de esa ciudad y solo aparecen si
  la tarde gira de verdad más de 45°. La mención al cañón y la meseta
  en la intro del PDF también solo sale si el informe es de Tuxtla.

## [1.2.1] - 2026-11-11
- LA 1.2.0 NO COMPILÓ (run #97): MainActivity usa BuildConfig para poner
  la versión de la app en el txt de cierre, pero en el plugin de Android
  8.x BuildConfig NO se genera por defecto. Corregido activando
  `buildConfig = true` en app/build.gradle.kts. Nada más cambió.

## [1.2.0] - 2026-11-11
- MAPA: en la 1.1.6 el WebView se veía en blanco. Causa: Leaflet venía
  de un solo CDN (unpkg); si ese CDN no responde, la página nunca
  termina de cargar, el evento `load` nunca se dispara y el mapa queda
  en blanco sin error. Ahora hay DOS CDN (unpkg y jsdelivr, con
  respaldo automático), una verificación con tiempo límite desde Kotlin
  (si a los 8 s no existe Leaflet, cae al esquema) y onRenderProcessGone
  (si el WebView falla, muestra el esquema en vez de cerrar la app).
- UBICACIÓN PRINCIPAL CAMBIABLE: arriba, bajo el encabezado, hay
  búsqueda por nombre ("Buscar lugar para el viento") y botón "Mi
  ubicación". El encabezado muestra el nombre del lugar elegido y sus
  coordenadas. Las zonas, el relieve, el cañón y su nota solo se
  muestran cerca de Tuxtla; en otro lugar el mapa se centra ahí con
  sus propias flechas y el resto de la app sigue igual.
- DRONES DJI: lista completa seleccionable con la resistencia máxima al
  viento publicada por DJI: Neo 8, Mini SE 8.5, Mavic Mini 8, Mini 2 SE
  / Mini 2 / Mini 3 / Mini 3 Pro / Mini 4 Pro / Mini 4K / Flip 10.7,
  Air 2S / Mavic Air 2 10.7, Air 3 / Air 3S 12, Mavic 2 Pro/Zoom 10,
  Mavic 3 / Classic / Pro / Enterprise 12, Avata / Avata 2 10.7,
  FPV 13.5, Phantom 4 Pro 10, Inspire 2 10, Inspire 3 14, Matrice 30/30T
  15, Matrice 300 RTK 15, Matrice 350 RTK 12, Matrice 4T/4E 12, y el
  DJI Lito X1 / clase <249 g 10.7 sigue primero y por defecto.
- CIERRE EN TXT, PERMANENTE: si la app se cierra de forma inesperada,
  el error queda guardado SIEMPRE (no se quita en versiones futuras):
  archivo con fecha y hora en el almacenamiento interno de la app
  (carpeta "crashes", nunca se pisa el anterior) y copia en Descargas,
  con versión de la app, Android, modelo del teléfono, último estado
  (origen, fuente, hora, ráfaga, lugar) y el error completo. OJO: esto
  atrapa errores de la app; un fallo NATIVO (como el de MapLibre) no lo
  puede atrapar nadie desde aquí, por eso el mapa ya no usa código nativo.
- SIN INFORMACIÓN REPETIDA: se quitó lo que ya estaba en otra parte —
  la tarjeta de límite (estaba en el veredicto y en los chips del dron),
  la "Mejor ventana" del veredicto (está en las tarjetas de ventanas),
  la tarjeta de dirección (está en la brújula), la leyenda bajo el mapa
  (está arriba de la brújula), el párrafo que repetía dirección/viento/
  ráfaga bajo el mapa (queda solo la nota del cañón, cerca de Tuxtla),
  la nota de la capa esquemática (ya no aplica al mapa real), la línea
  del dron en el informe PDF (el dron se elige arriba) y los umbrales
  del pie (están en la leyenda). Además la nota del cañón ya no sale en
  el PDF de lugares fuera de Tuxtla.

## [1.1.6] - 2026-11-11
- LA 1.1.5 NO COMPILÓ (run #84): al borrar RealMap.kt me llevé también
  dos cosas que otros archivos usaban — las tarjetas ZoneCards (que usa
  MainActivity) y los datos de zonas que WebMap importaba con el nombre
  viejo (la lista real es `data.ZONES`, con `elevM`, no `Tuxtla_ZONES`).
  Corregido: ZoneCards vive ahora sola en ZoneCards.kt y WebMap importa
  `data.ZONES` con los campos correctos. El mapa WebView en sí no cambió.

## [1.1.5] - 2026-11-11
- EL MAPA REAL YA NO ES MAPLIBRE: el video de Gabo 22:15 CST muestra el
  cierre exacto — toca "Cargar mapa real", sale "Cargando mapa..." y aparece
  el diálogo del sistema "Geoviento Mx continúa fallando". Y NO hay
  geoviento-crash.txt en Descargas: el fallo es NATIVO del SDK, el handler
  de Java no lo atrapa y no hay forma de ver la causa desde el teléfono. En
  vez de adivinar a ciegas, el mapa real ahora es un WebView con Leaflet +
  baldosas OpenStreetMap (el mismo mapa de la demo, que ya funciona en su
  teléfono): 5 zonas numeradas tocables, flechas animadas con la dirección
  y la ráfaga de la hora elegida, pausa y slider de intensidad. Se eliminó
  la dependencia MapLibre y RealMap.kt; si el WebView no carga (sin
  internet o CDN caído) cae al esquema Canvas de antes. La v1.1.4 quedó
  verificada en el mismo video: teclado ya no tapa la pantalla y el
  semáforo dice AMARILLO en español.

## [1.1.4] - 2026-11-11
- La 1.1.3 ya ABRE (video de Gabo 20:56 CST: interfaz completa con datos en
  vivo, semáforo AMARILLO 9.8 m/s, condiciones en superficie, gráfica de
  líneas, ventanas, esquema y zonas), pero con dos errores visibles: (1) el
  teclado quedaba abierto tapando la pantalla — el campo de búsqueda del
  informe agarraba el foco al abrir y no había forma de esconderlo; ahora se
  quita el foco al arrancar, tocar en cualquier parte vacía esconde el
  teclado, el botón "Buscar" lo esconde y la tecla Listo también; (2) la
  tarjeta "SEMÁFORO AHORA" decía "YELLOW" (nombre del enum en inglés) en
  vez de "AMARILLO".

## [1.1.3] - 2026-11-11
- La 1.1.2 se cerraba al entrar SIN dejar geoviento-crash.txt: el handler de
  Java de la v1.0.4 solo atrapa excepciones de Java, no fallos nativos. El
  sospechoso es el mapa real de MapLibre, que en la 1.1.2 se creaba al abrir
  (su inicialización nativa puede tumbar la app antes de que el handler
  pueda guardar nada). Ahora el mapa real es BAJO DEMANDA: al abrir se ve el
  esquema de siempre y un botón "Cargar mapa real con calles" lo activa solo
  cuando Gabo lo pide. Si al tocarlo se cierra, queda confirmado que es el
  mapa; si no, la app ya abre con toda la interfaz de la demo.

## [1.1.2] - 2026-11-11
- La 1.1.1 seguía sin compilar (run #66): los imports de MapLibre ya estaban
  bien, pero las tarjetas de zonas de RealMap.kt tenían tres errores: (1) el
  `Card` con onClick pasaba el Modifier como primer argumento posicional y
  Material3 lo interpretaba como el onClick; (2) el archivo importa
  android.graphics.Color (para dibujar los bitmaps) y `Color(0xFF...)` se
  resolvía a ese Color en vez de al de Compose; (3) faltaba el import de
  FontWeight. Corregido: onClick nombrado primero, colores de Compose con su
  ruta completa y FontWeight importado.

## [1.1.1] - 2026-11-11
- LA 1.1.0 NO COMPILABA: `Unresolved reference 'maplibre'` en RealMap.kt.
  Causa exacta (verificada en el AAR descargado de Maven Central): el SDK
  10.2.0 todavía usa los paquetes viejos de Mapbox
  (com.mapbox.mapboxsdk.*); el paquete org.maplibre.android.* llegó en la
  11.x. La dependencia SÍ estaba declarada y SÍ existe (POM, módulo y AAR
  con HTTP 200); el error era de los imports, no de la dependencia.
  Corregido cambiando los imports de RealMap.kt a com.mapbox.mapboxsdk
  (Mapbox.getInstance, MapboxMap, MapView, Style, GeoJsonSource,
  SymbolLayer, PropertyFactory, Expression) y las clases GeoJSON siguen en
  com.mapbox.geojson (artefacto transitivo android-sdk-geojson 5.9.0).

## [1.1.0] - 2026-11-11
- LA INTERFAZ COMPLETA DE LA DEMO, CON TODAS SUS FUNCIONALIDADES, EN LA APP
  (a pedido de Gabo, con el video de la demo como referencia):
  - Condiciones en superficie: humedad, presión, probabilidad de lluvia, UV,
    amanecer/atardecer y calidad del aire (nueva API Open-Meteo Air Quality),
    más la tarjeta "Ojo con la tarde" y la leyenda del semáforo.
  - Gráfica de líneas hora por hora (ráfaga en rojo, sostenido en verde, con
    banda amarilla y zona roja) y tarjetas de ventanas del día (buena,
    transición, evita volar, se calma), calculadas con los datos en vivo.
  - MAPA REAL con baldosas (MapLibre, demotiles OSM) y flechas de corriente
    animadas, selector de hora, intensidad del flujo y las 5 zonas numeradas
    con sus tarjetas (Centro, Real del Bosque, Cañón del Sumidero, Copoya,
    Chiapa de Corzo). Si el mapa no carga, se usa el esquema de antes.
  - Diagrama de relieve (corte del valle: Cañón → Valle → Meseta de Copoya)
    con las 3 explicaciones.
  - Calculadora por altura con selector de hora y perfil logarítmico de la
    demo (×1.67 a 105 m), y checklist previo al vuelo con textos calculados
    con los datos de hoy.
  - Informe PDF completo: buscar lugar por nombre (geocodificación),
    latitud/longitud, usar mi ubicación, día (hoy o días pasados con archivo
    ERA5), hora del vuelo, altura planeada y selector de dron (Lito X1 10.7,
    ligera ampliada 12, media 15 m/s); botón "Consultar datos del día" y
    referencia del archivo de 7 días. El semáforo ahora sigue el límite del
    dron elegido (verde < 75%, amarillo 75-100%, rojo >= límite).
  - Preferencia de fuente: se esperan las dos fuentes y se usa Open-Meteo
    cuando responde (trae viento a 80/120 m); met.no solo si Open-Meteo falla.
    La ventana de 24 h ahora empieza en la hora actual, no a medianoche.
  - Corrección menor: la mejor ventana ya no dice "23:00–22:00" cuando todo
    el día está en verde; ahora dice "Todo el día en verde".

## [1.0.5] - 2026-11-11
- EL CIERRE AL ABRIR YA TIENE CAUSA EXACTA (gracias al archivo
  geoviento-crash.txt que guardó la v1.0.4): `UnknownFormatConversionException:
  Conversion = ':'` en MainActivity.kt:275 — el texto del Cañón decía "~45%:"
  dentro de un `.format(...)` y el `%` se interpretaba como inicio de un
  formato. Corregido escapándolo como `%%` (también en el PDF, que tenía el
  mismo texto). La hipótesis de la fábrica del ViewModel era incorrecta.

## [1.0.4] - 2026-11-11
- La v1.0.3 se cerraba apenas al abrir (video de Gabo: logo y de regreso al
  cajón, sin llegar a la interfaz). Causa probable: el ViewModel pasó a
  AndroidViewModel y la fábrica por defecto no lo creaba. Ahora MainActivity
  lo crea con una fábrica explícita. Además, si la app se vuelve a cerrar, el
  error exacto queda guardado en Descargas/geoviento-crash.txt para poder
  diagnosticarlo sin computadora.

## [1.0.3] - 2026-11-11
- La v1.0.2 seguía atorada en "Cargando…" sin mostrar nada: TODA la interfaz
  estaba detrás de la carga de datos. Ahora la interfaz SIEMPRE se ve: al abrir
  muestra los datos de ejemplo de la demo de ayer (marcados EJEMPLO, nunca para
  decidir un vuelo) o los últimos datos guardados, y los reemplaza con datos en
  vivo en cuanto llegan. Nunca más pantalla blanca.
- Diagnóstico de conexión en la app: qué fuente respondió y cuál falló, con el
  mensaje exacto del error y los milisegundos; botones "Probar conexión" y
  "Copiar diagnóstico" (copia el texto para pegarlo en el chat).
- Open-Meteo y met.no ahora se prueban EN PARALELO y gana la primera que
  responde bien. El último reporte bueno se guarda en el teléfono.
- Workflow (11 nov 2026, sin cambio de versión): caché de Gradle desactivado
  (`cache-disabled: true`) porque el servicio de caché de GitHub estuvo caído y
  tumbó 7 compilaciones seguidas ("Cache service responded with 400"). Sin
  caché, Gradle se descarga fresco cada vez y la compilación ya no depende de
  ese servicio.

## [1.0.2] - 2026-11-11
- La v1.0.1 abría pero se quedaba en "Cargando viento en vivo…" y se cerraba sin
  mostrar datos ni error. Blindaje defensivo: red en Dispatchers.IO con timeout
  de 15 s (un cuelgue de red ahora termina en mensaje de error con Reintentar),
  listas horarias combinadas solo hasta el índice mínimo común (sin indexar
  fuera de rango), permiso de ubicación después del primer frame, un solo ciclo
  de actualización cada 15 min, CoroutineExceptionHandler en el ViewModel y
  sin `!!` en la interfaz.
- Visualización final de la demo web, ahora con datos en vivo: selector de hora
  para las corrientes (Ahora + próximas 24 h), botones Corrientes visibles y
  Pausar/Reanudar animación, deslizador de intensidad del flujo, zonas del mapa
  táctiles con su nota, sección "Cómo se mueve el aire hoy sobre el relieve",
  calculadora de viento por altura (deslizador 10–120 m), checklist interactivo
  "Antes de despegar hoy", mejor ventana de vuelo calculada de las ráfagas, e
  informe técnico de vuelo en PDF generado en el teléfono (se guarda en
  Descargas). IMPORTANTE: desinstalar la versión anterior antes de instalar
  esta APK, porque cada build de Actions la firma con una clave distinta.

## [1.0.1] - 2026-11-11
- CORREGIDO el error HTTP 400 de Open-Meteo: la API no tiene ráfagas a 80 ni a 120 m
  (wind_gusts_80m / wind_gusts_120m no existen). Se quitaron de la llamada; la ráfaga
  a 120 m ahora se estima como gust10 * (speed120 / speed10) y se muestra como "est.".
  Verificado con curl: la llamada corregida devuelve 200 con datos reales de Tuxtla.
- Respaldo met.no ahora es REAL (antes era solo un stub): si Open-Meteo falla, la app
  consulta locationforecast 2.0 y muestra los datos con fuente "met.no (respaldo)".
  met.no no da ráfagas: se estiman como viento * 1.4 y se marcan "est.".
- Interfaz nueva en MainActivity: tarjeta semáforo de colores, tarjetas de condiciones
  (10 m / 120 m), brújula con dirección, gráfica de ráfaga por hora (24 h), mapa
  esquemático de corrientes con las 5 zonas de Tuxtla y el Cañón del Sumidero,
  viento por altura (10/80/120 m), checklist previo al vuelo y actualización
  automática real cada 15 min. El mapa con baldosas MapLibre en vivo queda pendiente.
- Se quitó la frase falsa "Se actualiza solo cada 15 min" de la v1.0.0: ahora sí se
  actualiza solo cada 15 min.
- Corrección posterior a la primera subida (mismo 1.0.1): faltaba el import
  `androidx.compose.ui.graphics.drawscope.drawIntoCanvas` en MapScreen.kt y
  Actions fallaba con "Unresolved reference 'drawIntoCanvas'"; ya agregado.

## [1.0.0] - 2026-11-11
- Nombre de la app: Geoviento Mx. Icono de la APK: logo oficial de GeoVisión, sin redibujar.
- Primera versión: viento y ráfagas a 10/80/120 m con Open-Meteo, respaldo met.no.
- Mapa con MapLibre + relieve, rejilla 5x5 de corrientes.
- Semáforo de vuelo para dron <249 g: verde <8 m/s, amarillo 8-10.7, rojo >=10.7.
- GitHub Actions compila la APK en cada push a main y crea Release en cada tag v*.
