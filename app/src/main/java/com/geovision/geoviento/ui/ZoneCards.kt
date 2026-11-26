// Tarjetas de las 5 zonas de la demo, debajo del mapa.
//
// Vivía en RealMap.kt; al borrar ese archivo en la v1.1.5 (adiós MapLibre)
// se perdió también esta función y la v1.1.5 no compiló. Ahora vive aquí,
// sola, para que ningún cambio de mapa vuelva a llevársela.
package com.geovision.geoviento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geovision.geoviento.data.ZONES

@Composable
fun ZoneCards(selectedZone: String?, onZoneSelected: (String?) -> Unit) {
  ZONES.forEachIndexed { i, z ->
    val selected = selectedZone == z.name
    Card(
      onClick = { onZoneSelected(if (selected) null else z.name) },
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = if (selected) Geo.SelectedGreenBg else Geo.Card)
    ) {
      Column(Modifier.padding(12.dp)) {
        Row {
          Text("${i + 1}  ${z.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = Geo.Text)
          Text("${z.elevM} m", style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
        }
        Text(z.note, style = MaterialTheme.typography.bodySmall, color = Geo.Muted)
        Text(z.tag, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Geo.Green)
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}
