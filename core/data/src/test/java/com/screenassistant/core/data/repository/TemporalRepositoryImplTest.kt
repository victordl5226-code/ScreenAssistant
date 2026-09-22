package com.screenassistant.core.data.repository

import com.screenassistant.core.data.util.HolidayStore
import com.screenassistant.core.domain.model.DayPeriod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tests de TemporalRepositoryImpl contra HolidayStore mockeado.
 */
class TemporalRepositoryImplTest {

    private lateinit var holidayStore: HolidayStore
    private lateinit var repository: TemporalRepositoryImpl

    @Before
    fun setup() {
        holidayStore = mockk()
        repository = TemporalRepositoryImpl(holidayStore)
    }

    @Test
    fun `getCurrentContext retorna contexto correcto sin festivos`() {
        // 2026-08-25 15:30:00 UTC (martes)
        val instant = LocalDateTime.of(2026, 8, 25, 15, 30)
            .atZone(ZoneId.systemDefault()).toInstant()
        val clock = Clock.fixed(instant, ZoneId.systemDefault())

        every { holidayStore.getHolidayDatesSync() } returns emptySet()

        val ctx = repository.getCurrentContext(clock)

        assertEquals(DayOfWeek.TUESDAY, ctx.dayOfWeek)
        assertEquals(DayPeriod.TARDE, ctx.dayPeriod)
        assertFalse(ctx.esFinDeSemana)
        assertFalse(ctx.esFestivo)
    }

    @Test
    fun `getCurrentContext detecta fin de semana`() {
        // 2026-08-29 10:00:00 (sábado)
        val instant = LocalDateTime.of(2026, 8, 29, 10, 0)
            .atZone(ZoneId.systemDefault()).toInstant()
        val clock = Clock.fixed(instant, ZoneId.systemDefault())

        every { holidayStore.getHolidayDatesSync() } returns emptySet()

        val ctx = repository.getCurrentContext(clock)

        assertEquals(DayOfWeek.SATURDAY, ctx.dayOfWeek)
        assertTrue(ctx.esFinDeSemana)
    }

    @Test
    fun `getCurrentContext detecta festivo`() {
        val holiday = LocalDate.of(2026, 12, 25)
        val instant = LocalDateTime.of(2026, 12, 25, 12, 0)
            .atZone(ZoneId.systemDefault()).toInstant()
        val clock = Clock.fixed(instant, ZoneId.systemDefault())

        every { holidayStore.getHolidayDatesSync() } returns setOf(holiday)

        val ctx = repository.getCurrentContext(clock)

        assertTrue(ctx.esFestivo)
        assertEquals(DayOfWeek.FRIDAY, ctx.dayOfWeek)
    }

    @Test
    fun `getCurrentContext con multiples festivos`() {
        val holidays = setOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 25),
            LocalDate.of(2026, 3, 29)
        )
        val instant = LocalDateTime.of(2026, 3, 29, 8, 0)
            .atZone(ZoneId.systemDefault()).toInstant()
        val clock = Clock.fixed(instant, ZoneId.systemDefault())

        every { holidayStore.getHolidayDatesSync() } returns holidays

        val ctx = repository.getCurrentContext(clock)

        assertTrue(ctx.esFestivo)
        assertTrue(ctx.esFinDeSemana) // 2026-03-29 es sábado
    }

    @Test
    fun `getCurrentContext fecha no festiva en conjunto con festivos`() {
        val holidays = setOf(LocalDate.of(2026, 12, 25))
        val instant = LocalDateTime.of(2026, 12, 24, 12, 0)
            .atZone(ZoneId.systemDefault()).toInstant()
        val clock = Clock.fixed(instant, ZoneId.systemDefault())

        every { holidayStore.getHolidayDatesSync() } returns holidays

        val ctx = repository.getCurrentContext(clock)

        assertFalse(ctx.esFestivo)
    }

    @Test
    fun `getHolidayDates delega en HolidayStore`() = runTest {
        val expected = setOf(LocalDate.of(2026, 12, 25), LocalDate.of(2026, 1, 1))
        coEvery { holidayStore.getHolidayDates() } returns expected

        val result = repository.getHolidayDates()

        assertEquals(expected, result)
        coVerify(exactly = 1) { holidayStore.getHolidayDates() }
    }

    @Test
    fun `saveHolidayDate delega en HolidayStore`() = runTest {
        val date = LocalDate.of(2026, 12, 25)
        coEvery { holidayStore.saveHolidayDate(date) } returns Unit

        repository.saveHolidayDate(date)

        coVerify(exactly = 1) { holidayStore.saveHolidayDate(date) }
    }

    @Test
    fun `removeHolidayDate delega en HolidayStore`() = runTest {
        val date = LocalDate.of(2026, 12, 25)
        coEvery { holidayStore.removeHolidayDate(date) } returns Unit

        repository.removeHolidayDate(date)

        coVerify(exactly = 1) { holidayStore.removeHolidayDate(date) }
    }

    @Test
    fun `getCurrentContext retorna dateTime correcto`() {
        val instant = LocalDateTime.of(2026, 8, 25, 15, 30, 45)
            .atZone(ZoneId.systemDefault()).toInstant()
        val clock = Clock.fixed(instant, ZoneId.systemDefault())

        every { holidayStore.getHolidayDatesSync() } returns emptySet()

        val ctx = repository.getCurrentContext(clock)

        val expectedNow = LocalDateTime.now(clock)
        assertEquals(expectedNow, ctx.dateTime)
    }
}
