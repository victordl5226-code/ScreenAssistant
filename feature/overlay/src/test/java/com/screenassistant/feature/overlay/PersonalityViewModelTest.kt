package com.screenassistant.feature.overlay

import com.screenassistant.core.domain.model.personality.PersonalityConfig
import com.screenassistant.core.domain.model.personality.PersonalityTraits
import com.screenassistant.core.domain.repository.PersonalityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class PersonalityViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var personalityRepository: PersonalityRepository
    private lateinit var viewModel: PersonalityViewModel

    private val configFlow = MutableStateFlow(PersonalityConfig.DEFAULT_JARVIS)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        personalityRepository = mockk(relaxed = true)
        every { personalityRepository.getPersonalityConfig() } returns configFlow
        coEvery { personalityRepository.updatePersonalityConfig(any()) } returns Unit

        viewModel = PersonalityViewModel(personalityRepository = personalityRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default config and isLoading true`() {
        val state = viewModel.uiState.value
        assertEquals(PersonalityConfig.DEFAULT_JARVIS, state.config)
        assertTrue(state.isLoading)
    }

    @Test
    fun `after collecting config isLoading becomes false`() = runTest {
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(PersonalityConfig.DEFAULT_JARVIS, viewModel.uiState.value.config)
    }

    @Test
    fun `uiState reflects config changes from repository`() = runTest {
        val customConfig = PersonalityConfig(
            name = "CustomBot",
            traits = PersonalityTraits.NEUTRAL,
            language = "en"
        )
        configFlow.value = customConfig

        advanceUntilIdle()

        assertEquals("CustomBot", viewModel.uiState.value.config.name)
        assertEquals("en", viewModel.uiState.value.config.language)
    }

    @Test
    fun `updateTrait formality calls repository with updated config`() = runTest {
        advanceUntilIdle()

        viewModel.updateTrait("formality", 0.9f)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.updatePersonalityConfig(
                match { it.traits.formality == 0.9f }
            )
        }
    }

    @Test
    fun `updateTrait humor calls repository with updated config`() = runTest {
        advanceUntilIdle()

        viewModel.updateTrait("humor", 0.8f)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.updatePersonalityConfig(
                match { it.traits.humor == 0.8f }
            )
        }
    }

    @Test
    fun `updateTrait warmth calls repository with updated config`() = runTest {
        advanceUntilIdle()

        viewModel.updateTrait("warmth", 0.3f)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.updatePersonalityConfig(
                match { it.traits.warmth == 0.3f }
            )
        }
    }

    @Test
    fun `updateTrait verbosity calls repository with updated config`() = runTest {
        advanceUntilIdle()

        viewModel.updateTrait("verbosity", 0.7f)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.updatePersonalityConfig(
                match { it.traits.verbosity == 0.7f }
            )
        }
    }

    @Test
    fun `updateTrait sarcasm calls repository with updated config`() = runTest {
        advanceUntilIdle()

        viewModel.updateTrait("sarcasm", 0.5f)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.updatePersonalityConfig(
                match { it.traits.sarcasm == 0.5f }
            )
        }
    }

    @Test
    fun `updateTrait with unknown trait does not change traits`() = runTest {
        advanceUntilIdle()

        viewModel.updateTrait("unknown_trait", 0.5f)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.updatePersonalityConfig(
                match {
                    it.traits == PersonalityConfig.DEFAULT_JARVIS.traits
                }
            )
        }
    }

    @Test
    fun `updateName calls repository with new name`() = runTest {
        advanceUntilIdle()

        viewModel.updateName("Friday")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.updatePersonalityConfig(
                match { it.name == "Friday" }
            )
        }
    }

    @Test
    fun `updateName preserves other config fields`() = runTest {
        advanceUntilIdle()

        viewModel.updateName("Friday")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            personalityRepository.updatePersonalityConfig(
                match {
                    it.name == "Friday" &&
                        it.traits == PersonalityConfig.DEFAULT_JARVIS.traits &&
                        it.language == PersonalityConfig.DEFAULT_JARVIS.language
                }
            )
        }
    }

    @Test
    fun `repository getPersonalityConfig is called once`() = runTest {
        advanceUntilIdle()

        verify(exactly = 1) { personalityRepository.getPersonalityConfig() }
    }
}
