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

class MobileDataActionTest {

    private lateinit var context: Context
    private lateinit var action: MobileDataAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        action = MobileDataAction(context)
    }

    @Test
    fun `activar devuelve mensaje y lanza intent`() {
        val result = action.setMobileData(true)
        assertEquals("Abriendo ajustes de datos móviles para activarlos.", result)
    }

    @Test
    fun `desactivar devuelve mensaje y lanza intent`() {
        val result = action.setMobileData(false)
        assertEquals("Abriendo ajustes de datos móviles para desactivarlos.", result)
    }

    @Test
    fun `activar lanza intent wireless settings`() {
        action.setMobileData(true)
        val intentSlot = slot<Intent>()
        verify(exactly = 1) { context.startActivity(capture(intentSlot)) }
    }
}