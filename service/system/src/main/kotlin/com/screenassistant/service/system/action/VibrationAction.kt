package com.screenassistant.service.system.action

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VibrationAction @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun vibrate(durationMs: Long): String {
        return try {
            val safeDuration = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
            val effect = VibrationEffect.createOneShot(
                safeDuration,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
            vibrator.vibrate(effect)
            "Vibrando durante ${safeDuration / 1000.0} segundos."
        } catch (e: SecurityException) {
            "Error: No tengo permiso para vibrar el dispositivo."
        } catch (e: Exception) {
            "Error: No pudo activarse la vibración."
        }
    }

    fun vibratePattern(patternType: String): String {
        return try {
            val pattern = when (patternType.lowercase()) {
                "sos" -> SOS_PATTERN
                "llamada" -> CALL_PATTERN
                "alarma" -> ALARM_PATTERN
                else -> null
            }
            if (pattern == null) {
                return "Patrón no reconocido. Usa: sos, llamada o alarma."
            }
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
            "Vibrando patrón: $patternType."
        } catch (e: SecurityException) {
            "Error: No tengo permiso para vibrar el dispositivo."
        } catch (e: Exception) {
            "Error: No pudo activarse la vibración."
        }
    }

    companion object {
        const val MIN_DURATION_MS = 100L
        const val MAX_DURATION_MS = 10000L

        // Patrones de vibración (tiempo en ms)
        // 0 = onset, >0 = pausa, -1 = no repetir
        private val SOS_PATTERN = longArrayOf(0, 200, 200, 200, 200, 200, 600, 200, 600, 200, 600, 200, 200, 200, 200, 200, 600, 200, 200, 200, 200, 200, 600)
        private val CALL_PATTERN = longArrayOf(0, 500, 300, 500, 300, 500)
        private val ALARM_PATTERN = longArrayOf(0, 1000, 500, 1000, 500, 1000)
    }
}
