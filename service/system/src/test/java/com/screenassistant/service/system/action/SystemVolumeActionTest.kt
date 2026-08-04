package com.screenassistant.service.system.action

import android.content.Context
import android.media.AudioManager
import android.os.Build
import com.screenassistant.core.domain.model.VolumeAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * SystemVolumeAction con la lambda volumeControllerProvider inyectada:
 * testabilidad total sin tocar AudioManager (métodos finales en API 35,
 * no interceptables por MockK). La interfaz VolumeController se mockea y
 * el flujo se verifica contra ella.
 */
class SystemVolumeActionTest {

    private lateinit var volumeController: VolumeController
    private lateinit var action: SystemVolumeAction

    @Before
    fun setup() {
        volumeController = mockk()
        every { volumeController.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 10
        every { volumeController.getStreamVolume(AudioManager.STREAM_MUSIC) } returns 5
        // MockK 1.13.12: "no answer found" para métodos sin stub (aunque devuelvan Unit)
        every { volumeController.setStreamVolume(any(), any(), any()) } returns Unit
        every { volumeController.adjustStreamVolume(any(), any(), any()) } returns Unit

        action = SystemVolumeAction(
            context = mockk(relaxed = true),
            volumeControllerProvider = { volumeController }
        )
    }

    @Test
    fun `UP sube 5 niveles desde el volumen actual`() {
        val result = action.setVolume(VolumeAction.UP)

        assertEquals("Éxito: Volumen subido a 10.", result)
        verify { volumeController.setStreamVolume(AudioManager.STREAM_MUSIC, 10, 0) }
    }

    @Test
    fun `DOWN baja 5 niveles hasta cero`() {
        val result = action.setVolume(VolumeAction.DOWN)

        assertEquals("Éxito: Volumen bajado a 0.", result)
        verify { volumeController.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
    }

    @Test
    fun `MAX pone el volumen al maximo del stream`() {
        val result = action.setVolume(VolumeAction.MAX)

        assertEquals("Éxito: Volumen al máximo.", result)
        verify { volumeController.setStreamVolume(AudioManager.STREAM_MUSIC, 10, 0) }
    }

    @Test
    fun `MIN pone el volumen a cero`() {
        val result = action.setVolume(VolumeAction.MIN)

        assertEquals("Éxito: Volumen al mínimo.", result)
        verify { volumeController.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
    }

    @Test
    fun `MUTE en API 30 usa ADJUST_MUTE`() {
        // M11: en API < 31 se mantiene ADJUST_MUTE (aún no deprecado).
        val actionApi30 = SystemVolumeAction(
            context = mockk(relaxed = true),
            volumeControllerProvider = { volumeController },
            sdkInt = { Build.VERSION_CODES.R }
        )

        val result = actionApi30.setVolume(VolumeAction.MUTE)

        assertEquals("Éxito: Teléfono en silencio.", result)
        verify {
            volumeController.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        }
    }

    @Test
    fun `MUTE en API 31 usa ADJUST_TOGGLE_MUTE`() {
        // M11: ADJUST_MUTE deprecado en API 31+ → ADJUST_TOGGLE_MUTE.
        val actionApi31 = SystemVolumeAction(
            context = mockk(relaxed = true),
            volumeControllerProvider = { volumeController },
            sdkInt = { Build.VERSION_CODES.S }
        )

        val result = actionApi31.setVolume(VolumeAction.MUTE)

        assertEquals("Éxito: Teléfono en silencio.", result)
        verify {
            volumeController.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        }
    }

    @Test
    fun `UP se topa con el maximo del stream`() {
        every { volumeController.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 8
        every { volumeController.getStreamVolume(AudioManager.STREAM_MUSIC) } returns 7

        val result = action.setVolume(VolumeAction.UP)

        assertEquals("Éxito: Volumen subido a 8.", result)
        verify { volumeController.setStreamVolume(AudioManager.STREAM_MUSIC, 8, 0) }
    }

    @Test
    fun `setVolume devuelve error si el controller lanza excepcion`() {
        every { volumeController.getStreamVolume(AudioManager.STREAM_MUSIC) } throws RuntimeException("boom")

        val result = action.setVolume(VolumeAction.UP)

        assertEquals("Error: No pude ajustar el volumen.", result)
    }
}
