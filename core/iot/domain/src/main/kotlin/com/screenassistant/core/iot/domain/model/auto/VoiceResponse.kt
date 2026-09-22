package com.screenassistant.core.iot.domain.model.auto

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Respuesta del asistente de voz para el entorno automotriz.
 *
 * Representa la respuesta que el asistente debe reproducir (TTS)
 * y/o mostrar en pantalla, optimizada para conducción hands-free.
 *
 * @property responseId Identificador único de la respuesta
 * @property commandId ID del comando que originó esta respuesta (para correlación)
 * @property speechText Texto para TTS (versión hablada, natural)
 * @property displayText Texto para mostrar en pantalla (puede ser abreviado)
 * @property type Tipo de respuesta
 * @property priority Prioridad de reproducción (interrumpe media, etc.)
 * @property timestamp Momento de generación
 * @property language Idioma de la respuesta
 * @property ttsConfig Configuración de TTS (velocidad, tono, voz)
 * @property visualContent Contenido visual opcional (cards, listas, mapas)
 * @property followUp Acciones de seguimiento sugeridas
 * @property isError Si es una respuesta de error
 * @property errorCode Código de error si aplica
 */
@Serializable
data class VoiceResponse(
    val responseId: String,
    val commandId: String,
    val speechText: String,
    val displayText: String? = null,
    val type: ResponseType = ResponseType.INFO,
    val priority: ResponsePriority = ResponsePriority.NORMAL,
    val timestamp: Instant = Clock.System.now(),
    val language: String = "es-ES",
    val ttsConfig: TTSConfig = TTSConfig(),
    val visualContent: VisualContent? = null,
    val followUp: List<FollowUpAction> = emptyList(),
    val isError: Boolean = false,
    val errorCode: String? = null,
) {
    /**
     * Texto a mostrar en pantalla (usa speechText como fallback).
     */
    val effectiveDisplayText: String
        get() = displayText ?: speechText

    /**
     * Verifica si la respuesta debe interrumpir reproducción multimedia.
     */
    val shouldInterruptMedia: Boolean
        get() = priority == ResponsePriority.HIGH || priority == ResponsePriority.CRITICAL

    /**
     * Crea una respuesta de confirmación simple.
     */
    companion object {
        fun confirm(commandId: String, message: String = "Entendido"): VoiceResponse =
            VoiceResponse(
                responseId = "resp_${System.currentTimeMillis()}",
                commandId = commandId,
                speechText = message,
                type = ResponseType.CONFIRMATION,
            )

        /** Crea una respuesta de error */
        fun error(commandId: String, message: String, errorCode: String): VoiceResponse =
            VoiceResponse(
                responseId = "resp_${System.currentTimeMillis()}",
                commandId = commandId,
                speechText = message,
                type = ResponseType.ERROR,
                priority = ResponsePriority.HIGH,
                isError = true,
                errorCode = errorCode,
            )

        /** Crea una respuesta de resultado de navegación */
        fun navigationResult(
            commandId: String,
            destination: String,
            eta: String,
            distance: String,
        ): VoiceResponse =
            VoiceResponse(
                responseId = "resp_${System.currentTimeMillis()}",
                commandId = commandId,
                speechText = "Navegando a $destination. Llegada estimada $eta, $distance de distancia.",
                displayText = "Navegando a $destination\nLlegada: $eta • $distance",
                type = ResponseType.NAVIGATION_STARTED,
                visualContent = VisualContent.NavigationCard(
                    destination = destination,
                    eta = eta,
                    distance = distance,
                ),
            )

        /** Crea una respuesta de reproducción de medios */
        fun mediaPlaying(
            commandId: String,
            title: String,
            artist: String?,
        ): VoiceResponse {
            val artistText = artist?.let { " de $it" } ?: ""
            return VoiceResponse(
                responseId = "resp_${System.currentTimeMillis()}",
                commandId = commandId,
                speechText = "Reproduciendo $title$artistText",
                displayText = "Reproduciendo\n$title$artistText",
                type = ResponseType.MEDIA_PLAYING,
                visualContent = VisualContent.MediaCard(
                    title = title,
                    artist = artist,
                ),
            )
        }

        /** Crea una respuesta de estado del vehículo */
        fun vehicleStatus(
            commandId: String,
            status: String,
        ): VoiceResponse =
            VoiceResponse(
                responseId = "resp_${System.currentTimeMillis()}",
                commandId = commandId,
                speechText = status,
                displayText = status,
                type = ResponseType.VEHICLE_STATUS,
            )

        /** Crea una respuesta que requiere confirmación visual */
        fun requiresConfirmation(
            commandId: String,
            message: String,
            confirmAction: String,
            cancelAction: String,
        ): VoiceResponse =
            VoiceResponse(
                responseId = "resp_${System.currentTimeMillis()}",
                commandId = commandId,
                speechText = "$message. ¿Confirmas?",
                displayText = message,
                type = ResponseType.CONFIRMATION_REQUIRED,
                priority = ResponsePriority.HIGH,
                followUp = listOf(
                    FollowUpAction.Confirm(confirmAction),
                    FollowUpAction.Cancel(cancelAction),
                ),
            )
    }
}

