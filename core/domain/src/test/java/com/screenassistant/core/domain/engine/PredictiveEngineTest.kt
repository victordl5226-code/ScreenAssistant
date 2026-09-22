package com.screenassistant.core.domain.engine

import com.screenassistant.core.domain.model.TemporalContext
import com.screenassistant.core.domain.model.proactive.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.*
import java.util.UUID

class PredictiveEngineTest {

    private lateinit var engine: PredictiveEngine

    @Before
    fun setup() {
        engine = PredictiveEngine()
    }

    private fun createContext(dateTime: LocalDateTime): AggregatedContext {
        val temporal = TemporalContext.now(now = dateTime)
        return AggregatedContext.withDefaults(temporal)
    }

    private fun testPattern(
        action: String = "SetWifi",
        dayOfWeek: DayOfWeek = DayOfWeek.SATURDAY,
        hourStart: Int = 14,
        hourEnd: Int = 19,
        location: LocationType = LocationType.HOME,
        frequency: Int = 5,
        confidence: Float = 0.74f
    ) = UserPattern(
        id = UUID.randomUUID().toString(),
        action = action,
        context = PatternContext(
            dayOfWeek = dayOfWeek,
            hourStart = hourStart,
            hourEnd = hourEnd,
            location = location
        ),
        frequency = frequency,
        lastSeen = Instant.now(),
        confidence = confidence
    )

    // ── generateSuggestions ──

