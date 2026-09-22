package com.screenassistant.core.domain.model.proactive

import java.time.Instant
import java.util.UUID

/**
 * Resultado de la evaluación de las reglas proactivas contra el contexto actual.
 * Puede ser una sugerencia concreta o nada (el asistente no tiene nada que decir).
 */
sealed class ProactiveDecision {

    /**
     * El sistema tiene una sugerencia para el usuario.
     *
     * @property suggestion Sugerencia concreta a presentar
     */
    data class Suggest(val suggestion: ProactiveSuggestion) : ProactiveDecision()

    /**
     * No hay ninguna sugerencia relevante para el contexto actual.
     * El asistente permanece en silencio.
     */
    object Nothing : ProactiveDecision()
}

/**
 * Sugerencia proactiva que el asistente presenta al usuario.
 * Se genera a partir de [ProactiveRule]s que se activaron con el [AggregatedContext] actual.
 *
 * @property id Identificador único de la sugerencia
 * @property title Título corto de la sugerencia
 * @property message Descripción detallada de la sugerencia
 * @property action Acción que el usuario puede aceptar
 * @property priority Prioridad de la sugerencia para orden de presentación
 * @property source Identificador de la regla o patrón que generó esta sugerencia
 */
data class ProactiveSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val action: ProactiveAction,
    val priority: ProactivePriority = ProactivePriority.MEDIUM,
    val source: String
) {
    init {
        require(title.isNotBlank()) { "El título de la sugerencia no puede estar vacío" }
        require(message.isNotBlank()) { "El mensaje de la sugerencia no puede estar vacío" }
        require(source.isNotBlank()) { "La fuente de la sugerencia no puede estar vacía" }
    }

    /**
     * Timestamp de cuándo se generó esta sugerencia.
     * Se usa para calcular expiración y evitar sugerencias obsoletas.
     */
    val generatedAt: Instant = Instant.now()

    companion object {
        /**
         * Tiempo máximo de vida de una sugerencia en minutos.
         * Después de este tiempo, la sugerencia se considera obsoleta.
         */
        const val MAX_LIFETIME_MINUTES: Long = 60

        /**
         * Crea una sugerencia simplificada con solo título y mensaje.
         * La acción es [ProactiveAction.ShowNotification] por defecto.
         *
         * @param title Título de la sugerencia
         * @param message Mensaje descriptivo
         * @param priority Prioridad (por defecto MEDIUM)
         * @param source Regla o patrón que la generó
         * @return ProactiveSuggestion con acción de notificación por defecto
         */
        fun notify(
            title: String,
            message: String,
            priority: ProactivePriority = ProactivePriority.MEDIUM,
            source: String
        ): ProactiveSuggestion = ProactiveSuggestion(
            title = title,
            message = message,
            action = ProactiveAction.ShowNotification(title = title, body = message),
            priority = priority,
            source = source
        )
    }
}
