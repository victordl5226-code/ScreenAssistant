package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.screenassistant.core.domain.model.SystemCommand
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerAction @Inject constructor(
    private val context: Context
) {
    fun setTimer(minutes: Int): String {
        // M1 (Lote 10): guard defensivo ANTES del try (red final del invariante —
        // precedente B2 en AlarmAction.setAlarm). Fuera de [1, 1440] → error sin
        // tocar context (ningún startActivity).
        if (minutes !in SystemCommand.TIMER_MIN_MINUTOS..SystemCommand.TIMER_MAX_MINUTOS) {
            return "Error: La duración debe estar entre 1 minuto y 24 horas."
        }
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60_000L)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Éxito: Temporizador configurado para $minutes minutos."
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e  // B5: la cancelación nunca se traga
        } catch (e: Exception) {
            "Error: No se pudo configurar el temporizador."
        }
    }
}
