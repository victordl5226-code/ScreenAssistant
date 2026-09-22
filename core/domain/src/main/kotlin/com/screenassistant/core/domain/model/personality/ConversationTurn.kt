package com.screenassistant.core.domain.model.personality

import com.screenassistant.core.domain.model.proactive.ScreenInfo
import java.time.Instant
import java.util.UUID

/**
 * Un turno individual de conversación entre el usuario y el asistente.
 * Captura el intercambio completo con su contexto.
 *
 * @property id Identificador único del turno
 * @property userMessage Mensaje enviado por el usuario
 * @property assistantResponse Respuesta generada por el asistente
 * @property timestamp Timestamp del turno en milisegundos desde epoch
 * @property screenContext Contexto de pantalla disponible en el momento del turno (puede ser null)
 * @property topic Tema principal de la conversación (puede ser null si no se detectó)
 * @property sentiment Sentimiento detectado en el mensaje del usuario (puede ser null)
 */
data class ConversationTurn(
    val id: String = UUID.randomUUID().toString(),
    val userMessage: String,
    val assistantResponse: String,
    val timestamp: Instant,
    val screenContext: ScreenInfo? = null,
    val topic: String? = null,
    val sentiment: Sentiment? = null
) {
    init {
        require(userMessage.isNotBlank()) { "El mensaje del usuario no puede estar vacío" }
        require(assistantResponse.isNotBlank()) { "La respuesta del asistente no puede estar vacía" }
    }

    /**
     * Sentimiento detectado en el mensaje del usuario.
     * Se usa para adaptar el tono de las respuestas.
     */
    enum class Sentiment {
        /** El usuario parece contento o satisfecho */
        POSITIVE,
        /** El usuario parece frustrado o molesto */
        NEGATIVE,
        /** El usuario está neutral o no se detectó sentimiento claro */
        NEUTRAL,
        /** El usuario tiene una urgencia o solicitud urgente */
        URGENT
    }

    companion object {
        /**
         * Tiempo máximo de vida de un turno para ser considerado "reciente" en minutos.
         */
        const val RECENT_TURNS_WINDOW_MINUTES: Long = 30
    }
}
