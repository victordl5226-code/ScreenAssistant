package com.screenassistant.service.system.action

import android.content.Context
import android.media.AudioManager
import android.os.Build
import com.screenassistant.core.domain.model.VolumeAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstracción mínima sobre AudioManager.
 *
 * Necesaria para testabilidad: los métodos de AudioManager son FINAL en la
 * API 35 (no interceptables por MockK en JVM). La impl real delega 1:1 y la
 * prueba inyecta un mock de la interfaz vía la lambda audioManagerProvider
 * (mismo patrón que `now: () -> Long` de AppLauncherAction).
 */
interface VolumeController {
    fun getStreamMaxVolume(streamType: Int): Int
    fun getStreamVolume(streamType: Int): Int
    fun setStreamVolume(streamType: Int, index: Int, flags: Int)
    fun adjustStreamVolume(streamType: Int, direction: Int, flags: Int)
}

class AndroidVolumeController(private val audioManager: AudioManager) : VolumeController {
    override fun getStreamMaxVolume(streamType: Int): Int = audioManager.getStreamMaxVolume(streamType)
    override fun getStreamVolume(streamType: Int): Int = audioManager.getStreamVolume(streamType)
    override fun setStreamVolume(streamType: Int, index: Int, flags: Int) =
        audioManager.setStreamVolume(streamType, index, flags)
    override fun adjustStreamVolume(streamType: Int, direction: Int, flags: Int) =
        audioManager.adjustStreamVolume(streamType, direction, flags)
}

@Singleton
class SystemVolumeAction @Inject constructor(
    private val context: Context,
    // Lambda inyectable: testabilidad total sin mockear AudioManager.
    private val volumeControllerProvider: () -> VolumeController = {
        AndroidVolumeController(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    },
    // M11: SDK inyectable — ADJUST_MUTE está deprecado en API 31+; el default
    // consulta el SDK real y la prueba inyecta un valor fijo (mismo patrón que
    // volumeControllerProvider).
    private val sdkInt: () -> Int = { Build.VERSION.SDK_INT }
) {
    companion object {
        const val STEP = 5
        private const val STREAM = AudioManager.STREAM_MUSIC
    }

    fun setVolume(action: VolumeAction): String {
        return try {
            val controller = volumeControllerProvider()
            val max = controller.getStreamMaxVolume(STREAM)
            when (action) {
                VolumeAction.UP -> {
                    val current = controller.getStreamVolume(STREAM)
                    val target = (current + STEP).coerceAtMost(max)
                    controller.setStreamVolume(STREAM, target, 0)
                    "Éxito: Volumen subido a $target."
                }
                VolumeAction.DOWN -> {
                    val current = controller.getStreamVolume(STREAM)
                    val target = (current - STEP).coerceAtLeast(0)
                    controller.setStreamVolume(STREAM, target, 0)
                    "Éxito: Volumen bajado a $target."
                }
                VolumeAction.MAX -> {
                    controller.setStreamVolume(STREAM, max, 0)
                    "Éxito: Volumen al máximo."
                }
                VolumeAction.MIN -> {
                    controller.setStreamVolume(STREAM, 0, 0)
                    "Éxito: Volumen al mínimo."
                }
                VolumeAction.MUTE -> {
                    // M11: ADJUST_MUTE deprecado en API 31+ → ADJUST_TOGGLE_MUTE
                    // (comportamiento equivalente: alterna el silencio del stream).
                    val direction = if (sdkInt() >= Build.VERSION_CODES.S) {
                        AudioManager.ADJUST_TOGGLE_MUTE
                    } else {
                        AudioManager.ADJUST_MUTE
                    }
                    controller.adjustStreamVolume(STREAM, direction, 0)
                    "Éxito: Teléfono en silencio."
                }
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e  // B5: la cancelación nunca se traga
        } catch (e: Exception) {
            "Error: No pude ajustar el volumen."
        }
    }
}
