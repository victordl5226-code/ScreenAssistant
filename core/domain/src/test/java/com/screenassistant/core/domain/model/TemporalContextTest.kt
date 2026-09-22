package com.screenassistant.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TemporalContextTest {

    @Test
    fun `data class equality funciona correctamente`() {
        val dt = LocalDateTime.of(2026, 8, 15, 10, 30)
        val ctx1 = TemporalContext(
            dateTime = dt,
            dayOfWeek = DayOfWeek.FRIDAY,
            dayPeriod = DayPeriod.MANANA,
            esFinDeSemana = false,
            esFestivo = false
        )
        val ctx2 = TemporalContext(
            dateTime = dt,
            dayOfWeek = DayOfWeek.FRIDAY,
            dayPeriod = DayPeriod.MANANA,
            esFinDeSemana = false,
            esFestivo = false
        )
        assertEquals(ctx1, ctx2)
        assertEquals(ctx1.hashCode(), ctx2.hashCode())
    }

    @Test
    fun `data class copy funciona correctamente`() {
        val ctx = TemporalContext(
            dateTime = LocalDateTime.of(2026, 8, 15, 10, 30),
            dayOfWeek = DayOfWeek.FRIDAY,
            dayPeriod = DayPeriod.MANANA,
            esFinDeSemana = false,
            esFestivo = false
        )
        val modified = ctx.copy(esFestivo = true)
        assertTrue(modified.esFestivo)
        assertFalse(ctx.esFestivo)
        assertEquals(ctx.dateTime, modified.dateTime)
    }

    @Test
    fun `companion now crea contexto correcto para martes laboral`() {
        val now = LocalDateTime.of(2026, 8, 25, 15, 0) // martes 15:00
        val ctx = TemporalContext.now(now, emptySet())

        assertEquals(now, ctx.dateTime)
        assertEquals(DayOfWeek.TUESDAY, ctx.dayOfWeek)
        assertEquals(DayPeriod.TARDE, ctx.dayPeriod)
        assertFalse(ctx.esFinDeSemana)
        assertFalse(ctx.esFestivo)
    }

    @Test
    fun `companion now detecta fin de semana`() {
        val now = LocalDateTime.of(2026, 8, 29, 10, 0) // sábado 10:00
        val ctx = TemporalContext.now(now, emptySet())

        assertEquals(DayOfWeek.SATURDAY, ctx.dayOfWeek)
        assertTrue(ctx.esFinDeSemana)
    }

    @Test
    fun `companion now detecta domingo`() {
        val now = LocalDateTime.of(2026, 8, 30, 22, 0) // domingo 22:00
        val ctx = TemporalContext.now(now, emptySet())

        assertEquals(DayOfWeek.SUNDAY, ctx.dayOfWeek)
        assertTrue(ctx.esFinDeSemana)
    }

    @Test
    fun `companion now detecta festivo`() {
        val holiday = LocalDate.of(2026, 12, 25)
        val now = LocalDateTime.of(2026, 12, 25, 12, 0)
        val ctx = TemporalContext.now(now, setOf(holiday))

        assertTrue(ctx.esFestivo)
        assertEquals(DayOfWeek.FRIDAY, ctx.dayOfWeek)
    }

    @Test
    fun `companion now con fecha no festiva en conjunto no es festivo`() {
        val holiday = LocalDate.of(2026, 12, 25)
        val now = LocalDateTime.of(2026, 12, 24, 12, 0)
        val ctx = TemporalContext.now(now, setOf(holiday))

        assertFalse(ctx.esFestivo)
    }

    @Test
    fun `companion now mapea todas las horas a periodos correctos`() {
        // Madrugada
        val ctx0 = TemporalContext.now(LocalDateTime.of(2026, 1, 1, 0, 0), emptySet())
        assertEquals(DayPeriod.MADRUGADA, ctx0.dayPeriod)

        val ctx5 = TemporalContext.now(LocalDateTime.of(2026, 1, 1, 5, 59), emptySet())
        assertEquals(DayPeriod.MADRUGADA, ctx5.dayPeriod)

        // Mañana
        val ctx6 = TemporalContext.now(LocalDateTime.of(2026, 1, 1, 6, 0), emptySet())
        assertEquals(DayPeriod.MANANA, ctx6.dayPeriod)

        val ctx11 = TemporalContext.now(LocalDateTime.of(2026, 1, 1, 11, 59), emptySet())
        assertEquals(DayPeriod.MANANA, ctx11.dayPeriod)

        // Mediodía
        val ctx12 = TemporalContext.now(LocalDateTime.of(2026, 1, 1, 12, 0), emptySet())
        assertEquals(DayPeriod.MEDIODIA, ctx12.dayPeriod)

        val ctx13 = TemporalContext.now(LocalDateTime.of(2026, 1, 1, 13, 59), emptySet())
        assertEquals(DayPeriod.MEDIODIA, ctx13.dayPeriod)

        // Tarde
        val ctx14 = TemporalContext.now(LocalDateTime.of(2026, 1, 1, 14, 0), emptySet())
        assertEquals(DayPeriod.TARDE, ctx14.dayPeriod)

        val ctx19 = TemporalContext.now(LocalDateTime.of(2026, 1, 1, 19, 59), emptySet())
        assertEquals(DayPeriod.TARDE, ctx19.dayPeriod)

        // Noche
        val ctx20 = TemporalContext.now(LocalDateTime.of(2026, 1, 1, 20, 0), emptySet())
        assertEquals(DayPeriod.NOCHE, ctx20.dayPeriod)

        val ctx23 = TemporalContext.now(LocalDateTime.of(2026, 1, 1, 23, 59), emptySet())
        assertEquals(DayPeriod.NOCHE, ctx23.dayPeriod)
    }

    @Test
    fun `now con conjunto vacio de festivos es festivo false`() {
        val ctx = TemporalContext.now(
            LocalDateTime.of(2026, 1, 1, 12, 0),
            emptySet()
        )
        assertFalse(ctx.esFestivo)
    }

    @Test
    fun `companion now con multiples festivos detecta correctamente`() {
        val holidays = setOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 29),
            LocalDate.of(2026, 12, 25)
        )
        val ctx = TemporalContext.now(
            LocalDateTime.of(2026, 3, 29, 8, 0),
            holidays
        )
        assertTrue(ctx.esFestivo)
        assertEquals(DayOfWeek.SUNDAY, ctx.dayOfWeek)
        assertTrue(ctx.esFinDeSemana)
    }
}
