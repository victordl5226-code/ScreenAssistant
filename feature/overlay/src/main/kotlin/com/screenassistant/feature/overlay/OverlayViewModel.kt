package com.screenassistant.feature.overlay

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenassistant.core.data.util.MonitoringPreferences
import com.screenassistant.core.domain.model.ScreenMonitoringState
import com.screenassistant.core.domain.model.StepFeedback
import com.screenassistant.core.domain.model.StepStatus
import com.screenassistant.core.domain.usecase.MultiStepExecutor
import com.screenassistant.core.domain.usecase.SequenceParser
import com.screenassistant.core.domain.usecase.SystemCommandParser
import com.screenassistant.core.domain.di.IoDispatcher
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.domain.repository.PersonalityRepository
import com.screenassistant.core.domain.repository.ai.AiOrchestrator
import com.screenassistant.core.domain.repository.ai.AiResponse
import com.screenassistant.core.ai.memory.MemoryEnricher
import com.screenassistant.core.domain.service.SpeechToText
import com.screenassistant.core.domain.service.TextToSpeech
import com.screenassistant.feature.overlay.hotword.HotwordListeningService
import com.screenassistant.feature.overlay.hotword.HotwordPreferences
import com.screenassistant.core.domain.model.TemporalContext
import com.screenassistant.core.domain.usecase.AnalyzeScreenUseCase
import com.screenassistant.core.domain.usecase.CaptureScreenContextUseCase
import com.screenassistant.core.domain.usecase.GetTemporalContextUseCase
import com.screenassistant.core.data.proactive.ProactiveSuggestionManager
import com.screenassistant.core.data.local.MessageQueueManager
import com.screenassistant.core.domain.model.*
import com.screenassistant.core.domain.model.AssistantMode
import com.screenassistant.core.domain.repository.ai.ConnectivityMonitor
import com.screenassistant.core.domain.util.JarvisResponseFormatter
import com.screenassistant.core.domain.util.PromptCatalog
import com.screenassistant.core.nlp.parser.NlpCommandParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OverlayViewModel @Inject constructor(
    private val aiOrchestrator: AiOrchestrator,
    private val memoryEnricher: MemoryEnricher,
    private val captureScreenContextUseCase: CaptureScreenContextUseCase,
    private val messageQueueManager: MessageQueueManager,
    private val connectivityMonitor: ConnectivityMonitor,
    private val batteryMonitor: com.screenassistant.core.domain.repository.ai.BatteryMonitor,
    private val memoryRepository: MemoryRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
    // Conservamos estos por ahora aunque no se usen en este fragmento corregido 
    // para no romper la inyección de Hilt si otros métodos los usan fuera del fragmento leído.
    private val geminiRepository: GeminiRepository,
    private val commandParser: SystemCommandParser,
    private val nlpCommandParser: NlpCommandParser,
    private val sequenceParser: SequenceParser,
    private val multiStepExecutor: MultiStepExecutor,
    private val analyzeScreenUseCase: AnalyzeScreenUseCase,
    private val getTemporalContextUseCase: GetTemporalContextUseCase,
    private val proactiveSuggestionManager: ProactiveSuggestionManager,
    private val personalityRepository: PersonalityRepository,
    private val hotwordPreferences: HotwordPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    var textToSpeech: TextToSpeech? = null
    var speechToText: SpeechToText? = null
    val characterState = CharacterState()
    private var lastSpokenText: String? = null

    init {
        // Observar el modo del asistente desde el SystemActionHandler
        viewModelScope.launch(ioDispatcher) {
            commandParser.assistantMode.collect { mode ->
                _uiState.update { it.copy(assistantMode = mode) }
            }
        }

        viewModelScope.launch(ioDispatcher) {
            val interval = MonitoringPreferences.getInterval(context)
            _uiState.update { it.copy(monitoringIntervalMs = interval) }
        }

        // Leer nombre del asistente desde configuración de personalidad
        viewModelScope.launch(ioDispatcher) {
            personalityRepository.getPersonalityConfig().collect { config ->
                _uiState.update { it.copy(assistantName = config.name) }
            }
        }

        viewModelScope.launch(ioDispatcher) {
            captureScreenContextUseCase.activeAppPackage.collect { packageName ->
                packageName?.let { pkg -> onPackageChanged(pkg) }
            }
        }

        viewModelScope.launch(ioDispatcher) {
            connectivityMonitor.connectivityInfo.collect { info ->
                if (info.isConnected) {
                    val pending = messageQueueManager.getPendingMessages().first()
                    if (pending.isNotEmpty()) {
                        val memories = memoryRepository.getAllMemories().first()
                        val userName = memoryRepository.getUserName(memories) ?: "Señor"
                        val alert = JarvisResponseFormatter.formatSystemAlert(
                            "¡Ya hay internet! Tienes un mensaje guardado para ${pending.first().contactName}. ¿Lo enviamos ahora?",
                            userName
                        )
                        launch(Dispatchers.Main) { handleResponse(alert) }
                    }
                }
            }
        }

        viewModelScope.launch(ioDispatcher) {
            captureScreenContextUseCase.monitoringState.collect { state ->
                when (state) {
                    is ScreenMonitoringState.Active -> {
                        _uiState.update { it.copy(isMonitoring = true, monitoringScreenText = state.screenText) }
                    }
                    else -> _uiState.update { it.copy(isMonitoring = false) }
                }
            }
        }

        // Hotword: sincronizar preferencias con UI state
        viewModelScope.launch(ioDispatcher) {
            hotwordPreferences.isEnabled.collect { enabled ->
                _uiState.update { it.copy(hotwordEnabled = enabled) }
            }
        }

        // --- PROTOCOLOS J.A.R.V.I.S. (Hardware Awareness) ---
        
        // 1. Observar cambios en la batería y carga
        viewModelScope.launch(ioDispatcher) {
            batteryMonitor.observeBattery().collect { info ->
                val userName = "Señor" // TODO: Obtener del historial real
                
                _uiState.update { it.copy(batteryLevel = info.level, isCharging = info.isCharging) }

                // M28: Solo avisar si no estamos en modo Silencioso
                if (_uiState.value.assistantMode != AssistantMode.SILENCIOSO) {
                    when {
                        info.isCharging -> {
                            launch(Dispatchers.Main) { handleResponse("Sistemas conectados a la red eléctrica, $userName. Recargando celdas de energía.") }
                        }
                        info.level <= 15 -> {
                            launch(Dispatchers.Main) { handleResponse("Atención $userName, niveles de energía por debajo del quince por ciento. Recomiendo conectar el cargador.") }
                        }
                    }
                }
            }
        }

        // 2. Observar cambios en la red
        viewModelScope.launch(ioDispatcher) {
            connectivityMonitor.connectivityInfo.collect { info ->
                if (!info.isConnected && 
                    _uiState.value.assistantMode != AssistantMode.SILENCIOSO) {
                    launch(Dispatchers.Main) { handleResponse("Se ha perdido la conexión de datos. Cambiando a protocolos locales fuera de línea.") }
                }
            }
        }
    }

    private fun onPackageChanged(packageName: String) {
        val outfitIndex = mapPackageToOutfit(packageName)
        if (outfitIndex != null) {
            viewModelScope.launch(Dispatchers.Main) {
                characterState.setOutfit(outfitIndex)
                _uiState.update { it.copy(currentOutfitIndex = outfitIndex) }
            }
        }

        // M28: Filtrar comentarios proactivos según el Modo de Conciencia
        val mode = _uiState.value.assistantMode
        if (mode == AssistantMode.SILENCIOSO) return
        
        // En modo Táctico, solo comentar apps críticas (ej. ajustes, bancos, etc.)
        if (mode == AssistantMode.TACTICO &&
            !packageName.contains("settings") && !packageName.contains("android.settings")) {
            return
        }

        viewModelScope.launch(ioDispatcher) {
            if (!_uiState.value.isLoading && aiOrchestrator.isLocalAvailable()) {
                val prompt = PromptCatalog.getObservationPrompt(packageName)
                val thought = aiOrchestrator.processMessage(prompt, preferLocal = true)
                if (thought is AiResponse.Success) {
                    withContext(Dispatchers.Main) { handleResponse(thought.text) }
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(inputText = "", isLoading = true, assistantText = "Iniciando protocolos...") }

        viewModelScope.launch(ioDispatcher) {
            try {
                val config = personalityRepository.getPersonalityConfig().first()
                val assistantName = config.name
                val userName = "Señor" // TODO: Obtener del historial si existe

                if (text == "ACTION_INIT_CONVERSATION") {
                    val memories = memoryRepository.getAllMemories().first()
                    val userName = memoryRepository.getUserName(memories) ?: "Señor"
                    handleResponse(JarvisResponseFormatter.formatGreeting(assistantName, userName))
                    return@launch
                }

                // 1. Intentar secuencia multi-paso (Lote MULTI)
                val sequence = sequenceParser.parse(text)
                if (sequence != null) {
                    _uiState.update { it.copy(isMultiStep = true) }
                    val multiResult = multiStepExecutor.execute(sequence)
                    handleMultiStepResult(multiResult, text)
                    return@launch
                }

                // 2. Intentar parseo de comando directo (legacy/suspend)
                val directResponse = commandParser.parse(text)
                if (directResponse != null) {
                    when (directResponse) {
                        CommandMarkers.START_MONITORING -> {
                            toggleMonitoring()
                            val visible = "Activando monitoreo continuo, Señor. Sistemas de observación en línea."
                            handleResponse(visible)
                            memoryEnricher.persistTurn(text, visible)
                            return@launch
                        }
                        CommandMarkers.STOP_MONITORING -> {
                            toggleMonitoring()
                            val visible = "Deteniendo monitoreo, Señor. Sistemas en reposo."
                            handleResponse(visible)
                            memoryEnricher.persistTurn(text, visible)
                            return@launch
                        }
                        CommandMarkers.HELP -> {
                            val visible = LocalHelpResponder.getHelpResponse()
                            handleResponse(visible)
                            memoryEnricher.persistTurn(text, visible)
                            return@launch
                        }
                        CommandMarkers.REPEAT -> {
                            val last = lastSpokenText
                            val visible = if (!last.isNullOrBlank()) last
                            else "Aún no hay nada que repetir, Señor."
                            handleResponse(visible)
                            memoryEnricher.persistTurn(text, visible)
                            return@launch
                        }
                        CommandMarkers.ANALYZE_SCREEN -> {
                            val analysis = analyzeScreenUseCase(text)
                            val visible = analysis.getOrNull()
                                ?: "No pude analizar la pantalla en este momento, Señor."
                            handleResponse(visible)
                            memoryEnricher.persistTurn(text, visible)
                            return@launch
                        }
                        else -> {
                            if (directResponse.startsWith("__SCREEN_ASSISTANT")) {
                                val visible = "Comando de sistema procesado, Señor."
                                handleResponse(visible)
                                memoryEnricher.persistTurn(text, visible)
                                return@launch
                            }
                        }
                    }
                    handleResponse(directResponse)
                    memoryEnricher.persistTurn(text, directResponse)
                    return@launch
                }

                // 3. Fallback: Inteligencia Artificial J.A.R.V.I.S.
                val prompt = if (aiOrchestrator.isLocalAvailable()) {
                    PromptCatalog.getJarvisLocalPrompt(userName, text)
                } else text

                val response = aiOrchestrator.processMessage(prompt)
                when (response) {
                    is AiResponse.Success -> {
                        handleResponse(response.text)
                        memoryEnricher.persistTurn(text, response.text)
                    }
                    is AiResponse.Error -> handleResponse("Error de sistema: ${response.reason}")
                    else -> handleResponse("Servidores no responden.")
                }
            } catch (e: Exception) {
                handleResponse("Fallo en el núcleo de datos.")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun handleResponse(response: String) {
        viewModelScope.launch(Dispatchers.Main) {
            // M28: Silenciar micrófono antes de hablar para evitar auto-escucha
            if (_uiState.value.isListening) {
                stopListening()
            }
            
            val cleaned = response.removePrefix("Error: ")
            lastSpokenText = cleaned
            _uiState.update { it.copy(assistantText = cleaned, animationState = AnimationState.RESPONDING) }
            characterState.animationState = AnimationState.RESPONDING
            characterState.assistantText = cleaned
            // V4 DBG-veredicto (diagnóstico, cero cambio de comportamiento):
            // dos ramas observables — speak invocado vs omitido por TTS null.
            val tts = textToSpeech
            if (tts != null) {
                Log.d("OverlayViewModel", "handleResponse: tts=speak N=${cleaned.length}")
                tts.speak(cleaned)
            } else {
                Log.d("OverlayViewModel", "handleResponse: tts=null, solo-UI N=${cleaned.length}")
            }
        }
    }

    fun toggleHistory() {
        val newState = !_uiState.value.showHistory
        _uiState.update { it.copy(showHistory = newState) }
        if (newState) loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch(ioDispatcher) {
            val turns = memoryEnricher.getRecentTurns(10)
            _uiState.update { it.copy(historyTurns = turns) }
        }
    }

    fun startListening() { speechToText?.startListening(); _uiState.update { it.copy(isListening = true) } }
    fun stopListening() { speechToText?.stopListening(); _uiState.update { it.copy(isListening = false) } }
    fun onInputChanged(text: String) { _uiState.update { it.copy(inputText = text) } }
    fun onVoiceResult(text: String) {
        // M28: Protocolo Anti-Eco J.A.R.V.I.S.
        // Evita que el asistente procese su propia voz como comandos del usuario.
        val currentState = _uiState.value.animationState
        if (currentState == AnimationState.RESPONDING || currentState == AnimationState.SPEAKING) {
            Log.d("OverlayViewModel", "Protocolo de aislamiento activo: Eco ignorado.")
            return
        }
        sendMessage(text)
    }
    fun onVoicePartialResult(partial: String) { _uiState.update { it.copy(inputText = partial) } }
    fun onVoiceError(error: String) { handleResponse("Error de voz: $error"); _uiState.update { it.copy(isListening = false) } }
    fun dismissBubble() { _uiState.update { it.copy(assistantText = "", showHelpCard = false, showHistory = false) } }
    fun changeOutfit() { characterState.nextOutfit(); _uiState.update { it.copy(currentOutfitIndex = characterState.currentOutfitIndex) } }
    fun startMonitoring() { captureScreenContextUseCase.startMonitoring(_uiState.value.monitoringIntervalMs) }
    fun stopMonitoring() { captureScreenContextUseCase.stopMonitoring() }

    /**
     * Alterna el estado del monitoreo continuo: lo inicia si está detenido, lo detiene si está activo.
     */
    fun toggleMonitoring() {
        if (_uiState.value.isMonitoring) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }

    /**
     * Actualiza el intervalo del monitoreo y reinicia si está activo.
     *
     * @param intervalMs Nuevo intervalo en milisegundos
     */
    fun setMonitoringInterval(intervalMs: Long) {
        _uiState.update { it.copy(monitoringIntervalMs = intervalMs) }
        if (_uiState.value.isMonitoring) {
            stopMonitoring()
            startMonitoring()
        }
    }

    /**
     * Alterna el estado del hotword: lo activa si está desactivado, lo desactiva si está activo.
     */
    fun toggleHotword() {
        viewModelScope.launch(ioDispatcher) {
            val newEnabled = !_uiState.value.hotwordEnabled
            hotwordPreferences.setEnabled(newEnabled)
            if (newEnabled) {
                HotwordListeningService.start(context)
            } else {
                HotwordListeningService.stop(context)
            }
        }
    }

    fun onIdleTimeout() {
        if (_uiState.value.animationState == AnimationState.IDLE) {
            _uiState.update { it.copy(animationState = AnimationState.SQUATTING) }
            characterState.animationState = AnimationState.SQUATTING
        }
    }

    fun onAnimationStateChanged(newState: AnimationState) {
        _uiState.update { it.copy(animationState = newState) }
        characterState.animationState = newState
        
        // M28: Si empieza a hablar, pausar monitoreo si fuera necesario (opcional)
        // O si termina de hablar, limpiar texto de burbuja tras un delay
        if (newState == AnimationState.IDLE) {
            viewModelScope.launch {
                delay(8000) // Mantener la burbuja 8 segundos tras terminar de hablar
                if (_uiState.value.animationState == AnimationState.IDLE) {
                    _uiState.update { it.copy(assistantText = "") }
                }
            }
        }
    }

    private fun mapPackageToOutfit(packageName: String): Int? {
        return when {
            packageName.contains("chrome") -> 6
            packageName.contains("whatsapp") -> 3
            else -> 0
        }
    }

    private fun handleMultiStepResult(result: MultiStepResult, originalText: String) {
        val message = when (result) {
            is MultiStepResult.Success -> {
                val count = result.stepResults.size
                if (count == 1) result.stepResults.first().result
                else "He completado los $count pasos de la secuencia, Señor."
            }
            is MultiStepResult.Cancelled -> "Secuencia cancelada, Señor."
            is MultiStepResult.Error -> "Fallo en el paso ${result.failedStepIndex + 1}: ${result.reason}"
        }
        
        handleResponse(message)
        
        // Persistir el turno en memoria
        viewModelScope.launch(ioDispatcher) {
            memoryEnricher.persistTurn(originalText, message)
        }
        
        // Limpiar estado multi-paso
        _uiState.update { it.copy(isMultiStep = false, executingStep = null, totalSteps = null) }
    }

    override fun onCleared() {
        super.onCleared()
        captureScreenContextUseCase.stopMonitoring()
        textToSpeech?.destroy()
        speechToText?.destroy()
    }
}
