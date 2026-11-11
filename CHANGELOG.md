# Changelog — GeoViento

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

## [1.0.0] - 2026-11-11
- Nombre de la app: Geoviento Mx. Icono de la APK: logo oficial de GeoVisión, sin redibujar.
- Primera versión: viento y ráfagas a 10/80/120 m con Open-Meteo, respaldo met.no.
- Mapa con MapLibre + relieve, rejilla 5x5 de corrientes.
- Semáforo de vuelo para dron <249 g: verde <8 m/s, amarillo 8-10.7, rojo >=10.7.
- GitHub Actions compila la APK en cada push a main y crea Release en cada tag v*.
