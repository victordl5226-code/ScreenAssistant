package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AirplaneModeActionTest {

    private lateinit var context: Context
    private lateinit var action: AirplaneModeAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        action = AirplaneModeAction(context)
    }

    @Test
    fun `activar devuelve mensaje y lanza intent`() {
        val result = action.setAirplaneMode(true)
        assertEquals("Abriendo ajustes del modo avión para activarlo.", result)
    }

    @Test
    fun `desactivar devuelve mensaje y lanza intent`() {
        val result = action.setAirplaneMode(false)
        assertEquals("Abriendo ajustes del modo avión para desactivarlo.", result)
    }

    @Test
    fun `activar lanza intent settings`() {
        action.setAirplaneMode(true)
        val intentSlot = slot<Intent>()
        verify(exactly = 1) { context.startActivity(capture(intentSlot)) }
    }
}