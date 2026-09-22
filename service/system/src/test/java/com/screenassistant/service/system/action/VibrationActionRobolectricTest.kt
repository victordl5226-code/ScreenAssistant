package com.screenassistant.service.system.action

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests de VibrationAction con Robolectric.
 *
 * Robolectric provee Build.VERSION.SDK_INT REAL (SDK 33),
 * activando la rama VibratorManager en VibrationAction.
 * El vibrador shadow ejecuta la vibración sin crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VibrationActionRobolectricTest {

    private lateinit var context: Context
    private lateinit var action: VibrationAction

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        action = VibrationAction(context)
    }

    @Test
    fun `vibrate con 500ms valido devuelve mensaje con duracion`() {
        val result = action.vibrate(500)
        assertEquals("Vibrando durante 0.5 segundos.", result)
    }

    @Test
    fun `vibrate con 2000ms muestra 2_0 segundos`() {
        val result = action.vibrate(2000)
        assertEquals("Vibrando durante 2.0 segundos.", result)
    }

    @Test
    fun `vibrate con 50ms se ajusta a minimo 100ms`() {
        val result = action.vibrate(50)
        assertEquals("Vibrando durante 0.1 segundos.", result)
    }

    @Test
    fun `vibrate con 20000ms se ajusta a maximo 10000ms`() {
        val result = action.vibrate(20000)
        assertEquals("Vibrando durante 10.0 segundos.", result)
    }

    @Test
    fun `vibrate ejecuta sin excepcion en SDK 33`() {
        // Verifica que SDK 33 usa VibratorManager branch sin crash
        action.vibrate(100)
        // Si llega aquí sin excepción, el branching funciona
        assertTrue(true)
    }

    @Test
    fun `vibratePattern sos devuelve mensaje de patron`() {
        val result = action.vibratePattern("sos")
        assertEquals("Vibrando patrón: sos.", result)
    }

    @Test
    fun `vibratePattern invalido pide patron valido`() {
        val result = action.vibratePattern("invalido")
        assertTrue(result.contains("Patrón no reconocido"))
    }
}
