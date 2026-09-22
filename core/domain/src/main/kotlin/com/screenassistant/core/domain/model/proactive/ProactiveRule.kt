package com.screenassistant.core.domain.model.proactive

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * Prioridad de una regla proactiva o sugerencia.
 * Determina el orden de presentación y el nivel de intrusividad.
 *
 * De menor a mayor prioridad: [LOW] → [MEDIUM] → [HIGH] → [URGENT].
 */
enum class ProactivePriority {
    /** Baja prioridad: sugerencias informativas, no urgentes */
    LOW,
    /** Prioridad media: acciones útiles en contexto */
    MEDIUM,
    /** Alta prioridad: acciones importantes que el usuario debería considerar */
    HIGH,
    /** Urgencia máxima: requiere atención inmediata */
    URGENT
}

/**
 * Acción que el asistente puede ejecutar como respuesta a una regla proactiva.
 */
sealed class ProactiveAction {

    /**
     * Sugerir una acción del sistema (abrir app, cambiar configuración, etc.).
     *
     * @property actionId Identificador de la acción del sistema
     * @property params Parámetros opcionales para la acción
     */
    data class SuggestSystemAction(
        val actionId: String,
        val params: Map<String, String> = emptyMap()
    ) : ProactiveAction()

    /**
     * Mostrar una notificación con información contextual.
     *
     * @property title Título de la notificación
     * @property body Cuerpo del mensaje
     */
    data class ShowNotification(
        val title: String,
        val body: String
    ) : ProactiveAction()

    /**
     * Hablar al usuario usando texto a voz.
     *
     * @property message Mensaje a spoken aloud
     */
    data class Speak(val message: String) : ProactiveAction()

    /**
     * Sugerir una automatización que el usuario puede aprobar o rechazar.
     *
     * @property name Nombre descriptivo de la automatización
     * @property description Descripción de qué hace la automatización
     */
    data class SuggestAutomation(
        val name: String,
        val description: String
    ) : ProactiveAction()
}

/**
 * Condición que se evalúa contra un [AggregatedContext] para determinar
 * si una [ProactiveRule] debe activarse.
 *
 * Las condiciones se combinan mediante [And], [Or] y [Not] para
 * construir lógica compleja de activación.
 */
sealed class RuleCondition {

    /**
     * Evalúa si la hora actual está dentro de un rango.
     *
     * @property start Hora de inicio del rango (inclusiva)
     * @property end Hora de fin del rango (inclusiva)
     */
    data class TimeRange(val start: LocalTime, val end: LocalTime) : RuleCondition()

    /**
     * Evalúa si el día actual coincide con alguno de los días especificados.
     *
     * @property days Conjunto de días de la semana que activan la condición
     */
    data class DayOfWeek(val days: Set<java.time.DayOfWeek>) : RuleCondition()

    /**
     * Evalúa si el nivel de batería es menor al umbral dado.
     *
     * @property threshold Umbral máximo de batería (exclusivo)
     */
    data class BatteryBelow(val threshold: Int) : RuleCondition() {
        init {
            require(threshold in 0..100) { "El umbral debe estar entre 0 y 100, recibido: $threshold" }
        }
    }

    /**
     * Evalúa si el nivel de batería es mayor al umbral dado.
     *
     * @property threshold Umbral mínimo de batería (exclusivo)
     */
    data class BatteryAbove(val threshold: Int) : RuleCondition() {
        init {
            require(threshold in 0..100) { "El umbral debe estar entre 0 y 100, recibido: $threshold" }
        }
    }

    /** Evalúa si el dispositivo está cargando. */
    object IsCharging : RuleCondition()

    /**
     * Evalúa si hay un evento próximo dentro de los minutos especificados.
     *
     * @property withinMinutes Ventana de tiempo en minutos para buscar eventos
     */
    data class HasUpcomingEvent(val withinMinutes: Int) : RuleCondition() {
        init {
            require(withinMinutes > 0) { "Los minutos deben ser positivos, recibido: $withinMinutes" }
        }
    }

    /**
     * Evalúa si el texto de la pantalla contiene el texto buscado.
     * Búsqueda case-insensitive.
     *
     * @property text Texto a buscar en la pantalla
     */
    data class ScreenContains(val text: String) : RuleCondition()

    /**
     * Evalúa si la app en primer plano coincide con el paquete dado.
     *
     * @property packageName Nombre del paquete de la app
     */
    data class AppInForeground(val packageName: String) : RuleCondition()

    /**
     * Evalúa si la ubicación del usuario coincide con el tipo dado.
     *
     * @property location Tipo de ubicación esperada
     */
    data class AtLocation(val location: LocationType) : RuleCondition()

    /** Evalúa si es fin de semana (sábado o domingo). */
    object IsWeekend : RuleCondition()

    /** Evalúa si el dispositivo tiene alguna conexión de red. */
    object IsConnected : RuleCondition()

    /**
     * Combina dos condiciones con lógica AND. Ambas deben ser verdaderas.
     *
     * @property left Primera condición
     * @property right Segunda condición
     */
    data class And(val left: RuleCondition, val right: RuleCondition) : RuleCondition()

    /**
     * Combina dos condiciones con lógica OR. Al menos una debe ser verdadera.
     *
     * @property left Primera condición
     * @property right Segunda condición
     */
    data class Or(val left: RuleCondition, val right: RuleCondition) : RuleCondition()

    /**
     * Niega una condición.
     *
     * @property condition Condición a negar
     */
    data class Not(val condition: RuleCondition) : RuleCondition()
}

/**
 * Regla proactiva que define cuándo y cómo el asistente debe actuar
 * de forma proactiva sin que el usuario lo solicite explícitamente.
 *
 * Las reglas se evalúan contra un [AggregatedContext] y, si todas las
 * condiciones se cumplen, se ejecuta la [ProactiveAction] asociada.
 *
 * @property id Identificador único de la regla
 * @property name Nombre descriptivo de la regla
 * @property conditions Lista de condiciones que deben cumplirse (todas, lógica AND)
 * @property action Acción a ejecutar cuando se cumplan las condiciones
 * @property priority Prioridad de la regla para ordenar sugerencias
 * @property enabled true si la regla está activa
 * @property cooldownMinutes Tiempo mínimo en minutos entre activaciones consecutivas
 */
data class ProactiveRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val conditions: List<RuleCondition>,
    val action: ProactiveAction,
    val priority: ProactivePriority = ProactivePriority.MEDIUM,
    val enabled: Boolean = true,
    val cooldownMinutes: Int = 30
) {
    init {
        require(name.isNotBlank()) { "El nombre de la regla no puede estar vacío" }
        require(conditions.isNotEmpty()) { "La regla debe tener al menos una condición" }
        require(cooldownMinutes >= 0) { "El cooldown no puede ser negativo, recibido: $cooldownMinutes" }
    }

    companion object {
        /**
         * Crea una regla simple con una única condición.
         *
         * @param name Nombre descriptivo
         * @param condition Condición de activación
         * @param action Acción a ejecutar
         * @param priority Prioridad (por defecto MEDIUM)
         * @return ProactiveRule con una sola condición
         */
        fun simple(
            name: String,
            condition: RuleCondition,
            action: ProactiveAction,
            priority: ProactivePriority = ProactivePriority.MEDIUM
        ): ProactiveRule = ProactiveRule(
            name = name,
            conditions = listOf(condition),
            action = action,
            priority = priority
        )
    }
}
