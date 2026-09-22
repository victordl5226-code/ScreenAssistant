package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.CommandSequence
import com.screenassistant.core.domain.model.CommandStep
import com.screenassistant.core.domain.model.MultiStepResult
import com.screenassistant.core.domain.model.StepFeedback
import com.screenassistant.core.domain.model.StepResult
import com.screenassistant.core.domain.model.StepStatus
import com.screenassistant.core.domain.model.SystemCommand
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementación de MultiStepExecutor.
 *
 * Inyecta:
 * - SystemAction: para ejecutar comandos del sistema
 * - CoroutineDispatcher: para control de hilos (testable con TestCoroutineDispatcher)
 * - maxSteps: límite configurable de pasos por secuencia (default 10)
 *
 * Emite feedback via MutableStateFlow (StateFlow) para UI reactiva.
 */
class MultiStepExecutorImpl @Inject constructor(
    private val systemAction: SystemAction,
    private val dispatcher: CoroutineDispatcher,
    private val maxSteps: Int = 10
) : MultiStepExecutor {

    private val _stepFeedback = MutableStateFlow<StepFeedback?>(null)
    override val stepFeedback: StateFlow<StepFeedback?> = _stepFeedback.asStateFlow()

    @Volatile
    private var _isCancelled = false

    override suspend fun execute(sequence: CommandSequence): MultiStepResult {
        if (sequence.steps.size > maxSteps) {
            return MultiStepResult.Error(
                0,
                "Límite de $maxSteps pasos por secuencia superado (${sequence.steps.size})",
                emptyList()
            )
        }

        _isCancelled = false
        val completedSteps = mutableListOf<StepResult>()

        try {
            for (stepIndex in sequence.steps.indices) {
                if (_isCancelled) {
                    return MultiStepResult.Cancelled(completedSteps)
                }
                val step = sequence.steps[stepIndex]
                val totalSteps = sequence.steps.size
                val stepNum = stepIndex + 1

                // Emit feedback: STARTING
                _stepFeedback.value = StepFeedback(
                    stepIndex = stepNum,
                    totalSteps = totalSteps,
                    status = StepStatus.STARTING,
                    description = "Iniciando paso $stepNum/$totalSteps...",
                    step = step
                )

                delay(50)

                // Emit feedback: EXECUTING
                _stepFeedback.value = StepFeedback(
                    stepIndex = stepNum,
                    totalSteps = totalSteps,
                    status = StepStatus.EXECUTING,
                    description = "Ejecutando paso $stepNum/$totalSteps: ${buildStepDescription(step)}",
                    step = step
                )

                val result = executeStep(step)
                val stepResult = StepResult(step = step, result = result)
                completedSteps.add(stepResult)

                // Emit feedback: COMPLETED or FAILED
                val status = if (result.startsWith("Error:")) StepStatus.FAILED else StepStatus.COMPLETED
                _stepFeedback.value = StepFeedback(
                    stepIndex = stepNum,
                    totalSteps = totalSteps,
                    status = status,
                    description = if (result.startsWith("Error:")) {
                        "Paso $stepNum/$totalSteps falló: $result"
                    } else {
                        "Paso $stepNum/$totalSteps completado"
                    },
                    step = step
                )

                if (result.startsWith("Error:")) {
                    return MultiStepResult.Error(stepIndex, result, completedSteps)
                }

                delay(100)
            }

            return MultiStepResult.Success(completedSteps)
        } catch (e: kotlinx.coroutines.CancellationException) {
            return MultiStepResult.Cancelled(completedSteps)
        } catch (e: Exception) {
            val errorMsg = "Error inesperado: ${e.message}"
            _stepFeedback.value = _stepFeedback.value?.copy(
                status = StepStatus.FAILED,
                description = errorMsg
            )
            return MultiStepResult.Error(completedSteps.size, errorMsg, completedSteps)
        }
    }

    override fun cancel() {
        _isCancelled = true
        _stepFeedback.value = StepFeedback(
            stepIndex = 0,
            totalSteps = 0,
            status = StepStatus.CANCELLED,
            description = "Secuencia cancelada por el usuario"
        )
    }

    private suspend fun executeStep(step: CommandStep): String {
        return when (step) {
            is CommandStep.Command -> {
                // Ejecutar SystemCommand vía SystemAction en el dispatcher inyectado
                withContext(dispatcher) {
                    systemAction.execute(step.command)
                }.let { result ->
                    when (result) {
                        is com.screenassistant.core.domain.model.ActionResult.Success -> result.message
                        is com.screenassistant.core.domain.model.ActionResult.Error -> "Error: ${result.reason}"
                    }
                }
            }
            is CommandStep.Marker -> {
                // Marcadores de UI: no pasan por SystemAction, devuelven el marcador
                step.marker
            }
        }
    }

    private fun buildStepDescription(step: CommandStep): String {
        return when (step) {
            is CommandStep.Command -> commandToDescription(step.command)
            is CommandStep.Marker -> markerToDescription(step.marker)
        }
    }

    private fun commandToDescription(command: com.screenassistant.core.domain.model.SystemCommand): String {
        return when (command) {
            is com.screenassistant.core.domain.model.SystemCommand.Call -> "Llamando a ${command.contactName}"
            is com.screenassistant.core.domain.model.SystemCommand.CallNumber -> "Llamando al ${command.phoneNumber}"
            is com.screenassistant.core.domain.model.SystemCommand.SetAlarm -> "Poniendo alarma a ${command.hour}:${command.minute.toString().padStart(2, '0')}"
            is com.screenassistant.core.domain.model.SystemCommand.CancelAlarm -> "Cancelando alarma"
            is com.screenassistant.core.domain.model.SystemCommand.OpenApp -> "Abriendo ${command.appQuery}"
            is com.screenassistant.core.domain.model.SystemCommand.SearchGoogle -> "Buscando en Google: ${command.query}"
            is com.screenassistant.core.domain.model.SystemCommand.SetVolume -> "Ajustando volumen a ${command.action}"
            is com.screenassistant.core.domain.model.SystemCommand.SetLanguage -> "Cambiando idioma a ${command.language}"
            is com.screenassistant.core.domain.model.SystemCommand.SetTimer -> "Poniendo temporizador de ${command.minutes} minutos"
            is com.screenassistant.core.domain.model.SystemCommand.Navigate -> "Navegando a ${command.destination}"
            is com.screenassistant.core.domain.model.SystemCommand.OpenSettings -> "Abriendo ajustes"
            is com.screenassistant.core.domain.model.SystemCommand.SendSms -> "Enviando SMS a ${command.contact}"
            is com.screenassistant.core.domain.model.SystemCommand.SaveMemory -> "Recordando: ${command.fact}"
            is com.screenassistant.core.domain.model.SystemCommand.CreateNote -> "Creando nota: ${command.text}"
            is com.screenassistant.core.domain.model.SystemCommand.ReadNotes -> "Leyendo notas"
            is com.screenassistant.core.domain.model.SystemCommand.ReadNote -> "Leyendo nota: ${command.query}"
            is com.screenassistant.core.domain.model.SystemCommand.OpenAlarms -> "Abriendo alarmas"
            is com.screenassistant.core.domain.model.SystemCommand.OpenYouTube -> "Abriendo YouTube: ${command.query}"
            is com.screenassistant.core.domain.model.SystemCommand.OpenWhatsApp -> "Abriendo WhatsApp"
            is com.screenassistant.core.domain.model.SystemCommand.PlayMusic -> "Reproduciendo música"
            is com.screenassistant.core.domain.model.SystemCommand.SearchFile -> "Buscando archivo: ${command.query}"
            is com.screenassistant.core.domain.model.SystemCommand.QueueMessage -> "Encolando mensaje"
            // ===== Fase 1: Capacidades offline =====
            is com.screenassistant.core.domain.model.SystemCommand.Calculate -> "Calculando: ${command.operand1} ${when(command.operator) { com.screenassistant.core.domain.model.CalculatorOperator.ADD -> "+"; com.screenassistant.core.domain.model.CalculatorOperator.SUBTRACT -> "-"; com.screenassistant.core.domain.model.CalculatorOperator.MULTIPLY -> "*"; com.screenassistant.core.domain.model.CalculatorOperator.DIVIDE -> "/" }} ${command.operand2}"
            // MATH (ADR-MATH, P3): rama obligada por el when exhaustivo (sin else).
            is com.screenassistant.core.domain.model.SystemCommand.CalculateExpression -> "Calculando: ${command.expression}"
            is com.screenassistant.core.domain.model.SystemCommand.Stopwatch -> "Cronómetro"
            is com.screenassistant.core.domain.model.SystemCommand.DeviceInfo -> "Info del dispositivo"
            is com.screenassistant.core.domain.model.SystemCommand.Clipboard -> "Portapapeles"
            is com.screenassistant.core.domain.model.SystemCommand.ListContacts -> "Listando contactos"
            is com.screenassistant.core.domain.model.SystemCommand.GetWifiInfo -> "Info WiFi"
            // ===== Fase 2: Capacidades offline =====
            is com.screenassistant.core.domain.model.SystemCommand.SetBluetooth -> if (command.enabled) "Activando Bluetooth" else "Desactivando Bluetooth"
            is com.screenassistant.core.domain.model.SystemCommand.SetBrightness -> "Ajustando brillo"
            is com.screenassistant.core.domain.model.SystemCommand.SetFlashlight -> if (command.enabled) "Encendiendo linterna" else "Apagando linterna"
            is com.screenassistant.core.domain.model.SystemCommand.SetAirplaneMode -> if (command.enabled) "Activando modo avión" else "Desactivando modo avión"
            is com.screenassistant.core.domain.model.SystemCommand.SetMobileData -> if (command.enabled) "Activando datos móviles" else "Desactivando datos móviles"
            is com.screenassistant.core.domain.model.SystemCommand.OpenFile -> "Abriendo archivo: ${command.query}"
            is com.screenassistant.core.domain.model.SystemCommand.CallHistory -> "Mostrando historial de llamadas"
            // ===== Fase 3: Capacidades offline con ML Kit =====
            is com.screenassistant.core.domain.model.SystemCommand.ScanQr -> "Escaneando código QR"
            is com.screenassistant.core.domain.model.SystemCommand.OcrScan -> "Leyendo texto de pantalla"
            is com.screenassistant.core.domain.model.SystemCommand.TranslateText -> "Traduciendo texto"
            is com.screenassistant.core.domain.model.SystemCommand.DetectFace -> "Detectando caras"
            // ===== Fase 4: Hardware directo =====
            is com.screenassistant.core.domain.model.SystemCommand.Vibrate -> "Vibrando dispositivo"
            is com.screenassistant.core.domain.model.SystemCommand.GetLocation -> "Obteniendo ubicación GPS"
            is com.screenassistant.core.domain.model.SystemCommand.SetWifi -> if (command.enabled) "Activando WiFi" else "Desactivando WiFi"
            is com.screenassistant.core.domain.model.SystemCommand.TakePhoto -> "Tomando foto con cámara"
            is SystemCommand.SetAssistantMode -> "Cambiando modo a ${command.mode}"
        }
    }

    private fun markerToDescription(marker: String): String {
        return when (marker) {
            com.screenassistant.core.domain.model.CommandMarkers.HELP -> "Mostrando ayuda"
            com.screenassistant.core.domain.model.CommandMarkers.REPEAT -> "Repitiendo última respuesta"
            com.screenassistant.core.domain.model.CommandMarkers.START_MONITORING -> "Activando monitoreo"
            com.screenassistant.core.domain.model.CommandMarkers.STOP_MONITORING -> "Deteniendo monitoreo"
            com.screenassistant.core.domain.model.CommandMarkers.ANALYZE_SCREEN -> "Analizando pantalla"
            else -> "Ejecutando marcador: $marker"
        }
    }
}