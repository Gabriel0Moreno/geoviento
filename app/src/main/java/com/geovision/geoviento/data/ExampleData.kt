package com.geovision.geoviento.data

// Datos de EJEMPLO de la demo web de ayer (foto fija del 11 nov 2026, 02:54 CST).
// Solo sirven para que la interfaz se vea completa aunque no haya internet:
// viento sostenido 0.8 m/s, ráfaga máxima 10.3 m/s a las 16:00, ventana buena
// 06:00–12:00 y peligrosa 15:00–17:00. La app los marca siempre como EJEMPLO
// y los reemplaza con datos en vivo en cuanto llegan. NUNCA se usan para
// decidir un vuelo.
fun exampleReport(lat: Double, lon: Double): WindReport {
  val hours = listOf(
    Triple("2026-11-11T06:00", 0.9, 2.1), Triple("2026-11-11T07:00", 0.8, 1.8),
    Triple("2026-11-11T08:00", 1.1, 2.5), Triple("2026-11-11T09:00", 1.4, 3.2),
    Triple("2026-11-11T10:00", 1.8, 4.0), Triple("2026-11-11T11:00", 2.1, 4.8),
    Triple("2026-11-11T12:00", 2.4, 5.5), Triple("2026-11-11T13:00", 3.0, 6.8),
    Triple("2026-11-11T14:00", 3.6, 8.5), Triple("2026-11-11T15:00", 4.2, 9.8),
    Triple("2026-11-11T16:00", 4.5, 10.3), Triple("2026-11-11T17:00", 4.0, 9.5),
    Triple("2026-11-11T18:00", 3.1, 7.2), Triple("2026-11-11T19:00", 2.2, 5.0),
    Triple("2026-11-11T20:00", 1.7, 3.8), Triple("2026-11-11T21:00", 1.3, 3.0),
    Triple("2026-11-11T22:00", 1.1, 2.5), Triple("2026-11-11T23:00", 1.0, 2.2)
  )
  val hourly = hours.map { (t, sp, gu) ->
    val dir = if (t.substringAfter("T").substringBefore(":").toInt() >= 14) 315.0 else 0.0
    HourlyPoint(t, sp, gu, sp * 1.4, gu * 1.4, dir)
  }
  return WindReport(
    WindNow(0.8, 0.0, 2.5, 1.1, 0.0, 1.2, 0.0, 3.5, 26.0, "2026-11-11T10:00"),
    hourly, "Ejemplo (demo de ayer)", lat, lon
  )
}
