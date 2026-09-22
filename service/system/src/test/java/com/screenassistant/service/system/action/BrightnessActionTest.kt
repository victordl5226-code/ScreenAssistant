package com.screenassistant.service.system.action

import android.content.Context
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrightnessActionTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        mockkStatic(Settings.System::class)
        every { Settings.System.canWrite(any<Context>()) } returns true
        every { Settings.System.getInt(any(), any(), any()) } returns 128
        every { Settings.System.putInt(any(), any(), any()) } returns true
        context = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `setBrightness con nivel 0 establece brillo a 0`() {
        val action = BrightnessAction(context)
        val result = action.setBrightness(0)
        assertTrue(result.contains("0%"))
    }

    @Test
    fun `setBrightness con nivel 255 establece brillo a 100`() {
        val action = BrightnessAction(context)
        val result = action.setBrightness(255)
        assertTrue(result.contains("100%"))
    }

    @Test
    fun `setBrightness coerce nivel mayor a 255`() {
        val action = BrightnessAction(context)
        val result = action.setBrightness(300)
        assertTrue(result.contains("100%"))
    }

    @Test
    fun `setBrightness coerce nivel negativo a 0`() {
        val action = BrightnessAction(context)
        val result = action.setBrightness(-5)
        assertTrue(result.contains("0%"))
    }

    @Test
    fun `setBrightness -1 ajusta brillo subiendo`() {
        val action = BrightnessAction(context)
        val result = action.setBrightness(-1)
        assertTrue(result.contains("subido"))
    }

    @Test
    fun `setBrightness -2 ajusta brillo bajando`() {
        val action = BrightnessAction(context)
        val result = action.setBrightness(-2)
        assertTrue(result.contains("bajado"))
    }

    @Test
    fun `setBrightness sin permiso retorna error`() {
        every { Settings.System.canWrite(any<Context>()) } returns false
        val action = BrightnessAction(context)
        val result = action.setBrightness(128)
        assertTrue(result.startsWith("Error"))
    }
}
