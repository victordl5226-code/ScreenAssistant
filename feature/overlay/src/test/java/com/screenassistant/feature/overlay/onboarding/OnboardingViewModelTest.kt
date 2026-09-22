package com.screenassistant.feature.overlay.onboarding

import com.screenassistant.core.domain.model.personality.PersonalityConfig
import com.screenassistant.core.domain.repository.PersonalityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var personalityRepository: PersonalityRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        personalityRepository = mockk(relaxed = true)
        coEvery { personalityRepository.isOnboardingCompleted() } returns false
        coEvery { personalityRepository.completeOnboarding(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): OnboardingViewModel {
        return OnboardingViewModel(
            personalityRepository = personalityRepository,
            ioDispatcher = testDispatcher
        )
    }

    // ── Estado inicial ──────────────────────────────────────────────────

    @Test
    fun `init estado inicial tiene nameInput vacio y isNameValid true`() {
        viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertEquals("", state.nameInput)
        assertTrue(state.isNameValid)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertFalse(state.isOnboardingCompleted)
    }

    @Test
    fun `init consulta isOnboardingCompleted del repositorio`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { personalityRepository.isOnboardingCompleted() }
    }

    @Test
    fun `init cuando onboarding ya completado isOnboardingCompleted es true`() = runTest {
        coEvery { personalityRepository.isOnboardingCompleted() } returns true

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOnboardingCompleted)
    }

    @Test
    fun `init cuando onboarding no completado isOnboardingCompleted es false`() = runTest {
        coEvery { personalityRepository.isOnboardingCompleted() } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOnboardingCompleted)
    }

    // ── onNameChanged ───────────────────────────────────────────────────

    @Test
    fun `onNameChanged nombre valido actualiza nameInput y isNameValid true`() {
        viewModel = createViewModel()

        viewModel.onNameChanged("Friday")

        val state = viewModel.uiState.value
        assertEquals("Friday", state.nameInput)
        assertTrue(state.isNameValid)
        assertNull(state.errorMessage)
    }

    @Test
    fun `onNameChanged nombre vacio actualiza nameInput y isNameValid false`() {
        viewModel = createViewModel()

        viewModel.onNameChanged("")

        val state = viewModel.uiState.value
        assertEquals("", state.nameInput)
        assertFalse(state.isNameValid)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `onNameChanged nombre con caracteres invalidos es invalido`() {
        viewModel = createViewModel()

        viewModel.onNameChanged("Bot@2024!")

        assertFalse(viewModel.uiState.value.isNameValid)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onNameChanged nombre muy largo es invalido`() {
        viewModel = createViewModel()

        viewModel.onNameChanged("A".repeat(21))

        assertFalse(viewModel.uiState.value.isNameValid)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onNameChanged nombre J A R V I S es valido`() {
        viewModel = createViewModel()

        viewModel.onNameChanged("J.A.R.V.I.S.")

        assertTrue(viewModel.uiState.value.isNameValid)
    }

    @Test
    fun `onNameChanged con espacios al inicio hace trim antes de validar`() {
        viewModel = createViewModel()

        viewModel.onNameChanged("  Friday  ")

        assertTrue(viewModel.uiState.value.isNameValid)
        assertEquals("  Friday  ", viewModel.uiState.value.nameInput)
    }

    @Test
    fun `onNameChanged nombre solo espacios es invalido`() {
        viewModel = createViewModel()

        viewModel.onNameChanged("   ")

        assertFalse(viewModel.uiState.value.isNameValid)
    }

    @Test
    fun `onNameChanged nombre con guiones y puntos es valido`() {
        viewModel = createViewModel()

        viewModel.onNameChanged("Mi-Bot.A.I.")

        assertTrue(viewModel.uiState.value.isNameValid)
    }

    // ── completeOnboarding ──────────────────────────────────────────────

    @Test
    fun `completeOnboarding con nombre valido guarda config y completa`() = runTest {
        viewModel = createViewModel()
        viewModel.onNameChanged("Friday")
        advanceUntilIdle()

        viewModel.completeOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.completeOnboarding(
                withArg { config ->
                    assertEquals("Friday", config.name)
                    assertEquals(
                        PersonalityConfig.DEFAULT_JARVIS.traits,
                        config.traits
                    )
                    assertEquals(
                        PersonalityConfig.DEFAULT_JARVIS.language,
                        config.language
                    )
                }
            )
        }
        assertTrue(viewModel.uiState.value.isOnboardingCompleted)
    }

    @Test
    fun `completeOnboarding con nombre en blanco usa DEFAULT_NAME`() = runTest {
        viewModel = createViewModel()
        // nameInput queda en "" (default)
        advanceUntilIdle()

        viewModel.completeOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.completeOnboarding(
                withArg { config ->
                    assertEquals("J.A.R.V.I.S.", config.name)
                }
            )
        }
    }

    @Test
    fun `completeOnboarding con solo espacios usa DEFAULT_NAME`() = runTest {
        viewModel = createViewModel()
        viewModel.onNameChanged("   ")
        advanceUntilIdle()

        viewModel.completeOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.completeOnboarding(
                withArg { config ->
                    assertEquals("J.A.R.V.I.S.", config.name)
                }
            )
        }
    }

    @Test
    fun `completeOnboarding con nombre invalido no guarda`() = runTest {
        viewModel = createViewModel()
        viewModel.onNameChanged("Bot@!")
        advanceUntilIdle()

        viewModel.completeOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 0) { personalityRepository.completeOnboarding(any()) }
    }

    @Test
    fun `completeOnboarding muestra isLoading durante guardado`() = runTest {
        viewModel = createViewModel()
        viewModel.onNameChanged("Friday")
        advanceUntilIdle()

        viewModel.completeOnboarding()
        // No advanceUntilIdle aqui para capturar estado intermedio
        // El isLoading se pone true antes del launch

        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `completeOnboarding isLoading es false despues de guardar`() = runTest {
        viewModel = createViewModel()
        viewModel.onNameChanged("Friday")
        advanceUntilIdle()

        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `completeOnboarding propaga nombre con puntos`() = runTest {
        viewModel = createViewModel()
        viewModel.onNameChanged("J.A.R.V.I.S.")
        advanceUntilIdle()

        viewModel.completeOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.completeOnboarding(
                withArg { config ->
                    assertEquals("J.A.R.V.I.S.", config.name)
                }
            )
        }
    }

    // ── skipOnboarding ─────────────────────────────────────────────────

    @Test
    fun `skipOnboarding guarda DEFAULT_JARVIS config`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.skipOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.completeOnboarding(PersonalityConfig.DEFAULT_JARVIS)
        }
    }

    @Test
    fun `skipOnboarding isOnboardingCompleted es true`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.skipOnboarding()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOnboardingCompleted)
    }

    @Test
    fun `skipOnboarding no cambia nameInput`() = runTest {
        viewModel = createViewModel()
        viewModel.onNameChanged("Friday")
        advanceUntilIdle()

        viewModel.skipOnboarding()
        advanceUntilIdle()

        assertEquals("Friday", viewModel.uiState.value.nameInput)
    }

    // ── DEFAULT_NAME ────────────────────────────────────────────────────

    @Test
    fun `DEFAULT_NAME es J A R V I S`() {
        assertEquals("J.A.R.V.I.S.", OnboardingViewModel.DEFAULT_NAME)
    }
}
