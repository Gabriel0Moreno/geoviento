# Changelog — GeoViento

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
