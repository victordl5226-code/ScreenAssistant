package com.screenassistant.core.domain.engine

import com.screenassistant.core.domain.model.proactive.AggregatedContext
import com.screenassistant.core.domain.model.proactive.ProactiveAction
import com.screenassistant.core.domain.model.proactive.ProactivePriority
import com.screenassistant.core.domain.model.proactive.ProactiveSuggestion
import com.screenassistant.core.domain.model.proactive.UserPattern
import javax.inject.Inject

/**
 * Motor predictivo que genera sugerencias basadas en patrones establecidos del usuario.
 *
 * Función pura: [generateSuggestions] filtra patrones que coinciden con el contexto
 * temporal actual, los ordena por confianza y genera [ProactiveSuggestion]es.
 * No tiene side effects — el scheduling y la presentación son responsabilidad del caller.
 */
class PredictiveEngine @Inject constructor() {

    /**
     * Genera sugerencias basadas en patrones establecidos del usuario.
     *
     * Filtra los patrones que coinciden con el contexto temporal actual
     * (día de la semana y rango horario), los ordena por confianza
     * descendente y genera un máximo de [MAX_SUGGESTIONS] sugerencias.
     *
     * @param currentContext Contexto agregado del momento actual
     * @param establishedPatterns Lista de patrones establecidos del usuario
     * @return Lista de sugerencias generadas (puede ser vacía)
     */
    fun generateSuggestions(
        currentContext: AggregatedContext,
        establishedPatterns: List<UserPattern>
    ): List<ProactiveSuggestion> {
        if (establishedPatterns.isEmpty()) return emptyList()

        val now = currentContext.temporal
        return establishedPatterns
            .filter { matchesCurrentContext(it, now) }
            .sortedByDescending { it.confidence }
            .take(MAX_SUGGESTIONS)
            .map { pattern -> buildSuggestion(pattern) }
    }

    /**
     * Evalúa si un patrón coincide con el contexto temporal actual.
     *
     * Verifica que el día de la semana y la hora actual estén dentro
     * del rango horario registrado en el patrón.
     *
     * @param pattern Patrón a evaluar
     * @param temporal Contexto temporal actual
     * @return true si el patrón coincide con el contexto
     */
    private fun matchesCurrentContext(
        pattern: UserPattern,
        temporal: com.screenassistant.core.domain.model.TemporalContext
    ): Boolean {
        val currentHour = temporal.dateTime.hour
        return pattern.context.dayOfWeek == temporal.dayOfWeek &&
            currentHour in pattern.context.hourStart..pattern.context.hourEnd
    }

    /**
     * Construye la [ProactiveSuggestion] a partir de un patrón.
     *
     * @param pattern Patrón que generó la sugerencia
     * @return Sugerencia proactiva con título, mensaje y acción
     */
    private fun buildSuggestion(pattern: UserPattern): ProactiveSuggestion {
        val message = buildString {
            append("Detecté que sueles usar esta acción ")
            append("los ${pattern.context.dayOfWeek.name.lowercase()} ")
            append("entre las ${pattern.context.hourStart}:00 y ${pattern.context.hourEnd}:00. ")
            append("¿Quieres que la ejecute ahora?")
        }
        return ProactiveSuggestion(
            title = "Acción recurrente detectada",
            message = message,
            action = ProactiveAction.SuggestSystemAction(
                actionId = pattern.action
            ),
            priority = ProactivePriority.MEDIUM,
            source = "pattern:${pattern.id}"
        )
    }

    companion object {
        /** Número máximo de sugerencias que genera el motor en una invocación. */
        const val MAX_SUGGESTIONS = 3
    }
}
