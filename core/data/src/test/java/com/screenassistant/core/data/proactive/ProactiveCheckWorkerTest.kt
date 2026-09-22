package com.screenassistant.core.data.proactive

import android.content.Context
import androidx.work.WorkerParameters
import com.screenassistant.core.domain.engine.ProactiveEngine
import com.screenassistant.core.domain.engine.PredictiveEngine
import com.screenassistant.core.domain.model.DayPeriod
import com.screenassistant.core.domain.model.TemporalContext
import com.screenassistant.core.domain.model.proactive.AggregatedContext
import com.screenassistant.core.domain.model.proactive.BatteryInfo
import com.screenassistant.core.domain.model.proactive.ConnectivityInfo
import com.screenassistant.core.domain.model.proactive.LocationType
import com.screenassistant.core.domain.model.proactive.ProactiveAction
import com.screenassistant.core.domain.model.proactive.ProactivePriority
import com.screenassistant.core.domain.model.proactive.ProactiveRule
import com.screenassistant.core.domain.model.proactive.ProactiveSuggestion
import com.screenassistant.core.domain.model.proactive.RuleCondition
import com.screenassistant.core.domain.model.proactive.ScreenInfo
import com.screenassistant.core.domain.model.proactive.UserPattern
import com.screenassistant.core.domain.repository.ContextAggregatorRepository
import com.screenassistant.core.domain.repository.ProactiveRuleRepository
import com.screenassistant.core.domain.repository.UserPatternRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime

