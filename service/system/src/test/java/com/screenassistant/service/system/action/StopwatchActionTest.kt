package com.screenassistant.service.system.action

import com.screenassistant.core.domain.model.StopwatchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchActionTest {

    private var currentTime = 1000L
    private val provider = StopwatchActionProvider(now = { currentTime })

    @Test
    fun `iniciar cronometro devuelve mensaje de inicio`() {
        val result = provider.execute(StopwatchAction.START)
        assertEquals("Cronómetro iniciado.", result)
    }

    @Test
    fun `doble iniciar devuelve error`() {
        provider.execute(StopwatchAction.START)
        val result = provider.execute(StopwatchAction.START)
        assertTrue(result.contains("ya está corriendo"))
    }

    @Test
    fun `parar sin iniciar devuelve error`() {
        val result = provider.execute(StopwatchAction.STOP)
        assertTrue(result.contains("no está corriendo"))
    }

    @Test
    fun `parar despues de iniciar muestra duracion`() {
        provider.execute(StopwatchAction.START)
        currentTime += 5500L
        val result = provider.execute(StopwatchAction.STOP)
        assertTrue(result.contains("detenido"))
        assertTrue(result.contains("5.5 segundos"))
    }

    @Test
    fun `consultar tiempo sin iniciar devuelve error`() {
        val result = provider.execute(StopwatchAction.GET_TIME)
        assertTrue(result.contains("no está corriendo"))
    }

    @Test
    fun `consultar tiempo durante ejecucion`() {
        provider.execute(StopwatchAction.START)
        currentTime += 3000L
        val result = provider.execute(StopwatchAction.GET_TIME)
        assertTrue(result.contains("Llevas"))
        assertTrue(result.contains("3.0 segundos"))
    }

    @Test
    fun `parar y volver a iniciar`() {
        provider.execute(StopwatchAction.START)
        currentTime += 2000L
        provider.execute(StopwatchAction.STOP)
        currentTime += 1000L
        val result = provider.execute(StopwatchAction.START)
        assertEquals("Cronómetro iniciado.", result)
    }

    @Test
    fun `formato minutos`() {
        provider.execute(StopwatchAction.START)
        currentTime += 90000L // 1.5 minutos
        val result = provider.execute(StopwatchAction.STOP)
        assertTrue(result.contains("minutos"))
    }

    @Test
    fun `formato horas`() {
        provider.execute(StopwatchAction.START)
        currentTime += 3661000L // 1h 1m 1s
        val result = provider.execute(StopwatchAction.STOP)
        assertTrue(result.contains("1h"))
        assertTrue(result.contains("1m"))
        assertTrue(result.contains("1s"))
    }
}
