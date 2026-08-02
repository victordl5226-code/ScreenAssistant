package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import io.mockk.EqMatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * TimerAction con MockK puro (mismo patrón que CallActionTest):
 * los stubs de prototipo (constructedWith) se registran en @Before ANTES de la
 * producción y los verifies reutilizan los MISMOS matchers (mockk #1426/#1501).
 */
class TimerActionTest {

    private lateinit var context: Context
    private lateinit var timerAction: TimerAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)

        mockkConstructor(Intent::class)
        every {
            constructedWith<Intent>(EqMatcher(AlarmClock.ACTION_SET_TIMER)).putExtra(any(), any<Long>())
        } returns mockk()
        every {
            constructedWith<Intent>(EqMatcher(AlarmClock.ACTION_SET_TIMER)).putExtra(any(), any<Boolean>())
        } returns mockk()
        every {
            constructedWith<Intent>(EqMatcher(AlarmClock.ACTION_SET_TIMER)).setFlags(any())
        } returns mockk()

        timerAction = TimerAction(context)
    }

    @After
    fun tearDown() {
        unmockkConstructor(Intent::class)
    }

    @Test
    fun `setTimer de 5 minutos usa 300000 ms y arranca la actividad`() {
        val result = timerAction.setTimer(5)

        assertEquals("Éxito: Temporizador configurado para 5 minutos.", result)
        verify {
            constructedWith<Intent>(EqMatcher(AlarmClock.ACTION_SET_TIMER))
                .putExtra(AlarmClock.EXTRA_LENGTH, 300_000L)
        }
        verify { context.startActivity(any()) }
    }

    @Test
    fun `setTimer de 120 minutos usa 7200000 ms`() {
        val result = timerAction.setTimer(120)

        assertEquals("Éxito: Temporizador configurado para 120 minutos.", result)
        verify {
            constructedWith<Intent>(EqMatcher(AlarmClock.ACTION_SET_TIMER))
                .putExtra(AlarmClock.EXTRA_LENGTH, 7_200_000L)
        }
    }

    @Test
    fun `setTimer marca SKIP_UI false y NEW_TASK`() {
        timerAction.setTimer(5)

        verify {
            constructedWith<Intent>(EqMatcher(AlarmClock.ACTION_SET_TIMER))
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        verify {
            constructedWith<Intent>(EqMatcher(AlarmClock.ACTION_SET_TIMER))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Test
    fun `setTimer devuelve error si startActivity lanza excepcion`() {
        every { context.startActivity(any()) } throws RuntimeException("boom")

        val result = timerAction.setTimer(5)

        assertEquals("Error: No se pudo configurar el temporizador.", result)
    }
}
