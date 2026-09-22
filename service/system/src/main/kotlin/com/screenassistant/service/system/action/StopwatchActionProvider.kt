package com.screenassistant.service.system.action

import com.screenassistant.core.domain.model.StopwatchAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StopwatchActionProvider @Inject constructor(
    private val now: () -> Long = System::currentTimeMillis
) {
    @Volatile
    private var startTimeMillis: Long? = null

    fun execute(commandAction: com.screenassistant.core.domain.model.StopwatchAction): String = when (commandAction) {
        com.screenassistant.core.domain.model.StopwatchAction.START -> start()
        com.screenassistant.core.domain.model.StopwatchAction.STOP -> stop()
        com.screenassistant.core.domain.model.StopwatchAction.GET_TIME -> getTime()
    }

    private fun start(): String {
        if (startTimeMillis != null) {
            return "El cronómetro ya está corriendo. Usa 'para cronómetro' para detenerlo."
        }
        startTimeMillis = now()
        return "Cronómetro iniciado."
    }

    private fun stop(): String {
        val start = startTimeMillis
            ?: return "El cronómetro no está corriendo. Usa 'inicia cronómetro' para empezar."
        val elapsed = now() - start
        startTimeMillis = null
        return "Cronómetro detenido: ${formatDuration(elapsed)}."
    }

    private fun getTime(): String {
        val start = startTimeMillis
            ?: return "El cronómetro no está corriendo."
        val elapsed = now() - start
        return "Llevas ${formatDuration(elapsed)}."
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val ms = (millis % 1000) / 100

        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes} minutos y ${seconds} segundos"
            else -> "${seconds}.${ms} segundos"
        }
    }
}
