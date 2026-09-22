package com.screenassistant.core.domain.engine

import com.screenassistant.core.domain.model.proactive.AggregatedContext
import com.screenassistant.core.domain.model.proactive.ProactiveAction
import com.screenassistant.core.domain.model.proactive.ProactiveRule
import com.screenassistant.core.domain.model.proactive.ProactiveSuggestion
import com.screenassistant.core.domain.model.proactive.RuleCondition
import java.time.Clock
import javax.inject.Inject

/**
 * Motor de evaluación de reglas proactivas.
 *
 * Función pura: [evaluate] confronta las reglas contra el contexto agregado
 * y retorna una lista de sugerencias. No tiene side effects —
 * el scheduling y la presentación son responsabilidad del caller.
 *
 * @constructor Crea el motor con un [Clock] inyectable para testing determinístico.
 * @param clock Reloj inyectable (patrón GetTemporalContextUseCase)
 */
class ProactiveEngine @Inject constructor(
    private val clock: Clock
) {

    /**
     * Evalúa todas las reglas habilitadas contra el contexto actual.
     *
     * @param rules Lista de reglas proactivas a evaluar
     * @param context Contexto agregado del momento
     * @return Lista de sugerencias generadas (puede ser vacía)
     */
    fun evaluate(rules: List<ProactiveRule>, context: AggregatedContext): List<ProactiveSuggestion> {
        return rules
            .filter { it.enabled }
            .filter { evaluateConditions(it, context) }
            .map { buildSuggestion(it, context) }
    }

    /**
     * Evalúa una regla individual contra el contexto.
     *
     * @param rule Regla a evaluar
     * @param context Contexto agregado del momento
     * @return true si la regla se activa (todas las condiciones se cumplen)
     */
    fun evaluate(rule: ProactiveRule, context: AggregatedContext): Boolean {
        if (!rule.enabled) return false
        if (rule.conditions.isEmpty()) return false
        return evaluateConditions(rule, context)
    }

    /**
     * Evalúa que todas las condiciones de la regla se cumplan (lógica AND).
     */
    private fun evaluateConditions(rule: ProactiveRule, context: AggregatedContext): Boolean {
        return rule.conditions.all { evaluateCondition(it, context) }
    }

    /**
     * Evalúa una condición individual contra el contexto.
     *
     * Soporta combinadores lógicos: [RuleCondition.And], [RuleCondition.Or]
     * y [RuleCondition.Not] para construir lógica compleja de activación.
     *
     * @param condition Condición a evaluar
     * @param context Contexto agregado del momento
     * @return true si la condición se cumple
     */
    fun evaluateCondition(condition: RuleCondition, context: AggregatedContext): Boolean {
        return when (condition) {
            is RuleCondition.TimeRange -> {
                val time = context.temporal.dateTime.toLocalTime()
                time >= condition.start && time <= condition.end
            }

            is RuleCondition.DayOfWeek -> {
                context.temporal.dayOfWeek in condition.days
            }

            is RuleCondition.BatteryBelow -> {
                context.battery.level <= condition.threshold
            }

            is RuleCondition.BatteryAbove -> {
                context.battery.level >= condition.threshold
            }

            is RuleCondition.IsCharging -> {
                context.battery.isCharging
            }

            is RuleCondition.HasUpcomingEvent -> {
                val now = java.time.Instant.now(clock).toEpochMilli()
                val withinMillis = condition.withinMinutes * 60 * 1000L
                context.upcomingEvents.any { event ->
                    event.startMillis in now..(now + withinMillis)
                }
            }

            is RuleCondition.ScreenContains -> {
                context.screen.text.contains(condition.text, ignoreCase = true)
            }

            is RuleCondition.AppInForeground -> {
                val pkg = context.screen.packageName ?: return false
                pkg == condition.packageName
            }

            is RuleCondition.AtLocation -> {
                context.location == condition.location
            }

            is RuleCondition.IsWeekend -> {
                context.temporal.esFinDeSemana
            }

            is RuleCondition.IsConnected -> {
                context.connectivity.isConnected
            }

            is RuleCondition.And -> {
                evaluateCondition(condition.left, context) &&
                    evaluateCondition(condition.right, context)
            }

            is RuleCondition.Or -> {
                evaluateCondition(condition.left, context) ||
                    evaluateCondition(condition.right, context)
            }

            is RuleCondition.Not -> {
                !evaluateCondition(condition.condition, context)
            }
        }
    }

    /**
     * Genera la [ProactiveSuggestion] cuando una regla se activa.
     *
     * @param rule Regla que se activó
     * @param context Contexto agregado del momento
     * @return Sugerencia proactiva con id, título, mensaje y acción
     */
    fun buildSuggestion(rule: ProactiveRule, context: AggregatedContext): ProactiveSuggestion {
        val message = buildMessage(rule, context)
        return ProactiveSuggestion(
            id = "rule_${rule.id}_${clock.millis()}",
            title = rule.name,
            message = message,
            action = rule.action,
            priority = rule.priority,
            source = "rule:${rule.id}"
        )
    }

    /**
     * Construye el mensaje descriptivo de la sugerencia según el tipo de acción.
     */
    private fun buildMessage(rule: ProactiveRule, context: AggregatedContext): String {
        return when (val action = rule.action) {
            is ProactiveAction.SuggestSystemAction -> "Acción disponible: ${action.actionId}"
            is ProactiveAction.ShowNotification -> action.body
            is ProactiveAction.Speak -> action.message
            is ProactiveAction.SuggestAutomation ->
                "Detecté un patrón recurrente. ¿Quieres automatizarlo?"
        }
    }
}
