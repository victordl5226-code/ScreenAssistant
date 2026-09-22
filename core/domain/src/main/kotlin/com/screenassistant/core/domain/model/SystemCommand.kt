package com.screenassistant.core.domain.model

sealed class SystemCommand {
    data class Call(val contactName: String) : SystemCommand()
    data class SendSms(val contact: String, val message: String) : SystemCommand()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String?) : SystemCommand()
    // O4: con hora → cancela la alarma de esa hora; sin hora (null/null) → todas (guía general).
    data class CancelAlarm(val hour: Int?, val minute: Int?) : SystemCommand()
    data class OpenApp(val appQuery: String) : SystemCommand()
    data class SearchFile(val query: String) : SystemCommand()
    data class QueueMessage(val platform: String, val contact: String, val message: String) : SystemCommand()
    data object OpenAlarms : SystemCommand()
    data class SearchGoogle(val query: String) : SystemCommand()
    data class OpenYouTube(val query: String?) : SystemCommand()
    data object OpenWhatsApp : SystemCommand()
    data class PlayMusic(val query: String?) : SystemCommand()
    data class SetVolume(val action: VolumeAction) : SystemCommand()
    data class SetLanguage(val language: AssistantLanguage) : SystemCommand()
    /**
     * Temporizador por voz.
     *
     * INVARIANTE (M1, Lote 10): `minutes ∈ [TIMER_MIN_MINUTOS, TIMER_MAX_MINUTOS]` —
     * fuera de rango es un comando inválido y NO debe ejecutarse (guard en parser y
     * en TimerAction). La fuente única es el companion (compartida con el wire:
     * AccionRegistry.poner_temporizador consulta estas constantes).
     */
    data class SetTimer(val minutes: Int) : SystemCommand()
    data class Navigate(val destination: String) : SystemCommand()
    data object OpenSettings : SystemCommand()
    data class CallNumber(val phoneNumber: String) : SystemCommand()
    data class SaveMemory(val fact: String) : SystemCommand()
    data class CreateNote(val text: String) : SystemCommand()
    // N1: "lee mis notas" → resumen de todas; "lee la nota de X" → búsqueda por contenido.
    data object ReadNotes : SystemCommand()
    data class ReadNote(val query: String) : SystemCommand()

    // ===== Fase 1: Capacidades offline =====

    // --- Calculadora ---
    data class Calculate(
        val operand1: Double,
        val operator: CalculatorOperator,
        val operand2: Double
    ) : SystemCommand()

    /**
     * MATH (ADR-MATH, P3): expresión canónica libre (`2+3*4`, `sqrt(81)`).
     * NUEVO subtipo — [Calculate] binario CONGELADO (tests + codec + Tasker).
     * INVARIANTE: `expression` es la forma CANÓNICA (no el texto libre);
     * longitud ≤ 200 (la impone [MathEvaluator]).
     */
    data class CalculateExpression(val expression: String) : SystemCommand()

    // --- Cronómetro ---
    data class Stopwatch(val action: StopwatchAction) : SystemCommand()

    // --- Info del dispositivo ---
    data class DeviceInfo(val type: DeviceInfoType) : SystemCommand()

    // --- Portapapeles ---
    data class Clipboard(
        val operation: ClipboardOperation,
        val text: String? = null
    ) : SystemCommand()

    // --- Listar contactos ---
    data object ListContacts : SystemCommand()

    // --- Info WiFi ---
    data object GetWifiInfo : SystemCommand()

    // ===== Fase 2: Capacidades offline =====

    // --- Bluetooth ---
    data class SetBluetooth(val enabled: Boolean) : SystemCommand()

    // --- Brillo ---
    data class SetBrightness(val level: Int) : SystemCommand()

    // --- Linterna ---
    data class SetFlashlight(val enabled: Boolean) : SystemCommand()

    // --- Modo avión ---
    data class SetAirplaneMode(val enabled: Boolean) : SystemCommand()

    // --- Datos móviles ---
    data class SetMobileData(val enabled: Boolean) : SystemCommand()

    // --- Abrir archivo ---
    data class OpenFile(val query: String) : SystemCommand()

    // --- Historial de llamadas ---
    data object CallHistory : SystemCommand()

    // ===== Fase 3: Capacidades offline con ML Kit =====

    // --- Leer código QR ---
    data object ScanQr : SystemCommand()

    // --- OCR offline (leer texto de foto) ---
    data object OcrScan : SystemCommand()

    // --- Traducción offline ---
    data class TranslateText(
        val text: String,
        val targetLanguage: TranslateLanguage
    ) : SystemCommand()

    // --- Reconocimiento facial ---
    data object DetectFace : SystemCommand()

    // ===== Fase 4: Hardware directo =====

    // --- Vibración ---
    data class Vibrate(val durationMs: Long) : SystemCommand()

    // --- Ubicación GPS ---
    data object GetLocation : SystemCommand()

    // --- WiFi toggle ---
    data class SetWifi(val enabled: Boolean) : SystemCommand()

    // --- Captura de cámara ---
    data class TakePhoto(val useFrontCamera: Boolean) : SystemCommand()

    // --- Personalidad J.A.R.V.I.S. ---
    data class SetAssistantMode(val mode: AssistantMode) : SystemCommand()

    companion object {
        // Fuente única de verdad del límite de caracteres de una nota por voz
        // (el parser NO trunca: el límite lo aplica la acción al guardar).
        const val MAX_NOTE_CHARS: Int = 1000

        // M1 (Lote 10): invariante del temporizador — fuente única compartida con el
        // wire (AccionRegistry.poner_temporizador consulta este rango). 24 horas = 1440
        // minutos; por debajo de 1 minuto no es un temporizador real (el wire valida
        // el mismo rango en la Fase B' del codec).
        const val TIMER_MIN_MINUTOS: Int = 1
        const val TIMER_MAX_MINUTOS: Int = 1440

        // M1 (Lote 11): mensaje del invariante del temporizador — fuente única compartida
        // por el parser (rama 3) y TimerAction (guard defensivo). El wire NO lo usa
        // (formato valor_invalido con valor recibido — canales distintos).
        const val TIMER_ERROR_MENSAJE: String = "Error: La duración debe estar entre 1 minuto y 24 horas."
    }
}

enum class VolumeAction { UP, DOWN, MAX, MIN, MUTE }

enum class AssistantLanguage { SPANISH, ENGLISH }

// ===== Fase 1: Enums para capacidades offline =====
enum class CalculatorOperator { ADD, SUBTRACT, MULTIPLY, DIVIDE }
enum class StopwatchAction { START, STOP, GET_TIME }
enum class DeviceInfoType { MODEL, BATTERY, STORAGE }
enum class ClipboardOperation { COPY, PASTE, SHOW }

// ===== Fase 3: Enums para ML Kit =====
enum class TranslateLanguage { SPANISH, ENGLISH, FRENCH, GERMAN, PORTUGUESE, CHINESE, JAPANESE }

// ===== Fase J.A.R.V.I.S.: Modos de Conciencia =====
enum class AssistantMode { CENTINELA, TACTICO, SILENCIOSO }
