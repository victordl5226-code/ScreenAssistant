package com.screenassistant.feature.overlay

import androidx.lifecycle.ViewModel
import com.screenassistant.core.domain.repository.ConversationRepository
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.ScreenContextRepository
import com.screenassistant.core.domain.service.SpeechToText
import com.screenassistant.core.domain.service.TextToSpeech
import com.screenassistant.core.domain.model.CommandMarkers
import com.screenassistant.core.domain.usecase.CaptureScreenContextUseCase
import com.screenassistant.core.domain.usecase.SystemCommandParser
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de OverlayViewModel contra la lógica real de sendMessage/handleResponse.
 * Un único StandardTestDispatcher compartido (Main + IO) para que
 * advanceUntilIdle() ejecute las corrutinas de IO.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverlayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var geminiRepository: GeminiRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var screenContextRepository: ScreenContextRepository
    private lateinit var commandParser: SystemCommandParser
    private lateinit var captureScreenContextUseCase: CaptureScreenContextUseCase
    private lateinit var ttsMock: TextToSpeech
    private lateinit var sttMock: SpeechToText
    private lateinit var viewModel: OverlayViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mocks NO relaxed: solo responden lo que se stubea.
        geminiRepository = mockk()
        conversationRepository = mockk()
        screenContextRepository = mockk()
        commandParser = mockk()
        captureScreenContextUseCase = mockk()
        ttsMock = mockk()
        sttMock = mockk()

        // Stubs base
        coEvery { captureScreenContextUseCase.captureScreenshot() } returns null
        every { captureScreenContextUseCase.getScreenText() } returns ""
        every { commandParser.parse(any()) } returns null
        coEvery { geminiRepository.sendMessage(any(), any()) } returns "Hola"
        every { ttsMock.speak(any()) } returns Unit
        every { ttsMock.stop() } returns Unit
        every { ttsMock.destroy() } returns Unit
        every { sttMock.startListening() } returns Unit
        every { sttMock.stopListening() } returns Unit
        every { sttMock.destroy() } returns Unit

        // Instancia FRESCA del VM por test
        viewModel = OverlayViewModel(
            geminiRepository = geminiRepository,
            conversationRepository = conversationRepository,
            screenContextRepository = screenContextRepository,
            commandParser = commandParser,
            captureScreenContextUseCase = captureScreenContextUseCase,
            ioDispatcher = testDispatcher
        )
        viewModel.textToSpeech = ttsMock
        viewModel.speechToText = sttMock
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. sendMessage exitoso: estados durante/después + TTS
    @Test
    fun `sendMessage exitoso analiza, responde y habla`() {
        viewModel.sendMessage("hola")

        // Durante (antes de advance): analizando
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(AnimationState.ANALYZING, viewModel.uiState.value.animationState)
        assertEquals("", viewModel.uiState.value.inputText)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("Hola", state.assistantText)
        assertEquals(AnimationState.RESPONDING, state.animationState)
        verify { ttsMock.speak("Hola") }
    }

    // 2. Respuesta null de Gemini
    @Test
    fun `sendMessage con respuesta null muestra mensaje de despiste`() {
        coEvery { geminiRepository.sendMessage(any(), any()) } returns null

        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "Vaya, me he despistado un segundo. ¿Me lo repites?",
            viewModel.uiState.value.assistantText
        )
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    // 3. Excepción de Gemini
    @Test
    fun `sendMessage con excepcion muestra mensaje de conexion lenta`() {
        coEvery { geminiRepository.sendMessage(any(), any()) } throws RuntimeException("red caída")

        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "Parece que mi conexión está un poco lenta. ¿Intentamos de nuevo?",
            viewModel.uiState.value.assistantText
        )
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    // 4. Comando directo: no se llama a Gemini; M21: el texto SÍ se lee (barato,
    // no bloquea) pero la CAPTURA de imagen se difiere (solo para Gemini).
    @Test
    fun `sendMessage con comando directo no llama a Gemini`() {
        every { commandParser.parse(any()) } returns "Resultado"

        viewModel.sendMessage("busca gatos")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Resultado", viewModel.uiState.value.assistantText)
        coVerify(exactly = 0) { geminiRepository.sendMessage(any(), any()) }
        // M21: el comando directo NO captura imagen (antes se capturaba siempre).
        coVerify(exactly = 0) { captureScreenContextUseCase.captureScreenshot() }
        verify(exactly = 1) { captureScreenContextUseCase.getScreenText() }
    }

    // 5. Rama "técnico": Error: ... → IDLE
    @Test
    fun `sendMessage con error tecnico del parser limpia prefijo y pasa a IDLE`() {
        every { commandParser.parse(any()) } returns "Error: hubo un error técnico"

        viewModel.sendMessage("busca gatos")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("hubo un error técnico", viewModel.uiState.value.assistantText)
        assertEquals(AnimationState.IDLE, viewModel.uiState.value.animationState)
        assertEquals(AnimationState.IDLE, viewModel.characterState.animationState)
    }

    // 6. Texto vacío: return inmediato
    @Test
    fun `sendMessage con texto vacio no cambia nada`() {
        viewModel.sendMessage("   ")

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(AnimationState.IDLE, viewModel.uiState.value.animationState)
        assertEquals("", viewModel.uiState.value.inputText)
        coVerify(exactly = 0) { geminiRepository.sendMessage(any(), any()) }
    }

    // 7. Respuesta larga → TUTORING
    @Test
    fun `sendMessage con respuesta larga pasa a TUTORING`() {
        coEvery { geminiRepository.sendMessage(any(), any()) } returns "a".repeat(160)

        viewModel.sendMessage("explícame esto")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AnimationState.TUTORING, viewModel.uiState.value.animationState)
        assertEquals(AnimationState.TUTORING, viewModel.characterState.animationState)
    }

    // 8. onIdleTimeout: IDLE → SQUATTING → MEDITATING
    @Test
    fun `onIdleTimeout desde IDLE avanza a SQUATTING y luego MEDITATING`() {
        viewModel.onIdleTimeout()
        assertEquals(AnimationState.SQUATTING, viewModel.uiState.value.animationState)
        assertEquals(AnimationState.SQUATTING, viewModel.characterState.animationState)

        viewModel.onIdleTimeout()
        assertEquals(AnimationState.MEDITATING, viewModel.uiState.value.animationState)
        assertEquals(AnimationState.MEDITATING, viewModel.characterState.animationState)
    }

    // 9. onIdleTimeout desde RESPONDING: sin cambio
    @Test
    fun `onIdleTimeout desde RESPONDING no cambia el estado`() {
        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AnimationState.RESPONDING, viewModel.uiState.value.animationState)

        viewModel.onIdleTimeout()

        assertEquals(AnimationState.RESPONDING, viewModel.uiState.value.animationState)
        assertEquals(AnimationState.RESPONDING, viewModel.characterState.animationState)
    }

    // 10. onInputChanged
    @Test
    fun `onInputChanged actualiza inputText`() {
        viewModel.onInputChanged("abc")
        assertEquals("abc", viewModel.uiState.value.inputText)
    }

    // 11. dismissBubble limpia el texto sin hablar
    @Test
    fun `dismissBubble limpia assistantText y no dispara TTS`() {
        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.assistantText.isNotEmpty())

        // Resetear el registro de llamadas de TTS para aislar el efecto de dismissBubble
        clearMocks(ttsMock, answers = false, recordedCalls = true)

        viewModel.dismissBubble()

        assertEquals("", viewModel.uiState.value.assistantText)
        assertEquals("", viewModel.characterState.assistantText)
        verify(exactly = 0) { ttsMock.speak(any()) }
    }

    // 12. onVoiceResult: desactiva escucha y envía el texto
    @Test
    fun `onVoiceResult desactiva escucha y envia el texto`() {
        viewModel.onVoiceResult("hola")

        // Comportamiento real: sendMessage limpia inputText de forma SÍNCRONA al
        // inicio (antes del launch), por lo que el valor transitorio "hola" de
        // onVoiceResult nunca es observable desde fuera.
        assertEquals(false, viewModel.uiState.value.isListening)
        assertEquals("", viewModel.uiState.value.inputText)

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { geminiRepository.sendMessage("hola", null) }
    }

    // 13. onVoicePartialResult: solo actualiza input, NO envía
    @Test
    fun `onVoicePartialResult solo actualiza inputText sin enviar`() {
        viewModel.onVoicePartialResult("par")
        assertEquals("par", viewModel.uiState.value.inputText)

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { geminiRepository.sendMessage(any(), any()) }
    }

    // 14. onVoiceError: estado IDLE y texto de error, sin TTS
    @Test
    fun `onVoiceError muestra el error y vuelve a IDLE sin hablar`() {
        viewModel.onVoiceError("error")

        assertEquals(false, viewModel.uiState.value.isListening)
        assertEquals("error", viewModel.uiState.value.assistantText)
        assertEquals(AnimationState.IDLE, viewModel.uiState.value.animationState)
        assertEquals(AnimationState.IDLE, viewModel.characterState.animationState)
        assertEquals("error", viewModel.characterState.assistantText)
        verify(exactly = 0) { ttsMock.speak(any()) }
    }

    // 15a. startListening
    @Test
    fun `startListening activa escucha y LISTENING`() {
        viewModel.startListening()

        assertEquals(true, viewModel.uiState.value.isListening)
        assertEquals(AnimationState.LISTENING, viewModel.uiState.value.animationState)
        assertEquals(AnimationState.LISTENING, viewModel.characterState.animationState)
        verify { sttMock.startListening() }
        verify { ttsMock.stop() }
    }

    // 15b. stopListening (sin start previo): no vuelve a arrancar el micrófono
    @Test
    fun `stopListening desactiva escucha sin volver a arrancar`() {
        viewModel.stopListening()

        assertEquals(false, viewModel.uiState.value.isListening)
        verify { sttMock.stopListening() }
        verify(exactly = 0) { sttMock.startListening() }
    }

    // 16. changeOutfit: siguiente atuendo + IDLE
    @Test
    fun `changeOutfit avanza al siguiente atuendo y pasa a IDLE`() {
        val resBefore = viewModel.characterState.currentAssetRes

        viewModel.changeOutfit()

        assertEquals(1, viewModel.uiState.value.currentOutfitIndex)
        assertEquals(AnimationState.IDLE, viewModel.uiState.value.animationState)
        assertEquals(AnimationState.IDLE, viewModel.characterState.animationState)
        assertNotEquals(resBefore, viewModel.characterState.currentAssetRes)
    }

    // 16b. M16: el índice del UI usa módulo como CharacterState (antes crecía sin límite)
    @Test
    fun `changeOutfit vuelve a 0 tras un ciclo completo de atuendos`() {
        repeat(viewModel.characterState.outfitCount) { viewModel.changeOutfit() }

        // Un ciclo completo: índice 10 → 0 (con el código antiguo sería 10 y la
        // sincronía con el atuendo real se rompería).
        assertEquals(0, viewModel.uiState.value.currentOutfitIndex)
        assertEquals(AnimationState.IDLE, viewModel.uiState.value.animationState)
    }

    // 17. onCleared con TTS/STT asignados: destruye ambos
    @Test
    fun `onCleared destruye TTS y STT cuando estan asignados`() {
        invokeOnCleared(viewModel)

        verify { ttsMock.destroy() }
        verify { sttMock.destroy() }
    }

    // 18. onCleared sin TTS/STT: no lanza excepción
    @Test
    fun `onCleared sin TTS ni STT no lanza excepcion`() {
        viewModel.textToSpeech = null
        viewModel.speechToText = null

        invokeOnCleared(viewModel) // no debe lanzar
    }

    // 19. Comando de ayuda: muestra la tarjeta y habla la lista de capacidades, sin tocar Gemini
    @Test
    fun `comando de ayuda muestra la tarjeta sin llamar a Gemini`() {
        every { commandParser.parse(any()) } returns CommandMarkers.HELP

        viewModel.sendMessage("ayuda")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showHelpCard)
        assertEquals("", viewModel.uiState.value.assistantText)
        // La respuesta hablada enumera las capacidades (no solo "puedo hacer cosas")
        verify {
            ttsMock.speak(
                match {
                    it.contains("Puedo poner alarmas y temporizadores") &&
                        it.contains("llamar a tus contactos") &&
                        it.contains("buscar en Google") &&
                        it.contains("recordar cosas por ti")
                }
            )
        }
        coVerify(exactly = 0) { geminiRepository.sendMessage(any(), any()) }
    }

    // 20. Comando de repetición: relanza la última respuesta por TTS
    @Test
    fun `comando repite relanza la ultima respuesta`() {
        viewModel.sendMessage("hola")
        testDispatcher.scheduler.advanceUntilIdle()
        verify { ttsMock.speak("Hola") }

        // Aislar el historial previo: limpiar el registro de TTS y de Gemini
        clearMocks(ttsMock, answers = false, recordedCalls = true)
        clearMocks(geminiRepository, answers = false, recordedCalls = true)
        every { commandParser.parse(any()) } returns CommandMarkers.REPEAT

        viewModel.sendMessage("repite")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { ttsMock.speak("Hola") }
        coVerify(exactly = 0) { geminiRepository.sendMessage(any(), any()) }
    }

    // 21. Repetición sin historial: no habla nada
    @Test
    fun `repite sin historial no habla nada`() {
        every { commandParser.parse(any()) } returns CommandMarkers.REPEAT

        viewModel.sendMessage("repite")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { ttsMock.speak(any()) }
    }

    // 22. dismissBubble también oculta la tarjeta de ayuda
    @Test
    fun `dismissBubble oculta tambien la tarjeta de ayuda`() {
        every { commandParser.parse(any()) } returns CommandMarkers.HELP
        viewModel.sendMessage("ayuda")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showHelpCard)

        viewModel.dismissBubble()

        assertEquals(false, viewModel.uiState.value.showHelpCard)
        assertEquals("", viewModel.uiState.value.assistantText)
    }

    /** onCleared es protected en ViewModel; se invoca por reflexión en tests. */
    private fun invokeOnCleared(viewModel: ViewModel) {
        val method = ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }
}
