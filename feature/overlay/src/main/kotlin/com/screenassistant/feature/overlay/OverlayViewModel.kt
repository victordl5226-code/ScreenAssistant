package com.screenassistant.feature.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenassistant.core.domain.usecase.SystemCommandParser
import com.screenassistant.core.domain.di.IoDispatcher
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.ScreenContextRepository
import com.screenassistant.core.domain.service.SpeechToText
import com.screenassistant.core.domain.service.TextToSpeech
import com.screenassistant.core.domain.usecase.CaptureScreenContextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OverlayViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val screenContextRepository: ScreenContextRepository,
    private val commandParser: SystemCommandParser,
    private val captureScreenContextUseCase: CaptureScreenContextUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    // Referencias a TTS/STT (se asignan externamente desde el Composable)
    var textToSpeech: TextToSpeech? = null
    var speechToText: SpeechToText? = null

    // CharacterState para mantener compatibilidad con el sistema de outfits existente
    val characterState = CharacterState()

    // Última respuesta hablada (para el comando "repite")
    private var lastSpokenText: String? = null

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        _uiState.value = _uiState.value.copy(
            inputText = "",
            isLoading = true,
            animationState = AnimationState.ANALYZING,
            assistantText = "Analizando..."
        )

        viewModelScope.launch(ioDispatcher) {
            try {
                // M21 (Lote 8): el texto de pantalla se lee SIEMPRE (barato, no
                // bloquea) y la CAPTURA de imagen SOLO si el comando directo no
                // aplica (antes se capturaba la pantalla también para comandos
                // directos: trabajo de red+imagen desperdiciado en cada mensaje).
                val screenText = captureScreenContextUseCase.getScreenText()
                val isNetworkAvailable = true // Se verifica dentro del use case

                // Probar comando directo primero
                val commandResult = commandParser.parse(text)
                if (commandResult != null) {
                    when (commandResult) {
                        com.screenassistant.core.domain.model.CommandMarkers.HELP -> showHelpCard()
                        com.screenassistant.core.domain.model.CommandMarkers.REPEAT -> repeatLastResponse()
                        else -> handleResponse(commandResult)
                    }
                    return@launch
                }

                // M21: captura diferida — solo para Gemini (imagen null fail-soft).
                val imageData = captureScreenContextUseCase.captureScreenshot()

                // Enviar a Gemini
                val contextText = screenText
                val prompt = if (contextText.isNotEmpty()) {
                    "Analiza mi pantalla y mi petición.\nTexto en pantalla: $contextText\nUsuario dice: $text"
                } else {
                    text
                }

                val response = geminiRepository.sendMessage(prompt, imageData)
                if (response != null) {
                    handleResponse(response)
                } else {
                    handleResponse("Vaya, me he despistado un segundo. ¿Me lo repites?")
                }
            } catch (e: Exception) {
                handleResponse("Parece que mi conexión está un poco lenta. ¿Intentamos de nuevo?")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun handleResponse(response: String) {
        // O7: convención única "Éxito: "/"Error: " → se limpian ambos prefijos
        // para mostrar/leer el texto plano (antes se limpiaba solo "ERROR: ").
        val cleanedResponse = response.removePrefix("Éxito: ").removePrefix("Error: ")
        val animationState = if (cleanedResponse.contains("técnico") || cleanedResponse.contains("formato")) {
            AnimationState.IDLE
        } else if (cleanedResponse.length > 150) {
            AnimationState.TUTORING
        } else {
            AnimationState.RESPONDING
        }

        _uiState.value = _uiState.value.copy(
            assistantText = cleanedResponse,
            animationState = animationState
        )
        characterState.animationState = animationState
        characterState.assistantText = cleanedResponse

        // Si hay TTS disponible, hablar (y recordar para "repite")
        lastSpokenText = cleanedResponse
        textToSpeech?.speak(cleanedResponse)
    }

    /** Muestra la tarjeta de comandos disponibles en el overlay. */
    fun showHelpCard() {
        _uiState.value = _uiState.value.copy(showHelpCard = true, assistantText = "")
        characterState.assistantText = ""
        // Respuesta hablada: enumera las capacidades de la asistente (no solo "puedo hacer cosas").
        textToSpeech?.speak(HELP_SPEECH)
    }

    /** Lista hablada de capacidades para el comando de ayuda. */
    private val HELP_SPEECH: String =
        "Puedo poner alarmas y temporizadores, crear notas, llamar a tus contactos o a un número, " +
        "enviar mensajes, buscar en Google, abrir aplicaciones, reproducir música, " +
        "ajustar el volumen, darte indicaciones con Maps, abrir los ajustes, cambiar mi idioma, " +
        "recordar cosas por ti y responder a tus preguntas. ¿Qué quieres que haga?"

    /** Re-lanza la última respuesta al TTS. No-op si aún no hay historial. */
    fun repeatLastResponse() {
        lastSpokenText?.let { textToSpeech?.speak(it) }
    }

    fun startListening() {
        _uiState.value = _uiState.value.copy(
            isListening = true,
            animationState = AnimationState.LISTENING
        )
        characterState.animationState = AnimationState.LISTENING
        textToSpeech?.stop()
        speechToText?.startListening()
    }

    fun stopListening() {
        speechToText?.stopListening()
        _uiState.value = _uiState.value.copy(isListening = false)
    }

    fun onVoiceResult(text: String) {
        _uiState.value = _uiState.value.copy(
            isListening = false,
            inputText = text
        )
        sendMessage(text)
    }

    fun onVoicePartialResult(partial: String) {
        _uiState.value = _uiState.value.copy(inputText = partial)
    }

    fun onVoiceError(error: String) {
        _uiState.value = _uiState.value.copy(
            isListening = false,
            assistantText = error,
            animationState = AnimationState.IDLE
        )
        characterState.animationState = AnimationState.IDLE
        characterState.assistantText = error
    }

    fun changeOutfit() {
        characterState.nextOutfit()
        _uiState.value = _uiState.value.copy(
            animationState = AnimationState.IDLE,
            // M16 (Lote 8): el índice del UI con MÓDULO igual que CharacterState
            // (antes crecía sin límite y, tras 10 cambios, la sincronía entre el
            // UI y el estado del personaje se rompía).
            currentOutfitIndex = (_uiState.value.currentOutfitIndex + 1) % characterState.outfitCount
        )
    }

    fun dismissBubble() {
        _uiState.value = _uiState.value.copy(assistantText = "", showHelpCard = false)
        characterState.assistantText = ""
    }

    fun onIdleTimeout() {
        // Transiciones automáticas de animación (idle → squatting → meditating)
        val currentState = _uiState.value.animationState
        if (currentState == AnimationState.IDLE) {
            _uiState.value = _uiState.value.copy(animationState = AnimationState.SQUATTING)
            characterState.animationState = AnimationState.SQUATTING
        } else if (currentState == AnimationState.SQUATTING) {
            _uiState.value = _uiState.value.copy(animationState = AnimationState.MEDITATING)
            characterState.animationState = AnimationState.MEDITATING
        }
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.destroy()
        speechToText?.destroy()
    }
}
