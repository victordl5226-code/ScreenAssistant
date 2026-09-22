package com.screenassistant.core.domain.model.personality

import java.time.Duration
import java.time.Instant

/**
 * Contexto acumulado de la conversación actual.
 * Se usa para mantener coherencia en el tono y contenido de las respuestas.
 *
 * @property recentTurns Últimos turnos de la conversación (más recientes al final)
 * @property currentTopic Tema actual de la conversación (puede ser null)
 * @property userMood Sentimiento general detectado del usuario
 * @property referencedEntities Entidades (personas, lugares, apps) mencionadas en la conversación
 * @property timeSinceLastInteraction Duración desde la última interacción del usuario
 */
data class ConversationContext(
    val recentTurns: List<ConversationTurn> = emptyList(),
    val currentTopic: String? = null,
    val userMood: ConversationTurn.Sentiment = ConversationTurn.Sentiment.NEUTRAL,
    val referencedEntities: Set<String> = emptySet(),
    val timeSinceLastInteraction: Duration = Duration.ZERO
) {
    /**
     * Indica si la conversación está "activa" (hubo interacción reciente).
     * Se usa para decidir si mantener el contexto o empezar una conversación nueva.
     */
    val isActive: Boolean
        get() = timeSinceLastInteraction.toMinutes() <= ConversationTurn.RECENT_TURNS_WINDOW_MINUTES

    /**
     * Número de turnos en la conversación actual.
     */
    val turnCount: Int
        get() = recentTurns.size

    companion object {
        /**
         * Crea un contexto de conversación vacío.
         *
         * @return ConversationContext sin turnos ni contexto previo
         */
        fun empty(): ConversationContext = ConversationContext()

        /**
         * Crea un contexto de conversación a partir de una lista de turnos.
         * Calcula automáticamente el mood predominante y las entidades referenciadas.
         *
         * @param turns Lista de turnos de la conversación
         * @param now Timestamp actual para calcular timeSinceLastInteraction
         * @return ConversationContext con mood y entidades calculados
         */
        fun fromTurns(
            turns: List<ConversationTurn>,
            now: Instant = Instant.now()
        ): ConversationContext {
            if (turns.isEmpty()) return empty()

            val lastTurn = turns.last()
            val timeSince = Duration.between(lastTurn.timestamp, now)

            val mood = turns
                .mapNotNull { it.sentiment }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: ConversationTurn.Sentiment.NEUTRAL

            return ConversationContext(
                recentTurns = turns,
                currentTopic = lastTurn.topic,
                userMood = mood,
                referencedEntities = emptySet(),
                timeSinceLastInteraction = timeSince
            )
        }
    }
}