    @Test
    fun `generateSuggestions returns matching patterns`() {
        // 2026-09-20 es domingo, hora 14:30 (dentro del rango 14-19)
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))
        val pattern = testPattern(
            dayOfWeek = DayOfWeek.SUNDAY,
            hourStart = 14,
            hourEnd = 19,
            confidence = 0.74f
        )

        val suggestions = engine.generateSuggestions(context, listOf(pattern))

        assertEquals(1, suggestions.size)
        assertEquals("pattern:${pattern.id}", suggestions[0].source)
        assertEquals("Acción recurrente detectada", suggestions[0].title)
    }

    @Test
    fun `generateSuggestions returns empty when no match`() {
        // 2026-09-20 es sábado, pero el patrón es para lunes 6-11
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))
        val pattern = testPattern(
            dayOfWeek = DayOfWeek.MONDAY,
            hourStart = 6,
            hourEnd = 11,
            confidence = 0.74f
        )

        val suggestions = engine.generateSuggestions(context, listOf(pattern))

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `generateSuggestions returns empty when no patterns`() {
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))

        val suggestions = engine.generateSuggestions(context, emptyList())

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `generateSuggestions filters by hour range`() {
        // Hora 14:30 está fuera del rango 6-11
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))
        val pattern = testPattern(
            dayOfWeek = DayOfWeek.SUNDAY,
            hourStart = 6,
            hourEnd = 11,
            confidence = 0.74f
        )

        val suggestions = engine.generateSuggestions(context, listOf(pattern))

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `generateSuggestions matches hour at boundary start`() {
        // Hora 14:00 = horaStart = 14 → debe coincidir
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 0))
        val pattern = testPattern(
            dayOfWeek = DayOfWeek.SUNDAY,
            hourStart = 14,
            hourEnd = 19
        )

        val suggestions = engine.generateSuggestions(context, listOf(pattern))

        assertEquals(1, suggestions.size)
    }

    @Test
    fun `generateSuggestions matches hour at boundary end`() {
        // Hora 19:00 = hourEnd = 19 → debe coincidir
        val context = createContext(LocalDateTime.of(2026, 9, 20, 19, 0))
        val pattern = testPattern(
            dayOfWeek = DayOfWeek.SUNDAY,
            hourStart = 14,
            hourEnd = 19
        )

        val suggestions = engine.generateSuggestions(context, listOf(pattern))

        assertEquals(1, suggestions.size)
    }

    @Test
    fun `generateSuggestions excludes pattern with wrong day`() {
        // 2026-09-20 es domingo, pero el patrón es lunes
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))
        val pattern = testPattern(
            dayOfWeek = DayOfWeek.MONDAY,
            hourStart = 14,
            hourEnd = 19
        )

        val suggestions = engine.generateSuggestions(context, listOf(pattern))

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `generateSuggestions sorts by confidence descending`() {
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))
        val lowConfidence = testPattern(
            action = "low_action",
            dayOfWeek = DayOfWeek.SUNDAY,
            confidence = 0.3f
        )
        val highConfidence = testPattern(
            action = "high_action",
            dayOfWeek = DayOfWeek.SUNDAY,
            confidence = 0.95f
        )
        val midConfidence = testPattern(
            action = "mid_action",
            dayOfWeek = DayOfWeek.SUNDAY,
            confidence = 0.6f
        )

        val suggestions = engine.generateSuggestions(
            context,
            listOf(lowConfidence, highConfidence, midConfidence)
        )

        assertEquals(3, suggestions.size)
        // Las acciones en source son "pattern:<id>" donde id es UUID, así que verificamos el orden por acción
        assertEquals("pattern:${highConfidence.id}", suggestions[0].source)
        assertEquals("pattern:${midConfidence.id}", suggestions[1].source)
        assertEquals("pattern:${lowConfidence.id}", suggestions[2].source)
    }

    @Test
    fun `generateSuggestions limits to MAX_SUGGESTIONS`() {
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))
        val patterns = (1..10).map { i ->
            testPattern(
                action = "action_$i",
                dayOfWeek = DayOfWeek.SUNDAY,
                confidence = 0.1f + 0.09f * i
            )
        }

        val suggestions = engine.generateSuggestions(context, patterns)

        assertEquals(PredictiveEngine.MAX_SUGGESTIONS, suggestions.size)
    }

    @Test
    fun `generateSuggestions builds correct message`() {
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))
        val pattern = testPattern(
            dayOfWeek = DayOfWeek.SUNDAY,
            hourStart = 14,
            hourEnd = 19
        )

        val suggestions = engine.generateSuggestions(context, listOf(pattern))

        assertEquals(1, suggestions.size)
        val message = suggestions[0].message
        assertTrue(message.contains("sunday"))
        assertTrue(message.contains("14:00"))
        assertTrue(message.contains("19:00"))
        assertTrue(message.contains("¿Quieres que la ejecute ahora?"))
    }

    @Test
    fun `generateSuggestions builds SuggestSystemAction`() {
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))
        val pattern = testPattern(action = "SetWifi", dayOfWeek = DayOfWeek.SUNDAY)

        val suggestions = engine.generateSuggestions(context, listOf(pattern))

        assertEquals(1, suggestions.size)
        val action = suggestions[0].action
        assertTrue(action is ProactiveAction.SuggestSystemAction)
        assertEquals("SetWifi", (action as ProactiveAction.SuggestSystemAction).actionId)
    }

    @Test
    fun `generateSuggestions uses MEDIUM priority`() {
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))
        val pattern = testPattern(dayOfWeek = DayOfWeek.SUNDAY)

        val suggestions = engine.generateSuggestions(context, listOf(pattern))

        assertEquals(1, suggestions.size)
        assertEquals(ProactivePriority.MEDIUM, suggestions[0].priority)
    }

    @Test
    fun `generateSuggestions with single pattern matching`() {
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))
        val pattern = testPattern(
            dayOfWeek = DayOfWeek.SUNDAY,
            hourStart = 14,
            hourEnd = 19
        )

        val suggestions = engine.generateSuggestions(context, listOf(pattern))

        assertEquals(1, suggestions.size)
    }

    @Test
    fun `generateSuggestions mixes matching and non-matching patterns`() {
        val context = createContext(LocalDateTime.of(2026, 9, 20, 14, 30))
        val matching = testPattern(
            action = "match_action",
            dayOfWeek = DayOfWeek.SUNDAY,
            hourStart = 14,
            hourEnd = 19,
            confidence = 0.8f
        )
        val nonMatching = testPattern(
            action = "no_match_action",
            dayOfWeek = DayOfWeek.MONDAY,
            hourStart = 6,
            hourEnd = 11,
            confidence = 0.9f
        )

        val suggestions = engine.generateSuggestions(context, listOf(matching, nonMatching))

        assertEquals(1, suggestions.size)
        assertEquals("pattern:${matching.id}", suggestions[0].source)
    }
}
