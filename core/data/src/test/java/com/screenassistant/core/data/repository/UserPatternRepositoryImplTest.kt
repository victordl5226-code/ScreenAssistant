package com.screenassistant.core.data.repository

import com.screenassistant.core.data.local.UserPatternDao
import com.screenassistant.core.data.local.UserPatternEntity
import com.screenassistant.core.domain.model.proactive.AggregatedContext
import com.screenassistant.core.domain.model.proactive.LocationType
import com.screenassistant.core.domain.model.proactive.PatternContext
import com.screenassistant.core.domain.model.proactive.UserPattern
import com.screenassistant.core.domain.model.TemporalContext
import com.screenassistant.core.domain.model.DayPeriod
import com.screenassistant.core.domain.repository.ContextAggregatorRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class UserPatternRepositoryImplTest {

    private lateinit var dao: UserPatternDao
    private lateinit var contextAggregator: ContextAggregatorRepository
    private lateinit var clock: Clock
    private lateinit var repository: UserPatternRepositoryImpl

    private fun testPatternContext(
        dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        hourStart: Int = 9,
        hourEnd: Int = 17,
        location: LocationType = LocationType.HOME
    ) = PatternContext(dayOfWeek = dayOfWeek, hourStart = hourStart, hourEnd = hourEnd, location = location)

    private fun testEntity(
        id: String = "test-id",
        action: String = "test_action",
        dayOfWeek: String = "MONDAY",
        hourStart: Int = 9,
        hourEnd: Int = 17,
        locationType: String = "HOME",
        frequency: Int = 1,
        confidence: Float = 0.1f
    ) = UserPatternEntity(
        id = id, action = action, dayOfWeek = dayOfWeek, hourStart = hourStart,
        hourEnd = hourEnd, locationType = locationType, frequency = frequency,
        lastSeen = System.currentTimeMillis(), confidence = confidence
    )

    private fun testAggregatedContext(
        dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        hour: Int = 14,
        location: LocationType = LocationType.HOME
    ): AggregatedContext {
        val temporal = TemporalContext.now(
            now = LocalDateTime.of(2026, 9, 20, hour, 30)
        )
        return AggregatedContext.withDefaults(temporal).copy(location = location)
    }

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        contextAggregator = mockk(relaxed = true)
        clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC)
        repository = UserPatternRepositoryImpl(dao, contextAggregator, clock)
    }

    @Test
    fun `getAllPatterns mapea entities a dominio`() = runTest {
        val entities = listOf(testEntity(id = "p1", action = "action1"))
        every { dao.getAllPatterns() } returns flowOf(entities)

        repository.getAllPatterns().collect { patterns ->
            assertEquals(1, patterns.size)
            assertEquals("p1", patterns[0].id)
            assertEquals("action1", patterns[0].action)
        }
    }

    @Test
    fun `getAllPatterns retorna lista vacia`() = runTest {
        every { dao.getAllPatterns() } returns flowOf(emptyList())

        repository.getAllPatterns().collect { patterns ->
            assertTrue(patterns.isEmpty())
        }
    }

    @Test
    fun `getPattern retorna patron existente`() = runTest {
        coEvery { dao.getPattern("p1") } returns testEntity(id = "p1")

        val result = repository.getPattern("p1")

        assertEquals("p1", result?.id)
    }

    @Test
    fun `getPattern retorna null cuando no existe`() = runTest {
        coEvery { dao.getPattern("no-existe") } returns null

        val result = repository.getPattern("no-existe")

        assertNull(result)
    }

    @Test
    fun `recordOccurrence crea patron nuevo cuando no existe`() = runTest {
        coEvery { dao.findByActionAndContext(any(), any(), any(), any(), any()) } returns null
        coEvery { dao.insertPattern(any()) } returns Unit

        repository.recordOccurrence("new_action", testPatternContext())

        coVerify(exactly = 1) {
            dao.insertPattern(withArg { entity ->
                assertEquals("new_action", entity.action)
                assertEquals(1, entity.frequency)
            })
        }
    }

    @Test
    fun `recordOccurrence incrementa frecuencia de patron existente`() = runTest {
        val existing = testEntity(id = "existing", action = "action1", frequency = 3, confidence = 0.5f)
        coEvery {
            dao.findByActionAndContext("action1", "MONDAY", 9, 17, "HOME")
        } returns existing
        coEvery { dao.insertPattern(any()) } returns Unit

        repository.recordOccurrence("action1", testPatternContext())

        coVerify(exactly = 1) {
            dao.insertPattern(withArg { entity ->
                assertEquals(4, entity.frequency)
                assertTrue(entity.confidence > 0.5f)
            })
        }
    }

    @Test
    fun `recordOccurrence crea nuevo cuando accion no coincide`() = runTest {
        coEvery {
            dao.findByActionAndContext("new_action", "MONDAY", 9, 17, "HOME")
        } returns null
        coEvery { dao.insertPattern(any()) } returns Unit

        repository.recordOccurrence("new_action", testPatternContext())

        coVerify(exactly = 1) {
            dao.insertPattern(withArg { entity ->
                assertEquals("new_action", entity.action)
                assertEquals(1, entity.frequency)
            })
        }
    }

    @Test
    fun `recordOccurrence crea nuevo cuando dia no coincide`() = runTest {
        coEvery {
            dao.findByActionAndContext("action1", "MONDAY", 9, 17, "HOME")
        } returns null
        coEvery { dao.insertPattern(any()) } returns Unit

        repository.recordOccurrence("action1", testPatternContext(dayOfWeek = DayOfWeek.MONDAY))

        coVerify(exactly = 1) {
            dao.insertPattern(withArg { entity ->
                assertEquals(1, entity.frequency)
            })
        }
    }

    @Test
    fun `getFrequentPatterns delega en dao`() = runTest {
        val entities = listOf(testEntity(id = "freq1", frequency = 5))
        coEvery { dao.getFrequentPatterns(3) } returns entities

        val result = repository.getFrequentPatterns(3)

        assertEquals(1, result.size)
        assertEquals("freq1", result[0].id)
    }

    @Test
    fun `clearOldPatterns usa el clock inyectado y calcula cutoff correcto`() = runTest {
        coEvery { dao.deleteOldPatterns(any()) } returns Unit

        repository.clearOldPatterns(30)

        coVerify(exactly = 1) {
            dao.deleteOldPatterns(withArg { cutoff ->
                val now = clock.millis()
                val expected = now - 30L * 86_400_000L
                assertEquals(expected, cutoff)
            })
        }
    }

    @Test
    fun `deletePattern delega en dao`() = runTest {
        coEvery { dao.deletePattern("id-to-delete") } returns Unit

        repository.deletePattern("id-to-delete")

        coVerify(exactly = 1) { dao.deletePattern("id-to-delete") }
    }

    @Test
    fun `trackAction obtiene contexto y registra ocurrencia`() = runTest {
        val context = testAggregatedContext(dayOfWeek = DayOfWeek.SUNDAY, hour = 14, location = LocationType.HOME)
        coEvery { contextAggregator.getAggregatedContext() } returns context
        coEvery { dao.findByActionAndContext(any(), any(), any(), any(), any()) } returns null
        coEvery { dao.insertPattern(any()) } returns Unit

        repository.trackAction("SetWifi")

        coVerify(exactly = 1) {
            dao.insertPattern(withArg { entity ->
                assertEquals("SetWifi", entity.action)
                assertEquals("SUNDAY", entity.dayOfWeek)
                assertEquals(14, entity.hourStart)
                assertEquals(19, entity.hourEnd)
                assertEquals("HOME", entity.locationType)
                assertEquals(1, entity.frequency)
            })
        }
    }

    @Test
    fun `trackAction incrementa frecuencia si patron ya existe`() = runTest {
        val context = testAggregatedContext(dayOfWeek = DayOfWeek.SUNDAY, hour = 14, location = LocationType.HOME)
        coEvery { contextAggregator.getAggregatedContext() } returns context
        val existing = testEntity(
            id = "existing", action = "SetWifi",
            dayOfWeek = "SUNDAY", hourStart = 14, hourEnd = 19,
            locationType = "HOME", frequency = 2, confidence = 0.40f
        )
        coEvery {
            dao.findByActionAndContext("SetWifi", "SUNDAY", 14, 19, "HOME")
        } returns existing
        coEvery { dao.insertPattern(any()) } returns Unit

        repository.trackAction("SetWifi")

        coVerify(exactly = 1) {
            dao.insertPattern(withArg { entity ->
                assertEquals("SetWifi", entity.action)
                assertEquals(3, entity.frequency)
                assertTrue(entity.confidence > 0.40f)
            })
        }
    }

    @Test
    fun `trackAction pasa screenApp cuando se proporciona`() = runTest {
        val context = testAggregatedContext(dayOfWeek = DayOfWeek.MONDAY, hour = 9, location = LocationType.HOME)
        coEvery { contextAggregator.getAggregatedContext() } returns context
        coEvery { dao.findByActionAndContext(any(), any(), any(), any(), any()) } returns null
        coEvery { dao.insertPattern(any()) } returns Unit

        repository.trackAction("OpenApp", screenApp = "com.android.chrome")

        coVerify(exactly = 1) {
            dao.insertPattern(withArg { entity ->
                assertEquals("OpenApp", entity.action)
                assertEquals("com.android.chrome", entity.screenApp)
            })
        }
    }

    @Test
    fun `calculateConfidence crece con frecuencia`() = runTest {
        coEvery { dao.findByActionAndContext(any(), any(), any(), any(), any()) } returns null
        coEvery { dao.insertPattern(any()) } returns Unit

        val confidences = mutableListOf<Float>()
        for (i in 1..5) {
            coEvery { dao.findByActionAndContext(any(), any(), any(), any(), any()) } returns null
            repository.recordOccurrence("act", testPatternContext())
            // La confianza crece según la fórmula del repositorio
            val expectedConfidence = 0.1f + 0.9f * (1f - Math.exp(-i / 5.0)).toFloat()
            confidences.add(expectedConfidence)
        }

        assertTrue(confidences.last() > confidences.first())
    }
}
