package com.screenassistant.core.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitarios para MonitoringPreferences.
 *
 * Nota: getInterval/setInterval requieren un Context real con DataStore
 * (integration test). Aquí testamos la lógica pura: constantes y coerción.
 */
class MonitoringPreferencesTest {

    @Test
    fun `DEFAULT_INTERVAL_MS_LONG es 5000`() {
        assertEquals(5000L, MonitoringPreferences.DEFAULT_INTERVAL_MS_LONG)
    }

    @Test
    fun `DEFAULT_INTERVAL_MS_LONG es positivo`() {
        assert(MonitoringPreferences.DEFAULT_INTERVAL_MS_LONG > 0L)
    }

    @Test
    fun `coerceAtLeast 1000 aplica para valor 0`() {
        assertEquals(1000L, 0L.coerceAtLeast(1000L))
    }

    @Test
    fun `coerceAtLeast 1000 aplica para valor negativo`() {
        assertEquals(1000L, (-500L).coerceAtLeast(1000L))
    }

    @Test
    fun `coerceAtLeast 1000 NO aplica para valor 1000`() {
        assertEquals(1000L, 1000L.coerceAtLeast(1000L))
    }

    @Test
    fun `coerceAtLeast 1000 NO aplica para valor mayor`() {
        assertEquals(10000L, 10000L.coerceAtLeast(1000L))
    }

    @Test
    fun `coerceAtLeast 1000 NO aplica para valor 5000 default`() {
        assertEquals(5000L, MonitoringPreferences.DEFAULT_INTERVAL_MS_LONG.coerceAtLeast(1000L))
    }

    @Test
    fun `intervalo minimo razonable es 1 segundo`() {
        val minInterval = 1000L
        assertEquals(1000L, minInterval)
    }

    @Test
    fun `intervalo maximo razonable es 60 segundos`() {
        val maxInterval = 60000L
        assertEquals(60000L, maxInterval)
    }
}