/**
 * Tests de integración para [ProactiveCheckWorker].
 *
 * Verifica que el worker evalúa correctamente reglas Y patrones predictivos,
 * combinando ambos tipos de sugerencias y entregándolas al [ProactiveSuggestionManager].
 *
 * Se llama directamente a [ProactiveCheckWorker.doWork] (método público) en
 * vez de [startWork] para evitar la infraestructura de WorkManager que
 * requiere un WorkerContext con backgroundDispatcher real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProactiveCheckWorkerTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var proactiveEngine: ProactiveEngine
    private lateinit var predictiveEngine: PredictiveEngine
    private lateinit var ruleRepository: ProactiveRuleRepository
    private lateinit var contextAggregator: ContextAggregatorRepository
    private lateinit var patternRepository: UserPatternRepository
    private lateinit var suggestionManager: ProactiveSuggestionManager
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        proactiveEngine = mockk(relaxed = true)
        predictiveEngine = mockk(relaxed = true)
        ruleRepository = mockk(relaxed = true)
        contextAggregator = mockk(relaxed = true)
        patternRepository = mockk(relaxed = true)
        suggestionManager = mockk(relaxed = true)

        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun createWorker(): ProactiveCheckWorker {
        return ProactiveCheckWorker(
            context,
            workerParams,
            proactiveEngine,
            predictiveEngine,
            ruleRepository,
            contextAggregator,
            patternRepository,
            suggestionManager
        )
    }

    private fun createTestContext(): AggregatedContext {
        val temporal = TemporalContext(
            dateTime = LocalDateTime.of(2026, 1, 15, 10, 30),
            dayOfWeek = DayOfWeek.WEDNESDAY,
            dayPeriod = DayPeriod.MANANA,
            esFinDeSemana = false,
            esFestivo = false
        )
        return AggregatedContext(
            temporal = temporal,
            battery = BatteryInfo(level = 80, isCharging = false),
            upcomingEvents = emptyList(),
            screen = ScreenInfo(packageName = null, text = ""),
            connectivity = ConnectivityInfo(wifi = true, mobile = false),
            location = LocationType.HOME
        )
    }

    private fun createTestRule(): ProactiveRule {
        return ProactiveRule.simple(
            name = "Test Rule",
            condition = RuleCondition.BatteryBelow(threshold = 50),
            action = ProactiveAction.Speak(message = "Battery low")
        )
    }

    private fun createTestPattern(): UserPattern {
        return UserPattern(
            id = "pattern_1",
            action = "OpenApp",
            context = com.screenassistant.core.domain.model.proactive.PatternContext(
                dayOfWeek = DayOfWeek.WEDNESDAY,
                hourStart = 9,
                hourEnd = 12,
                location = LocationType.HOME
            ),
            frequency = 10,
            lastSeen = Instant.now(),
            confidence = 0.85f
        )
    }

    private fun createRuleSuggestion(): ProactiveSuggestion {
        return ProactiveSuggestion(
            id = "rule_suggestion",
            title = "Rule Suggestion",
            message = "Rule says something",
            action = ProactiveAction.Speak(message = "Rule says something"),
            priority = ProactivePriority.HIGH,
            source = "rule:rule_1"
        )
    }

    private fun createPatternSuggestion(): ProactiveSuggestion {
        return ProactiveSuggestion(
            id = "pattern_suggestion",
            title = "Pattern Suggestion",
            message = "Pattern detected",
            action = ProactiveAction.SuggestSystemAction(actionId = "OpenApp"),
            priority = ProactivePriority.MEDIUM,
            source = "pattern:pattern_1"
        )
    }

    /**
     * Verifica que el resultado es success sin comparar objetos
     * (evita problemas de serialización entre distintas instancias de Result).
     */
    private fun assertSuccess(result: Any) {
        assertTrue(
            "Se esperaba Result.success(), pero fue: ${result::class.simpleName}",
            result.toString().contains("Success", ignoreCase = true)
        )
    }

    /**
     * Verifica que el resultado es retry sin comparar objetos.
     */
    private fun assertRetry(result: Any) {
        assertTrue(
            "Se esperaba Result.retry(), pero fue: ${result::class.simpleName}",
            result.toString().contains("Retry", ignoreCase = true)
        )
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Test
    fun `doWork llama a predictiveEngine con patrones establecidos`() = runTest {
        val testContext = createTestContext()
        val pattern = createTestPattern()

        coEvery { ruleRepository.getEnabledRules() } returns emptyList()
        coEvery { contextAggregator.getAggregatedContext() } returns testContext
        coEvery { patternRepository.getFrequentPatterns(minFrequency = 5) } returns listOf(pattern)
        every { predictiveEngine.generateSuggestions(any(), any()) } returns emptyList()

        val worker = createWorker()
        val result = worker.doWork()

        advanceUntilIdle()

        assertSuccess(result)
        verify(exactly = 1) {
            predictiveEngine.generateSuggestions(
                testContext,
                match { it.size == 1 && it.first().isEstablished }
            )
        }
    }

    @Test
    fun `doWork combina sugerencias de reglas + patrones`() = runTest {
        val testContext = createTestContext()
        val rule = createTestRule()
        val ruleSuggestion = createRuleSuggestion()
        val patternSuggestion = createPatternSuggestion()

        coEvery { ruleRepository.getEnabledRules() } returns listOf(rule)
        coEvery { contextAggregator.getAggregatedContext() } returns testContext
        coEvery { patternRepository.getFrequentPatterns(minFrequency = 5) } returns emptyList()
        every {
            proactiveEngine.evaluate(any<List<ProactiveRule>>(), any<AggregatedContext>())
        } returns listOf(ruleSuggestion)
        every { predictiveEngine.generateSuggestions(any(), any()) } returns listOf(patternSuggestion)

        val worker = createWorker()
        val result = worker.doWork()

        advanceUntilIdle()

        assertSuccess(result)
        coVerify(exactly = 1) {
            suggestionManager.submitSuggestions(match { suggestions ->
                suggestions.size == 2 &&
                    suggestions.any { it.source == "rule:rule_1" } &&
                    suggestions.any { it.source == "pattern:pattern_1" }
            })
        }
    }

    @Test
    fun `doWork retorna success cuando no hay sugerencias`() = runTest {
        coEvery { ruleRepository.getEnabledRules() } returns emptyList()
        coEvery { contextAggregator.getAggregatedContext() } returns createTestContext()
        coEvery { patternRepository.getFrequentPatterns(minFrequency = 5) } returns emptyList()

        val worker = createWorker()
        val result = worker.doWork()

        advanceUntilIdle()

        assertSuccess(result)
        coVerify(exactly = 0) { suggestionManager.submitSuggestions(any()) }
    }

    @Test
    fun `doWork retorna retry en caso de error`() = runTest {
        coEvery { ruleRepository.getEnabledRules() } throws RuntimeException("Network error")

        val worker = createWorker()
        val result = worker.doWork()

        advanceUntilIdle()

        assertRetry(result)
    }
}
