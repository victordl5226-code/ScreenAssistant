package com.screenassistant.service.system.action

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.screenassistant.core.data.local.ActiveAlarmStore
import com.screenassistant.core.data.local.AlarmEntity
import java.util.Calendar

/**
 * Alarmas 100% in-app con AlarmManager (O4-P2): se acabó ACTION_SET_ALARM /
 * ACTION_SHOW_ALARMS (no permiten cancelar alarmas de terceros).
 *
 * Testabilidad: dependencias por constructor con defaults + lambdas providers
 * (mismo patrón que NoteAction/SystemVolumeAction/AppLauncherAction). Dagger no
 * soporta lambdas con default en @Inject constructor → se provee vía @Provides
 * en AppModule (única vía de creación).
 */
class AlarmAction(
    private val context: Context,
    private val store: ActiveAlarmStore,
    private val notifier: AlarmNotificationHelper,
    private val alarmSchedulerProvider: () -> AlarmScheduler = { AndroidAlarmScheduler(context) },
    private val now: () -> Calendar = { Calendar.getInstance() }
) {

    companion object {
        const val ACTION_ALARM_FIRED = "com.screenassistant.ALARM_FIRED"
        const val EXTRA_REQUEST_CODE = "request_code"
        private val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    /**
     * QA #1 (CRÍTICO): UNA única fábrica privada de PendingIntent compartida por
     * setAlarm / cancelAlarm / restore. Mismo requestCode → mismo action, mismo
     * package y mismos flags. Si divergieran, cancelar/restaurar apuntaría a un
     * PendingIntent distinto del que se programó → alarma fantasma.
     */
    private fun buildPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(ACTION_ALARM_FIRED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, PI_FLAGS)
    }

    /**
     * "Pon una alarma" sin hora. O4-P2 elimina ACTION_SHOW_ALARMS (no permite
     * cancelar alarmas de terceros); en su lugar se guía al usuario para que
     * diga la hora de la alarma in-app.
     */
    fun openAlarms(): String =
        "Error: Dime a qué hora quieres la alarma, por ejemplo: 'pon una alarma a las 7:30'."

    suspend fun setAlarm(hour: Int, minute: Int, label: String?): String {
        val scheduler = alarmSchedulerProvider()
        if (!scheduler.canScheduleExactAlarms()) {
            return "Error: La app no puede programar alarmas exactas. Activa 'Alarmas y recordatorios' en los ajustes del sistema."
        }
        val calendar = now()
        val trigger = nextTrigger(hour, minute, calendar)
        val pretty = prettyTime(hour, minute)
        // Label del usuario ("para ...") → se persiste y llega al título de la
        // notificación; sin label queda el genérico.
        val entityLabel = label?.takeIf { it.isNotBlank() }
            ?.let { "Alarma de las $pretty para $it" }
            ?: "Alarma de las $pretty"
        val requestCode = hour * 60 + minute

        // TOCTOU: PRIMERO se programa en AlarmManager y DESPUÉS se persiste. Si el
        // sistema rechaza la alarma (SecurityException en API 31+ sin
        // SCHEDULE_EXACT_ALARM, IllegalArgumentException...), no queda fila fantasma
        // en Room que se re-propague en cada arranque.
        scheduler.setExactAndAllowWhileIdle(trigger.timeInMillis, buildPendingIntent(requestCode))
        store.upsertAlarm(
            AlarmEntity(
                requestCode = requestCode,
                hour = hour,
                minute = minute,
                label = entityLabel,
                triggerAtMillis = trigger.timeInMillis,
                createdAt = calendar.timeInMillis
            )
        )
        return "Éxito: Alarma configurada para las $pretty."
    }

    suspend fun cancelAlarm(hour: Int?, minute: Int?): String {
        val scheduler = alarmSchedulerProvider()
        if (hour == null && minute == null) {
            val alarms = store.allAlarms()
            if (alarms.isEmpty()) return "Error: No hay alarmas que cancelar."
            alarms.forEach { alarm ->
                scheduler.cancel(buildPendingIntent(alarm.requestCode))
            }
            store.clearAll()
            return "Éxito: He cancelado todas las alarmas."
        }
        val h = hour ?: 0
        val m = minute ?: 0
        val requestCode = h * 60 + m
        val pretty = prettyTime(h, m)
        val alarm = store.findAlarm(requestCode)
            ?: return "Error: No encontré ninguna alarma a las $pretty."
        scheduler.cancel(buildPendingIntent(alarm.requestCode))
        store.removeAlarm(alarm.requestCode)
        return "Éxito: Alarma de las $pretty cancelada."
    }

    suspend fun onAlarmFired(requestCode: Int) {
        val alarm = store.findAlarm(requestCode) ?: return
        notifier.showAlarm(alarm.label, requestCode)
        store.removeAlarm(requestCode)
    }

    /**
     * Reprogramación tras reinicio del proceso (llamado desde ScreenAssistantApp).
     * Las futuras se reprograman SIEMPRE con el nextTrigger recomputado (nunca se
     * reusa el triggerAtMillis almacenado); las pasadas se purgan.
     *
     * Guard de permiso (QA Supervisor): sin canScheduleExactAlarms (API 31+) no se
     * toca nada — setExactAndAllowWhileIdle lanzaría SecurityException y el
     * runCatching del arranque la tragaría dejando filas fantasma.
     */
    suspend fun restoreActiveAlarms() {
        val scheduler = alarmSchedulerProvider()
        if (!scheduler.canScheduleExactAlarms()) return
        val calendar = now()
        store.allAlarms().forEach { alarm ->
            if (alarm.triggerAtMillis > calendar.timeInMillis) {
                val trigger = nextTrigger(alarm.hour, alarm.minute, calendar)
                // TOCTOU: setExact primero, upsert después (si el sistema rechaza la
                // reprogramación, la fila se elimina abajo o se queda sin programar).
                scheduler.setExactAndAllowWhileIdle(trigger.timeInMillis, buildPendingIntent(alarm.requestCode))
                store.upsertAlarm(alarm.copy(triggerAtMillis = trigger.timeInMillis))
            } else {
                store.removeAlarm(alarm.requestCode)
            }
        }
    }

    /**
     * Hora hablada → próxima ocurrencia: hoy si aún no ha pasado (comparada con
     * [nowCal]), si no mañana. Devuelve una copia; nunca muta el calendario dado.
     */
    private fun nextTrigger(hour: Int, minute: Int, nowCal: Calendar): Calendar {
        val trigger = nowCal.clone() as Calendar
        trigger.set(Calendar.HOUR_OF_DAY, hour)
        trigger.set(Calendar.MINUTE, minute)
        trigger.set(Calendar.SECOND, 0)
        trigger.set(Calendar.MILLISECOND, 0)
        if (!trigger.after(nowCal)) {
            trigger.add(Calendar.DAY_OF_YEAR, 1)
        }
        return trigger
    }

    private fun prettyTime(hour: Int, minute: Int): String =
        "$hour:${minute.toString().padStart(2, '0')}"
}
