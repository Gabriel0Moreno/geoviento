# Cómo lanzar actualizaciones de GeoViento

Regla: la versión vive en UN solo lugar: `app/build.gradle.kts` (`versionCode` y `versionName`).

## Actualización normal (nueva APK para Gabo)
1. Edita el código.
2. Sube `versionCode` en +1 y `versionName` (ej. 1.0.0 -> 1.1.0).
3. Añade una entrada en `CHANGELOG.md`.
4. Haz commit y push a `main`.
5. GitHub Actions compila sola. Descarga la APK en Actions > run > Artifacts > `geoviento-debug-apk`.

## Lanzamiento con Release (recomendado para Gabo)
1. Haz los pasos 1-3.
2. Crea un tag: `git tag v1.1.0 && git push origin v1.1.0`
3. Actions crea un Release en GitHub con la APK adjunta. Gabo la descarga desde Releases, sin entrar a Actions.

## Qué NO subir nunca
- `local.properties` (tiene la ruta del SDK de cada máquina, está en .gitignore).
- Carpetas `build/` ni `.gradle/`.
- Claves de firma: esta APK es debug, sin firmar para tienda. Para Play Store haría falta firma release, no incluida.

## Estructura
- `app/src/main/java/com/geovision/geoviento/data/` — Open-Meteo, met.no, repositorio.
- `app/src/main/java/com/geovision/geoviento/ui/` — MainActivity, ViewModel, semáforo, mapa.
- `.github/workflows/android.yml` — compilación automática.
