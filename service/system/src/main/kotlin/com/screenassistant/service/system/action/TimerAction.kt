package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerAction @Inject constructor(
    private val context: Context
) {
    fun setTimer(minutes: Int): String {
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
