package com.screenassistant.feature.overlay

import android.content.Context
import com.screenassistant.core.domain.model.ScreenMonitoringState
import com.screenassistant.core.ai.memory.MemoryEnricher
import com.screenassistant.core.domain.repository.PersonalityRepository
import com.screenassistant.core.domain.repository.ai.AiOrchestrator
import com.screenassistant.core.domain.repository.ai.AiProvider
import com.screenassistant.core.domain.repository.ai.AiResponse
import com.screenassistant.core.domain.service.SpeechToText
import com.screenassistant.core.domain.service.TextToSpeech
import com.screenassistant.core.domain.model.personality.PersonalityConfig
import com.screenassistant.core.domain.usecase.AnalyzeScreenUseCase
import com.screenassistant.core.domain.usecase.CaptureScreenContextUseCase
import com.screenassistant.core.domain.usecase.GetTemporalContextUseCase
import com.screenassistant.core.domain.usecase.MultiStepExecutor
import com.screenassistant.core.domain.usecase.SequenceParser
import com.screenassistant.core.domain.usecase.SystemCommandParser
import com.screenassistant.core.data.proactive.ProactiveSuggestionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests de OverlayViewModel — sendMessage y persistTurn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverlayViewModelTemporalTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var aiOrchestrator: AiOrchestrator
    private lateinit var memoryEnricher: MemoryEnricher
    private lateinit var ttsMock: TextToSpeech
    private lateinit var sttMock: SpeechToText
    private lateinit var commandParser: SystemCommandParser
    private lateinit var sequenceParser: SequenceParser
    private lateinit var multiStepExecutor: MultiStepExecutor
    private lateinit var viewModel: OverlayViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        aiOrchestrator = mockk(relaxed = true)
        memoryEnricher = mockk(relaxed = true)
        ttsMock = mockk(relaxed = true)
        sttMock = mockk(relaxed = true)
        commandParser = mockk(relaxed = true)
        sequenceParser = mockk(relaxed = true)
        multiStepExecutor = mockk(relaxed = true)

        // Sin secuencia ni comando directo: todo sendMessage cae al orquestador IA.
        every { sequenceParser.parse(any()) } returns null
        coEvery { commandParser.parse(any()) } returns null

        coEvery { aiOrchestrator.processMessage(any(), any(), any()) } returns AiResponse.Success("Respuesta de prueba", AiProvider.GEMINI)

        val contextMock = mockk<Context>(relaxed = true)
        every { contextMock.applicationContext } returns contextMock
        val testDir = File(System.getProperty("java.io.tmpdir"), "overlay_temporal_test_${System.nanoTime()}")
        testDir.mkdirs()
        every { contextMock.filesDir } returns testDir

        val personalityRepo = mockk<PersonalityRepository>(relaxed = true)
        every { personalityRepo.getPersonalityConfig() } returns flowOf(PersonalityConfig.DEFAULT_JARVIS)

        viewModel = OverlayViewModel(
            geminiRepository = mockk(relaxed = true),
            aiOrchestrator = aiOrchestrator,
            commandParser = commandParser,
            sequenceParser = sequenceParser,
            multiStepExecutor = multiStepExecutor,
            captureScreenContextUseCase = mockk(relaxed = true),
            analyzeScreenUseCase = mockk(relaxed = true),
            getTemporalContextUseCase = mockk(relaxed = true),
            memoryEnricher = memoryEnricher,
            proactiveSuggestionManager = mockk(relaxed = true),
            memoryRepository = mockk(relaxed = true),
            messageQueueManager = mockk(relaxed = true),
            connectivityMonitor = mockk(relaxed = true),
            batteryMonitor = mockk(relaxed = true),
            nlpCommandParser = mockk(relaxed = true),
            personalityRepository = personalityRepo,
            hotwordPreferences = mockk(relaxed = true),
            ioDispatcher = testDispatcher,
            context = contextMock
        )
        viewModel.textToSpeech = ttsMock
        viewModel.speechToText = sttMock
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sendMessage exitoso muestra respuesta`() {
        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Respuesta de prueba", viewModel.uiState.value.assistantText)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(AnimationState.RESPONDING, viewModel.uiState.value.animationState)
    }

    @Test
    fun `sendMessage con texto vacio no hace nada`() {
        viewModel.sendMessage("")

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `sendMessage delega a aiOrchestrator`() {
        viewModel.sendMessage("test message")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { aiOrchestrator.processMessage(match { it.contains("test message") }, any(), any()) }
    }

    @Test
    fun `sendMessage con error muestra el error`() {
        coEvery { aiOrchestrator.processMessage(any(), any(), any()) } returns AiResponse.Error("API key no configurada", AiProvider.GEMINI)

        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Error de sistema: API key no configurada", viewModel.uiState.value.assistantText)
    }

    @Test
    fun `persistTurn se llama en respuesta exitosa`() {
        coEvery { aiOrchestrator.processMessage("pregunta", any(), any()) } returns AiResponse.Success("respuesta", AiProvider.GEMINI)

        viewModel.sendMessage("pregunta")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { memoryEnricher.persistTurn("pregunta", "respuesta") }
    }

    @Test
    fun `persistTurn no se llama en error`() {
        coEvery { aiOrchestrator.processMessage(any(), any(), any()) } returns AiResponse.Error("fallo", AiProvider.GEMINI)

        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { memoryEnricher.persistTurn(any(), any()) }
    }
}
