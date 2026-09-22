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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests de OverlayViewModel para escenarios de respuesta variada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverlayViewModelMultiStepTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var aiOrchestrator: AiOrchestrator
    private lateinit var captureScreenContextUseCase: CaptureScreenContextUseCase
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
        captureScreenContextUseCase = mockk(relaxed = true)
        memoryEnricher = mockk(relaxed = true)
        ttsMock = mockk(relaxed = true)
        sttMock = mockk(relaxed = true)
        commandParser = mockk(relaxed = true)
        sequenceParser = mockk(relaxed = true)
        multiStepExecutor = mockk(relaxed = true)

        // Sin secuencia ni comando directo: todo sendMessage cae al orquestador IA.
        every { sequenceParser.parse(any()) } returns null
        coEvery { commandParser.parse(any()) } returns null

        every { captureScreenContextUseCase.monitoringState } returns MutableStateFlow(ScreenMonitoringState.Idle)
        coEvery { aiOrchestrator.processMessage(any(), any(), any()) } returns AiResponse.Success("Hola", AiProvider.GEMINI)

        val contextMock = mockk<Context>(relaxed = true)
        every { contextMock.applicationContext } returns contextMock
        val testDir = File(System.getProperty("java.io.tmpdir"), "overlay_multistep_test_${System.nanoTime()}")
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
            captureScreenContextUseCase = captureScreenContextUseCase,
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

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Hola", viewModel.uiState.value.assistantText)
        assertEquals(AnimationState.RESPONDING, viewModel.uiState.value.animationState)
    }

    @Test
    fun `sendMessage con error muestra razon`() {
        coEvery { aiOrchestrator.processMessage(any(), any(), any()) } returns AiResponse.Error("timeout", AiProvider.GEMINI)

        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Error de sistema: timeout", viewModel.uiState.value.assistantText)
    }

    @Test
    fun `sendMessage con FallbackFailed muestra No disponible`() {
        coEvery { aiOrchestrator.processMessage(any(), any(), any()) } returns AiResponse.FallbackFailed("local", "gemini")

        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Servidores no responden.", viewModel.uiState.value.assistantText)
    }

    @Test
    fun `sendMessage con excepcion muestra error generico`() {
        coEvery { aiOrchestrator.processMessage(any(), any(), any()) } throws RuntimeException("boom")

        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Fallo en el núcleo de datos.", viewModel.uiState.value.assistantText)
    }

    @Test
    fun `doble sendMessage se ejecutan correctamente`() {
        coEvery { aiOrchestrator.processMessage("primero", any(), any()) } returns AiResponse.Success("r1", AiProvider.GEMINI)
        coEvery { aiOrchestrator.processMessage("segundo", any(), any()) } returns AiResponse.Success("r2", AiProvider.GEMINI)

        viewModel.sendMessage("primero")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("r1", viewModel.uiState.value.assistantText)

        viewModel.sendMessage("segundo")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("r2", viewModel.uiState.value.assistantText)
    }

    @Test
    fun `sendMessage con texto largo se procesa correctamente`() {
        val longText = "a".repeat(500)
        coEvery { aiOrchestrator.processMessage(longText, any(), any()) } returns AiResponse.Success("ok", AiProvider.GEMINI)

        viewModel.sendMessage(longText)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("ok", viewModel.uiState.value.assistantText)
    }

    @Test
    fun `sendMessage muestra estado de loading durante procesamiento`() {
        viewModel.sendMessage("hola")

        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals("Iniciando protocolos...", viewModel.uiState.value.assistantText)
    }

    @Test
    fun `memory persistTurn se llama en respuesta exitosa`() {
        coEvery { aiOrchestrator.processMessage("test", any(), any()) } returns AiResponse.Success("ok", AiProvider.GEMINI)

        viewModel.sendMessage("test")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { memoryEnricher.persistTurn("test", "ok") }
    }
}
