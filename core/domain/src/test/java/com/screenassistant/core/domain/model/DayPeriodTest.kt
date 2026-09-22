package com.screenassistant.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DayPeriodTest {

    @Test
    fun `fromHour 0 retorna MADRUGADA`() {
        assertEquals(DayPeriod.MADRUGADA, DayPeriod.fromHour(0))
    }

    @Test
    fun `fromHour 5 retorna MADRUGADA`() {
        assertEquals(DayPeriod.MADRUGADA, DayPeriod.fromHour(5))
    }

    @Test
    fun `fromHour 6 retorna MANANA`() {
        assertEquals(DayPeriod.MANANA, DayPeriod.fromHour(6))
    }

    @Test
    fun `fromHour 11 retorna MANANA`() {
        assertEquals(DayPeriod.MANANA, DayPeriod.fromHour(11))
    }

    @Test
    fun `fromHour 12 retorna MEDIODIA`() {
        assertEquals(DayPeriod.MEDIODIA, DayPeriod.fromHour(12))
    }

    @Test
    fun `fromHour 13 retorna MEDIODIA`() {
        assertEquals(DayPeriod.MEDIODIA, DayPeriod.fromHour(13))
    }

    @Test
    fun `fromHour 14 retorna TARDE`() {
        assertEquals(DayPeriod.TARDE, DayPeriod.fromHour(14))
    }

    @Test
    fun `fromHour 19 retorna TARDE`() {
        assertEquals(DayPeriod.TARDE, DayPeriod.fromHour(19))
    }

    @Test
    fun `fromHour 20 retorna NOCHE`() {
        assertEquals(DayPeriod.NOCHE, DayPeriod.fromHour(20))
    }

    @Test
    fun `fromHour 23 retorna NOCHE`() {
        assertEquals(DayPeriod.NOCHE, DayPeriod.fromHour(23))
    }

    @Test
    fun `fromHour -1 lanza IllegalArgumentException`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DayPeriod.fromHour(-1)
        }
        assertEquals("Hora fuera de rango: -1 (debe ser 0-23)", ex.message)
    }

    @Test
    fun `fromHour 24 lanza IllegalArgumentException`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DayPeriod.fromHour(24)
        }
        assertEquals("Hora fuera de rango: 24 (debe ser 0-23)", ex.message)
    }

    @Test
    fun `fromHour 100 lanza IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            DayPeriod.fromHour(100)
        }
    }

    @Test
    fun `todas las horas 0-23 mapean a un periodo sin excepciones`() {
        for (hour in 0..23) {
            val period = DayPeriod.fromHour(hour)
            assert(period in DayPeriod.entries) { "Hora $hour no mapeó a ningún período" }
        }
    }

    @Test
    fun `hourRange de cada periodo es consistente con fromHour`() {
        for (period in DayPeriod.entries) {
            for (hour in period.hourRange) {
                assertEquals(period, DayPeriod.fromHour(hour))
            }
        }
    }

    @Test
    fun `valores del enum son exactamente 5`() {
        assertEquals(5, DayPeriod.entries.size)
    }

    @Test
    fun `MADRUGADA cubre 0-5`() {
        for (h in 0..5) assertEquals(DayPeriod.MADRUGADA, DayPeriod.fromHour(h))
    }

    @Test
    fun `MANANA cubre 6-11`() {
        for (h in 6..11) assertEquals(DayPeriod.MANANA, DayPeriod.fromHour(h))
    }

    @Test
    fun `MEDIODIA cubre 12-13`() {
        for (h in 12..13) assertEquals(DayPeriod.MEDIODIA, DayPeriod.fromHour(h))
    }

    @Test
    fun `TARDE cubre 14-19`() {
        for (h in 14..19) assertEquals(DayPeriod.TARDE, DayPeriod.fromHour(h))
    }

    @Test
    fun `NOCHE cubre 20-23`() {
        for (h in 20..23) assertEquals(DayPeriod.NOCHE, DayPeriod.fromHour(h))
    }
}
