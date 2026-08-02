package com.screenassistant.service.system.action

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Notificación de alarma disparada (O4-P2). Canal propio "alarm_channel" de
 * IMPORTANCE_HIGH; guard silencioso si falta POST_NOTIFICATIONS.
 *
 * El smallIcon usa un drawable del sistema (android.R.drawable) porque el módulo
 * service:system no puede referenciar el R de la app; ic_lock_idle_alarm existe
 * en todas las versiones y es temáticamente una alarma. Igualmente, el
 * contentIntent apunta a MainActivity por nombre (setClassName con el package
 * de la app) para no añadir dependencia de módulo app → service:system.
 */
class AlarmNotificationHelper(
    private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "alarm_channel"
    }

    fun showAlarm(label: String, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel()
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent().apply {
                setPackage(context.packageName)
                setClassName(context.packageName, "${context.packageName}.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("⏰ $label")
            .setContentText("Toca para descartar")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(requestCode, notification)
    }

    // Idempotente: createNotificationChannel con un id ya existente no hace nada.
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarmas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de las alarmas programadas"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
