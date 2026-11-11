package com.geovision.geoviento.ui
// Mapa: MapLibre MapView con estilo OSM + raster-dem para hillshade y terreno 3D.
// Las partículas de viento se dibujan en un overlay Canvas sobre el mapa, interpolando
// la rejilla 5x5 de WindRepository.grid(). Ver MainActivity para el cableado completo.
object MapStyle {
  const val STYLE = "https://demotiles.maplibre.org/style.json" // demo; en producción usa tu estilo OSM + dem
  const val TERRAIN_URL = "https://demotiles.maplibre.org/terrain-tiles/tiles.json"
}
