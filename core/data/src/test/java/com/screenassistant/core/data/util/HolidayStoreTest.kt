package com.screenassistant.core.data.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Tests de HolidayStore.
 *
 * NOTA: DataStore no es compatible con Robolectric (IOException en SingleProcessDataStore).
 * Los tests de integración con DataStore real se ejecutan en androidTest.
 * Aquí se testea la lógica de parseo de fechas vía parseDates() (internal @VisibleForTesting).
 *
 * Los tests de integración completa del repositorio (CRUD con HolidayStore)
 * se cubren en TemporalRepositoryImplTest usando mocks de HolidayStore.
 */
@RunWith(RobolectricTestRunner::class)
class HolidayStoreTest {

    private lateinit var context: Context
    private lateinit var store: HolidayStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = HolidayStore(context)
    }

    @Test
    fun `getHolidayDatesSync retorna conjunto vacio cuando no hay datos`() {
        val result = store.getHolidayDatesSync()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getHolidayDates retorna conjunto vacio cuando no hay datos`() {
        val result = kotlinx.coroutines.runBlocking {
            store.getHolidayDates()
        }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `HolidayStore se instancia correctamente con contexto`() {
        val newStore = HolidayStore(context)
        val result = newStore.getHolidayDatesSync()
        assertTrue(result.isEmpty())
    }

    // ── parseDates() tests ──────────────────────────────────────────────

    @Test
    fun `parseDates con fechas validas retorna todas`() {
        val raw = "2026-12-25,2026-01-01,2026-07-04"
        val result = store.parseDates(raw)
        assertEquals(
            setOf(
                LocalDate.of(2026, 12, 25),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 7, 4)
            ),
            result
        )
    }

    @Test
    fun `parseDates con string vacio retorna conjunto vacio`() {
        val result = store.parseDates("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseDates con null retorna conjunto vacio`() {
        val result = store.parseDates(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseDates con espacios en blanco los ignora`() {
        val raw = "  ,  ,  "
        val result = store.parseDates(raw)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseDates con fechas invalidas las ignora`() {
        val raw = "not-a-date,2026-01-01,also-bad"
        val result = store.parseDates(raw)
        assertEquals(setOf(LocalDate.of(2026, 1, 1)), result)
    }

    @Test
    fun `parseDates con mixto de validas e invalidas retorna solo validas`() {
        val raw = "2026-12-25,,invalid,2026-07-04, "
        val result = store.parseDates(raw)
        assertEquals(
            setOf(
                LocalDate.of(2026, 12, 25),
                LocalDate.of(2026, 7, 4)
            ),
            result
        )
    }

    @Test
    fun `parseDates con una sola fecha retorna conjunto de uno`() {
        val raw = "2026-03-17"
        val result = store.parseDates(raw)
        assertEquals(setOf(LocalDate.of(2026, 3, 17)), result)
    }

    @Test
    fun `parseDates no duplica fechas repetidas`() {
        val raw = "2026-12-25,2026-12-25,2026-12-25"
        val result = store.parseDates(raw)
        assertEquals(setOf(LocalDate.of(2026, 12, 25)), result)
    }
}
