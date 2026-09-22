package com.screenassistant.feature.overlay

import androidx.lifecycle.ViewModel
import android.content.Context
import com.screenassistant.core.ai.memory.MemoryEnricher
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.PersonalityRepository
import com.screenassistant.core.domain.repository.ai.AiOrchestrator
import com.screenassistant.core.domain.repository.ai.AiProvider
import com.screenassistant.core.domain.repository.ai.AiResponse
import com.screenassistant.core.domain.service.SpeechToText
import com.screenassistant.core.domain.service.TextToSpeech
import com.screenassistant.core.domain.model.ScreenMonitoringState
import com.screenassistant.core.domain.model.CommandMarkers
import com.screenassistant.core.domain.model.personality.PersonalityConfig
import com.screenassistant.core.domain.usecase.AnalyzeScreenUseCase
import com.screenassistant.core.domain.usecase.CaptureScreenContextUseCase
import com.screenassistant.core.domain.usecase.GetTemporalContextUseCase
import com.screenassistant.core.domain.usecase.MultiStepExecutor
import com.screenassistant.core.domain.usecase.SequenceParser
import com.screenassistant.core.domain.usecase.SystemCommandParser
import com.screenassistant.core.data.proactive.ProactiveSuggestionManager
import io.mockk.clearMocks
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests de OverlayViewModel contra la lógica actual de sendMessage/handleResponse.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverlayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var aiOrchestrator: AiOrchestrator
    private lateinit var captureScreenContextUseCase: CaptureScreenContextUseCase
    private lateinit var memoryEnricher: MemoryEnricher
    private lateinit var ttsMock: TextToSpeech
    private lateinit var sttMock: SpeechToText
    private lateinit var contextMock: Context
    private lateinit var commandParser: SystemCommandParser
    private lateinit var sequenceParser: SequenceParser
    private lateinit var multiStepExecutor: MultiStepExecutor
    private lateinit var viewModel: OverlayViewModel

    private val monitoringStateFlow = MutableStateFlow<ScreenMonitoringState>(ScreenMonitoringState.Idle)

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
        // (relaxed devolvería no-nulo y la ruta multi-paso abortaría con "Secuencia cancelada").
        every { sequenceParser.parse(any()) } returns null
        coEvery { commandParser.parse(any()) } returns null

        every { captureScreenContextUseCase.monitoringState } returns monitoringStateFlow

        // Configurar respuesta exitosa por defecto
        coEvery { aiOrchestrator.processMessage(any(), any(), any()) } returns AiResponse.Success("Hola", AiProvider.GEMINI)

        contextMock = mockk(relaxed = true)
        every { contextMock.applicationContext } returns contextMock
        val testDir = File(System.getProperty("java.io.tmpdir"), "overlay_test_${System.nanoTime()}")
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

    // ========== sendMessage ==========

    @Test
    fun `sendMessage exitoso responde y habla`() {
        viewModel.sendMessage("hola")

        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals("Iniciando protocolos...", viewModel.uiState.value.assistantText)
        assertEquals("", viewModel.uiState.value.inputText)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Hola", state.assistantText)
        assertEquals(AnimationState.RESPONDING, state.animationState)
        verify { ttsMock.speak("Hola") }
    }

    @Test
    fun `sendMessage con error de AiOrchestrator muestra solo el motivo`() {
        coEvery { aiOrchestrator.processMessage(any(), any(), any()) } returns AiResponse.Error("sin respuesta", AiProvider.GEMINI)

        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Error de sistema: sin respuesta", viewModel.uiState.value.assistantText)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `sendMessage con excepcion muestra error inesperado`() {
        coEvery { aiOrchestrator.processMessage(any(), any(), any()) } throws RuntimeException("red caída")

        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Fallo en el núcleo de datos.", viewModel.uiState.value.assistantText)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `sendMessage con texto vacio no cambia nada`() {
        viewModel.sendMessage("   ")

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(AnimationState.IDLE, viewModel.uiState.value.animationState)
        assertEquals("", viewModel.uiState.value.inputText)
    }

    @Test
    fun `sendMessage con FallbackFailed muestra No disponible`() {
        coEvery { aiOrchestrator.processMessage(any(), any(), any()) } returns AiResponse.FallbackFailed("local", "gemini")

        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Servidores no responden.", viewModel.uiState.value.assistantText)
    }

    // ========== onIdleTimeout ==========

    @Test
    fun `onIdleTimeout desde IDLE avanza a SQUATTING`() {
        viewModel.onIdleTimeout()
        assertEquals(AnimationState.SQUATTING, viewModel.uiState.value.animationState)
    }

    @Test
    fun `onIdleTimeout doble llamada se queda en SQUATTING`() {
        viewModel.onIdleTimeout()
        assertEquals(AnimationState.SQUATTING, viewModel.uiState.value.animationState)

        viewModel.onIdleTimeout()
        assertEquals(AnimationState.SQUATTING, viewModel.uiState.value.animationState)
    }

    @Test
    fun `onIdleTimeout desde RESPONDING no cambia el estado`() {
        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AnimationState.RESPONDING, viewModel.uiState.value.animationState)

        viewModel.onIdleTimeout()

        assertEquals(AnimationState.RESPONDING, viewModel.uiState.value.animationState)
    }

    // ========== Input / Voice ==========

    @Test
    fun `onInputChanged actualiza inputText`() {
        viewModel.onInputChanged("abc")
        assertEquals("abc", viewModel.uiState.value.inputText)
    }

    @Test
    fun `onVoiceResult envia el texto`() {
        viewModel.onVoiceResult("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { aiOrchestrator.processMessage(match { it.contains("hola") }, any(), any()) }
    }

    @Test
    fun `onVoicePartialResult solo actualiza inputText`() {
        viewModel.onVoicePartialResult("par")
        assertEquals("par", viewModel.uiState.value.inputText)
    }

    @Test
    fun `onVoiceError muestra el error`() {
        viewModel.onVoiceError("error de voz")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isListening)
        assertEquals("Error de voz: error de voz", viewModel.uiState.value.assistantText)
        assertEquals(AnimationState.RESPONDING, viewModel.uiState.value.animationState)
    }

    @Test
    fun `startListening activa escucha`() {
        viewModel.startListening()

        assertTrue(viewModel.uiState.value.isListening)
        verify { sttMock.startListening() }
    }

    @Test
    fun `stopListening desactiva escucha`() {
        viewModel.stopListening()

        assertFalse(viewModel.uiState.value.isListening)
        verify { sttMock.stopListening() }
    }

    // ========== dismissBubble ==========

    @Test
    fun `dismissBubble limpia assistantText`() {
        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.assistantText.isNotEmpty())

        clearMocks(ttsMock, answers = false, recordedCalls = true)

        viewModel.dismissBubble()

        assertEquals("", viewModel.uiState.value.assistantText)
        verify(exactly = 0) { ttsMock.speak(any()) }
    }

    // ========== changeOutfit ==========

    @Test
    fun `changeOutfit avanza al siguiente atuendo`() {
        val resBefore = viewModel.characterState.currentAssetRes

        viewModel.changeOutfit()

        assertEquals(1, viewModel.uiState.value.currentOutfitIndex)
        assertNotEquals(resBefore, viewModel.characterState.currentAssetRes)
    }

    // ========== onCleared ==========

    @Test
    fun `onCleared destruye TTS y STT`() {
        invokeOnCleared(viewModel)

        verify { ttsMock.destroy() }
        verify { sttMock.destroy() }
    }

    @Test
    fun `onCleared sin TTS ni STT no lanza excepcion`() {
        viewModel.textToSpeech = null
        viewModel.speechToText = null

        invokeOnCleared(viewModel)
    }

    @Test
    fun `onCleared llama a stopMonitoring`() {
        invokeOnCleared(viewModel)

        verify { captureScreenContextUseCase.stopMonitoring() }
    }

    // ========== Monitoring ==========

    @Test
    fun `toggleMonitoring inicia monitoreo si esta inactivo`() {
        assertFalse(viewModel.uiState.value.isMonitoring)
        viewModel.toggleMonitoring()

        verify { captureScreenContextUseCase.startMonitoring(any()) }
    }

    @Test
    fun `toggleMonitoring detiene monitoreo si esta activo`() {
        monitoringStateFlow.value = ScreenMonitoringState.Active(intervalMs = 5000L, screenText = "abc")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isMonitoring)

        viewModel.toggleMonitoring()

        verify { captureScreenContextUseCase.stopMonitoring() }
    }

    @Test
    fun `cuando monitoringState emite Active el uiState refleja isMonitoring true`() {
        monitoringStateFlow.value = ScreenMonitoringState.Active(intervalMs = 3000L, screenText = "Texto")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMonitoring)
        assertEquals("Texto", viewModel.uiState.value.monitoringScreenText)
    }

    @Test
    fun `cuando monitoringState emite Idle el uiState refleja isMonitoring false`() {
        monitoringStateFlow.value = ScreenMonitoringState.Active(intervalMs = 5000L, screenText = "abc")
        testDispatcher.scheduler.advanceUntilIdle()

        monitoringStateFlow.value = ScreenMonitoringState.Idle
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMonitoring)
    }

    @Test
    fun `cuando monitoringState emite Error se desactiva monitoreo`() {
        monitoringStateFlow.value = ScreenMonitoringState.Error("Servicio desconectado")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMonitoring)
    }

    @Test
    fun `setMonitoringInterval actualiza el intervalo`() {
        viewModel.setMonitoringInterval(3000L)
        assertEquals(3000L, viewModel.uiState.value.monitoringIntervalMs)
    }

    @Test
    fun `setMonitoringInterval reinicia monitoreo cuando esta activo`() {
        monitoringStateFlow.value = ScreenMonitoringState.Active(intervalMs = 5000L, screenText = "Texto")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setMonitoringInterval(3000L)

        assertEquals(3000L, viewModel.uiState.value.monitoringIntervalMs)
        verify { captureScreenContextUseCase.stopMonitoring() }
        verify { captureScreenContextUseCase.startMonitoring(3000L) }
    }

    @Test
    fun `setMonitoringInterval no toca monitoreo cuando esta inactivo`() {
        assertFalse(viewModel.uiState.value.isMonitoring)

        viewModel.setMonitoringInterval(3000L)

        assertEquals(3000L, viewModel.uiState.value.monitoringIntervalMs)
        verify(exactly = 0) { captureScreenContextUseCase.stopMonitoring() }
        verify(exactly = 0) { captureScreenContextUseCase.startMonitoring(any()) }
    }

    @Test
    fun `startMonitoring delega al use case`() {
        viewModel.startMonitoring()
        verify { captureScreenContextUseCase.startMonitoring(any()) }
    }

    @Test
    fun `stopMonitoring delega al use case`() {
        viewModel.stopMonitoring()
        verify { captureScreenContextUseCase.stopMonitoring() }
    }

    // ========== FIX-B: intercepción de marcadores ==========

    @Test
    fun `sendMessage activar monitoreo intercepta marcador y muestra texto visible`() {
        coEvery { commandParser.parse(any()) } returns CommandMarkers.START_MONITORING

        viewModel.sendMessage("activar monitoreo")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Activando monitoreo continuo, Señor. Sistemas de observación en línea.", state.assistantText)
        assertFalse(state.assistantText.contains("__SCREEN_ASSISTANT"))
        verify { captureScreenContextUseCase.startMonitoring(any()) }
        coVerify { memoryEnricher.persistTurn("activar monitoreo", "Activando monitoreo continuo, Señor. Sistemas de observación en línea.") }
    }

    @Test
    fun `sendMessage detener monitoreo intercepta marcador y muestra texto visible`() {
        monitoringStateFlow.value = ScreenMonitoringState.Active(intervalMs = 5000L, screenText = "abc")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isMonitoring)
        coEvery { commandParser.parse(any()) } returns CommandMarkers.STOP_MONITORING

        viewModel.sendMessage("detener monitoreo")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Deteniendo monitoreo, Señor. Sistemas en reposo.", state.assistantText)
        assertFalse(state.assistantText.contains("__SCREEN_ASSISTANT"))
        verify { captureScreenContextUseCase.stopMonitoring() }
        coVerify { memoryEnricher.persistTurn("detener monitoreo", "Deteniendo monitoreo, Señor. Sistemas en reposo.") }
    }

    @Test
    fun `sendMessage ayuda muestra respuesta local sin marcador`() {
        coEvery { commandParser.parse(any()) } returns CommandMarkers.HELP

        viewModel.sendMessage("ayuda")
        testDispatcher.scheduler.advanceUntilIdle()

        val text = viewModel.uiState.value.assistantText
        assertTrue(text.contains("Puedo ayudarte"))
        assertFalse(text.contains("__SCREEN_ASSISTANT"))
    }

    @Test
    fun `sendMessage repite con historial vacio muestra fallback`() {
        coEvery { commandParser.parse(any()) } returns CommandMarkers.REPEAT

        viewModel.sendMessage("repite")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Aún no hay nada que repetir, Señor.", viewModel.uiState.value.assistantText)
    }

    /** Helper: invoca onCleared por reflexión. */
    private fun invokeOnCleared(vm: ViewModel) {
        val method = ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(vm)
    }
}
