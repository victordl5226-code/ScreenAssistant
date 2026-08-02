package com.screenassistant.service.system.action

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/**
 * Abstracción mínima sobre AlarmManager (O4-P2).
 *
 * Necesaria para testabilidad: AlarmManager es clase con métodos finales no
 * interceptables por MockK en JVM; la impl real delega 1:1 y la prueba inyecta
 * un fake vía la lambda alarmSchedulerProvider (mismo patrón que
 * volumeControllerProvider de SystemVolumeAction).
 */
interface AlarmScheduler {
    fun canScheduleExactAlarms(): Boolean
    fun setExactAndAllowWhileIdle(triggerAtMillis: Long, pendingIntent: PendingIntent)
    fun cancel(pendingIntent: PendingIntent)
}

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun setExactAndAllowWhileIdle(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    override fun cancel(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
    }
}
