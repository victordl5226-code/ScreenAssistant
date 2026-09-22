package com.screenassistant.feature.overlay

import com.screenassistant.core.data.util.ProactivePreferences
import com.screenassistant.core.domain.model.proactive.ProactivePriority
import com.screenassistant.core.domain.model.proactive.ProactiveAction
import com.screenassistant.core.domain.model.proactive.ProactiveRule
import com.screenassistant.core.domain.model.proactive.RuleCondition
import com.screenassistant.core.domain.repository.ProactiveRuleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProactiveViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var preferences: ProactivePreferences
    private lateinit var ruleRepository: ProactiveRuleRepository
    private lateinit var viewModel: ProactiveViewModel

    private val isEnabledFlow = MutableStateFlow(true)
    private val checkIntervalFlow = MutableStateFlow(15L)
    private val rulesFlow = MutableStateFlow<List<ProactiveRule>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        preferences = mockk(relaxed = true)
        ruleRepository = mockk(relaxed = true)

        every { preferences.isEnabled } returns isEnabledFlow
        every { preferences.checkIntervalMinutes } returns checkIntervalFlow
        every { ruleRepository.getAllRules() } returns rulesFlow

        coEvery { preferences.setEnabled(any()) } returns Unit
        coEvery { preferences.setCheckInterval(any()) } returns Unit

        viewModel = ProactiveViewModel(
            preferences = preferences,
            ruleRepository = ruleRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default values`() = runTest {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEnabled)
        assertEquals(15L, state.checkIntervalMinutes)
        assertTrue(state.rules.isEmpty())
    }

    @Test
    fun `uiState reflects preferences isEnabled when false`() = runTest {
        isEnabledFlow.value = false

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEnabled)
    }

    @Test
    fun `uiState reflects preferences checkIntervalMinutes`() = runTest {
        checkIntervalFlow.value = 30L

        advanceUntilIdle()

        assertEquals(30L, viewModel.uiState.value.checkIntervalMinutes)
    }

    @Test
    fun `uiState reflects rules from repository`() = runTest {
        val rule = ProactiveRule(
            name = "Test Rule",
            conditions = listOf(RuleCondition.IsWeekend),
            action = ProactiveAction.Speak("Hello"),
            priority = ProactivePriority.HIGH
        )
        rulesFlow.value = listOf(rule)

        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.rules.size)
        assertEquals("Test Rule", viewModel.uiState.value.rules.first().name)
    }

    @Test
    fun `toggleProactive calls preferences setEnabled with true`() = runTest {
        viewModel.toggleProactive(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferences.setEnabled(true) }
    }

    @Test
    fun `toggleProactive calls preferences setEnabled with false`() = runTest {
        viewModel.toggleProactive(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferences.setEnabled(false) }
    }

    @Test
    fun `setCheckInterval calls preferences setCheckInterval`() = runTest {
        viewModel.setCheckInterval(30L)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferences.setCheckInterval(30L) }
    }

    @Test
    fun `setCheckInterval with minimum value`() = runTest {
        viewModel.setCheckInterval(1L)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferences.setCheckInterval(1L) }
    }

    @Test
    fun `multiple state changes are combined correctly`() = runTest {
        isEnabledFlow.value = false
        checkIntervalFlow.value = 60L
        val rule = ProactiveRule(
            name = "Rule A",
            conditions = listOf(RuleCondition.IsWeekend),
            action = ProactiveAction.Speak("Hi")
        )
        rulesFlow.value = listOf(rule)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEnabled)
        assertEquals(60L, state.checkIntervalMinutes)
        assertEquals(1, state.rules.size)
    }

    @Test
    fun `getAllRules is collected from repository`() = runTest {
        advanceUntilIdle()

        verify(exactly = 1) { ruleRepository.getAllRules() }
    }

    @Test
    fun `isEnabled is collected from preferences`() = runTest {
        advanceUntilIdle()

        verify(exactly = 1) { preferences.isEnabled }
    }

    @Test
    fun `checkIntervalMinutes is collected from preferences`() = runTest {
        advanceUntilIdle()

        verify(exactly = 1) { preferences.checkIntervalMinutes }
    }
}
