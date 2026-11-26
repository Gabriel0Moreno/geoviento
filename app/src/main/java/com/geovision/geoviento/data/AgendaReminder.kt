package com.geovision.geoviento.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

// Recordatorio de la agenda (v1.5.0): WorkManager programa UN trabajo
// por fecha que suena a la hora del vuelo menos el aviso (60 min por
// defecto). El trabajo sobrevive al reinicio de la app (WorkManager lo
// persiste solo); al editar o borrar una fecha se cancela su trabajo por
// etiqueta. LÍMITE CONOCIDO: WorkManager no es exacto al minuto — con
// el teléfono en ahorro de batería el aviso puede llegar con unos
// minutos de retraso. Para un aviso exacto haría falta una alarma
// exacta (permiso SCHEDULE_EXACT_ALARM), que no se pidió para no
// complicar la instalación.
object AgendaReminder {
  const val CHANNEL_ID = "agenda_recordatorios"
  private const val TAG_PREFIX = "agenda_"

  fun schedule(context: Context, item: AgendaItem) {
    cancel(context, item.id)
    val zone = ZoneId.of("America/Mexico_City")
    val targetMs = runCatching {
      LocalDate.parse(item.date).atTime(
        item.time.substringBefore(":").toInt(),
        item.time.substringAfter(":").toInt()
      ).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull() ?: return
    val triggerMs = targetMs - item.remindMinutesBefore * 60_000L
    val delayMs = triggerMs - System.currentTimeMillis()
    // Si la hora del aviso ya pasó (fecha muy cercana o en el pasado),
    // no se programa nada: el aviso inmediato sería peor que ninguno.
    if (delayMs <= 0) return
    val data = workDataOf(
      "id" to item.id,
      "note" to item.note,
      "date" to item.date,
      "time" to item.time,
      "place" to item.placeName
    )
    val req = OneTimeWorkRequestBuilder<AgendaReminderWorker>()
      .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
      .addTag(TAG_PREFIX + item.id)
      .setInputData(data)
      .build()
    runCatching { WorkManager.getInstance(context).enqueue(req) }
  }

  fun cancel(context: Context, id: String) {
    runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(TAG_PREFIX + id) }
  }
}

class AgendaReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
  override fun doWork(): Result {
    val note = inputData.getString("note") ?: ""
    val date = inputData.getString("date") ?: ""
    val time = inputData.getString("time") ?: ""
    val place = inputData.getString("place") ?: ""
    val title = if (note.isBlank()) "Vuelo agendado en 1 hora" else note
    val dateLabel = runCatching {
      LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "MX")))
    }.getOrDefault(date)
    // Sin .format() aquí: el texto se arma con plantillas, así no hay
    // ningún % que escapar.
    val text = "$dateLabel · $time · $place — revisa el viento antes de salir."
    val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val ch = NotificationChannel(
        AgendaReminder.CHANNEL_ID,
        "Recordatorios de agenda",
        NotificationManager.IMPORTANCE_DEFAULT
      )
      ch.description = "Avisos 1 hora antes de tus vuelos agendados"
      nm.createNotificationChannel(ch)
    }
    val notification = NotificationCompat.Builder(applicationContext, AgendaReminder.CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_today)
      .setContentTitle(title)
      .setContentText(text)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setAutoCancel(true)
      .build()
    // Sin permiso de notificaciones (Android 13+) el aviso simplemente
    // no se muestra; no se cierra nada.
    runCatching { nm.notify(inputData.getString("id")?.hashCode() ?: 0, notification) }
    return Result.success()
  }
}
