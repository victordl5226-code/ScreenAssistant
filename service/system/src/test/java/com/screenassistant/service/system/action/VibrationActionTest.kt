package com.screenassistant.service.system.action

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VibrationActionTest {

    private lateinit var context: Context
    private lateinit var vibrator: Vibrator
    private lateinit var action: VibrationAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        vibrator = mockk(relaxed = true)
        // Since VibrationAction creates vibrator lazily via getSystemService(String),
        // we mock both possible overloads. isReturnDefaultValues=true handles Build.VERSION.
        every { context.getSystemService(any<String>()) } returns vibrator
        action = VibrationAction(context)
    }

    @Test
    fun `vibrate con duracion valida devuelve mensaje con la duracion`() {
        val result = action.vibrate(500)
        assertEquals("Vibrando durante 0.5 segundos.", result)
    }

    @Test
    fun `vibrate con duracion menor al minimo se ajusta`() {
        val result = action.vibrate(50)
        assertEquals("Vibrando durante 0.1 segundos.", result)
    }

    @Test
    fun `vibrate con duracion mayor al maximo se ajusta`() {
        val result = action.vibrate(20000)
        assertEquals("Vibrando durante 10.0 segundos.", result)
    }

    @Test
    fun `vibrate con security exception devuelve error`() {
        // Force vibrator to throw on vibrate
        every { vibrator.vibrate(any<VibrationEffect>()) } throws SecurityException()
        val result = action.vibrate(500)
        assertEquals("Error: No tengo permiso para vibrar el dispositivo.", result)
    }

    @Test
    fun `vibrate con exception generica devuelve error`() {
        every { vibrator.vibrate(any<VibrationEffect>()) } throws RuntimeException("fail")
        val result = action.vibrate(500)
        assertEquals("Error: No pudo activarse la vibración.", result)
    }
}