/**
 * Tipo de respuesta.
 */
enum class ResponseType {
    INFO,
    CONFIRMATION,
    CONFIRMATION_REQUIRED,
    ERROR,
    NAVIGATION_STARTED,
    NAVIGATION_UPDATED,
    NAVIGATION_ARRIVED,
    MEDIA_PLAYING,
    MEDIA_PAUSED,
    MEDIA_CHANGED,
    VEHICLE_STATUS,
    CLIMATE_CHANGED,
    SMART_HOME_CONTROL,
    TIMER_SET,
    ALARM_SET,
    REMINDER_SET,
    CALL_INITIATED,
    MESSAGE_SENT,
    GENERAL_ANSWER,
}

/**
 * Prioridad de la respuesta.
 */
enum class ResponsePriority {
    LOW,        // No interrumpe, se reproduce en background
    NORMAL,     // Reproduce normalmente, baja volumen media
    HIGH,       // Interrumpe media, reproduce inmediatamente
    CRITICAL,   // Alerta de seguridad, siempre interrumpe
}

/**
 * Configuración de TTS.
 */
@Serializable
data class TTSConfig(
    val speechRate: Float = 1.0f,      // 0.5 - 2.0
    val pitch: Float = 1.0f,           // 0.5 - 2.0
    val volume: Float = 1.0f,          // 0.0 - 1.0
    val voiceName: String? = null,     // Nombre de voz específico (ej: "es-ES-Standard-A")
    val language: String = "es-ES",
    val useEarcon: Boolean = true,     // Usar sonidos de inicio/fin
) {
    companion object {
        val DRIVING_OPTIMIZED = TTSConfig(
            speechRate = 1.1f,
            pitch = 1.0f,
            volume = 0.9f,
            useEarcon = true,
        )
    }
}

/**
 * Contenido visual para mostrar en la pantalla del coche.
 */
@Serializable
sealed class VisualContent {
    /** Card de navegación */
    @Serializable
    data class NavigationCard(
        val destination: String,
        val eta: String,
        val distance: String,
        val nextTurn: String? = null,
        val nextTurnDistance: String? = null,
    ) : VisualContent()

    /** Card de medios */
    @Serializable
    data class MediaCard(
        val title: String,
        val artist: String?,
        val albumArtUri: String? = null,
        val progress: Float = 0f,
        val duration: String? = null,
    ) : VisualContent()

    /** Card de contacto/llamada */
    @Serializable
    data class ContactCard(
        val name: String,
        val phoneNumber: String?,
        val photoUri: String? = null,
        val action: ContactAction = ContactAction.CALL,
    ) : VisualContent()

    /** Lista de opciones (POI, contactos, canciones) */
    @Serializable
    data class ListCard(
        val title: String,
        val items: List<ListItem>,
        val maxVisible: Int = 5,
    ) : VisualContent()

    /** Card de estado del vehículo */
    @Serializable
    data class VehicleStatusCard(
        val title: String,
        val metrics: List<StatusMetric>,
    ) : VisualContent()

    /** Card de clima */
    @Serializable
    data class ClimateCard(
        val driverTemp: String,
        val passengerTemp: String?,
        val fanSpeed: Int,
        val isAcOn: Boolean,
        val mode: String,
    ) : VisualContent()

    /** Card genérica con título, subtítulo e icono */
    @Serializable
    data class SimpleCard(
        val title: String,
        val subtitle: String?,
        val icon: String?,
    ) : VisualContent()
}

/**
 * Item en una lista visual.
 */
@Serializable
data class ListItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val icon: String?,
    val action: String, // Action al tocar
)

/**
 * Métrica de estado para VehicleStatusCard.
 */
@Serializable
data class StatusMetric(
    val label: String,
    val value: String,
    val unit: String?,
    val icon: String?,
    val status: MetricStatus = MetricStatus.NORMAL,
)

/**
 * Estado de una métrica.
 */
enum class MetricStatus {
    NORMAL,
    WARNING,
    CRITICAL,
    UNKNOWN,
}

/**
 * Acción de contacto.
 */
enum class ContactAction {
    CALL,
    MESSAGE,
    VIDEO_CALL,
}

/**
 * Acciones de seguimiento sugeridas.
 */
@Serializable
sealed class FollowUpAction {
    /** Confirmar acción */
    @Serializable
    data class Confirm(val actionId: String) : FollowUpAction()

    /** Cancelar */
    @Serializable
    data class Cancel(val actionId: String) : FollowUpAction()

    /** Repetir última respuesta */
    object Repeat : FollowUpAction()

    /** Ir a app específica */
    @Serializable
    data class OpenApp(val appAction: String) : FollowUpAction()

    /** Comando de voz de seguimiento */
    @Serializable
    data class VoiceCommand(val suggestedPhrase: String) : FollowUpAction()
}