package com.screenassistant.core.iot.domain.model.auto

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Comando de voz procesado para el entorno automotriz.
 *
 * Representa un comando de voz que ha sido transcrito y clasificado
 * (NLU) y está listo para ser ejecutado en el contexto del coche.
 *
 * Diferencia con SystemCommand del core:domain:
 * - Incluye contexto automotriz (velocidad, marcha, restricciones UX)
 * - Optimizado para ejecución hands-free
 * - Incluye confianza del ASR/NLU
 * - Soporta comandos multi-intent (ej: "navega a casa y pon música")
 *
 * @property commandId Identificador único del comando
 * @property rawTranscript Transcripción cruda del ASR
 * @property normalizedText Texto normalizado (lowercase, sin puntuación)
 * @property intents Intents detectados (puede ser múltiple)
 * @property entities Entidades extraídas (destino, contacto, canción, etc.)
 * @property confidence Confianza global del NLU (0.0 - 1.0)
 * @property language Idioma detectado
 * @property timestamp Momento del comando
 * @property context Contexto automotriz al momento del comando
 * @property requiresVisualConfirmation Si requiere confirmación visual (restringido en conducción)
 * @property executionMode Modo de ejecución (inmediato, en cola, diferido)
 * @property source Fuente del comando (micrófono coche, teléfono, watch)
 */
@Serializable
data class VoiceCommand(
    val commandId: String,
    val rawTranscript: String,
    val normalizedText: String,
    val intents: List<VoiceIntent>,
    @Contextual val entities: Map<String, VoiceEntity>,
    val confidence: Double,
    val language: String = "es-ES",
    val timestamp: Instant = Clock.System.now(),
    val context: VoiceCommandContext = VoiceCommandContext(),
    val requiresVisualConfirmation: Boolean = false,
    val executionMode: ExecutionMode = ExecutionMode.IMMEDIATE,
    val source: VoiceSource = VoiceSource.CAR_MICROPHONE,
) {
    /**
     * Verifica si el comando tiene alta confianza para ejecución automática.
     */
    val isHighConfidence: Boolean
        get() = confidence >= 0.85

    /**
     * Verifica si el comando puede ejecutarse sin confirmación visual.
     */
    val canExecuteHeadless: Boolean
        get() = !requiresVisualConfirmation && isHighConfidence

    /**
     * Obtiene el intent principal (mayor confianza).
     */
    val primaryIntent: VoiceIntent?
        get() = intents.maxByOrNull { it.confidence }

    /**
     * Obtiene una entidad por tipo.
     */
    fun getEntity(type: String): VoiceEntity? = entities[type]

    /**
     * Verifica si contiene un intent específico.
     */
    fun hasIntent(intentType: VoiceIntentType): Boolean =
        intents.any { it.type == intentType }
}

/**
 * Intent de voz clasificado.
 */
@Serializable
data class VoiceIntent(
    val type: VoiceIntentType,
    val confidence: Double,
    val parameters: Map<String, String> = emptyMap(),
) {
    /** Verifica si el intent es accionable (confianza suficiente) */
    val isActionable: Boolean
        get() = confidence >= 0.7
}

/**
 * Tipos de intent soportados en el coche.
 */
enum class VoiceIntentType {
    // Navegación
    NAVIGATE_TO,
    NAVIGATE_HOME,
    NAVIGATE_WORK,
    FIND_POI,
    CANCEL_NAVIGATION,
    SHOW_MAP,
    // Medios
    PLAY_MEDIA,
    PAUSE_MEDIA,
    NEXT_TRACK,
    PREVIOUS_TRACK,
    SET_VOLUME,
    SET_SHUFFLE,
    SET_REPEAT,
    // Clima
    SET_TEMPERATURE,
    SET_FAN_SPEED,
    TOGGLE_AC,
    TOGGLE_DEFROST,
    TOGGLE_SEAT_HEATING,
    TOGGLE_STEERING_HEATING,
    // Vehículo
    LOCK_DOORS,
    UNLOCK_DOORS,
    OPEN_TRUNK,
    OPEN_CHARGE_PORT,
    CHECK_VEHICLE_STATUS,
    // Comunicación
    CALL_CONTACT,
    SEND_MESSAGE,
    READ_MESSAGES,
    // Asistente general
    GENERAL_QUERY,
    SET_TIMER,
    SET_ALARM,
    SET_REMINDER,
    // Hogar inteligente (desde el coche)
    CONTROL_SMART_HOME,
    ACTIVATE_SCENE,
    // App
    OPEN_APP,
    CLOSE_APP,
    // Desconocido
    UNKNOWN,
}

/**
 * Entidad extraída del comando de voz.
 */
@Serializable
data class VoiceEntity(
    val type: VoiceEntityType,
    val value: String,
    val confidence: Double,
    @Contextual val metadata: Map<String, String> = emptyMap(),
)

/**
 * Tipos de entidad.
 */
enum class VoiceEntityType {
    // Lugares
    DESTINATION,
    ADDRESS,
    POI_CATEGORY,
    // Contactos
    CONTACT_NAME,
    PHONE_NUMBER,
    // Medios
    SONG_NAME,
    ARTIST_NAME,
    ALBUM_NAME,
    PLAYLIST_NAME,
    GENRE,
    // Vehículo
    TEMPERATURE_VALUE,
    FAN_SPEED_LEVEL,
    SEAT_POSITION,
    // Tiempo
    DURATION,
    TIME_OF_DAY,
    DATE,
    // Hogar
    SCENE_NAME,
    DEVICE_NAME,
    ROOM_NAME,
    // General
    NUMBER,
    QUANTITY,
    YES_NO,
}

/**
 * Contexto automotriz al momento del comando.
 */
@Serializable
data class VoiceCommandContext(
    val isDriving: Boolean = false,
    val speedKmh: Double = 0.0,
    val gear: String = "UNKNOWN",
    val isPassenger: Boolean = false,
    val screenAvailable: Boolean = true,
    val microphoneAvailable: Boolean = true,
    val networkType: NetworkType = NetworkType.UNKNOWN,
    val batteryLevel: Double? = null,
)

/**
 * Tipo de red disponible.
 */
enum class NetworkType {
    WIFI,
    MOBILE_5G,
    MOBILE_4G,
    MOBILE_3G,
    OFFLINE,
    UNKNOWN,
}

/**
 * Modo de ejecución del comando.
 */
enum class ExecutionMode {
    /** Ejecutar inmediatamente */
    IMMEDIATE,
    /** Poner en cola para ejecutar cuando sea seguro */
    QUEUED,
    /** Ejecutar cuando el vehículo se detenga */
    DEFERRED_UNTIL_PARKED,
    /** Requiere confirmación del usuario */
    REQUIRES_CONFIRMATION,
    /** Solo informativo, no ejecuta acción */
    INFO_ONLY,
}

/**
 * Fuente del comando de voz.
 */
enum class VoiceSource {
    CAR_MICROPHONE,
    PHONE_MICROPHONE,
    WATCH_MICROPHONE,
    HEADSET_MICROPHONE,
    REMOTE_APP,
}