package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsActionTest {

    private lateinit var context: Context
    private lateinit var action: SettingsAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        action = SettingsAction(context)
    }

    @Test
    fun `openSettings retorna exito`() {
        val result = action.openSettings()
        assertTrue(result.startsWith("Éxito"))
    }

    @Test
    fun `openSettings llama startActivity`() {
        action.openSettings()
        verify(exactly = 1) { context.startActivity(any<Intent>()) }
    }

    @Test
    fun `openSettings exception retorna error`() {
        every { context.startActivity(any<Intent>()) } throws RuntimeException("no permiso")
        val result = action.openSettings()
        assertTrue(result.startsWith("Error"))
    }
}
