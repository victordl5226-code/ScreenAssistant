package com.screenassistant.core.iot.domain.model.smartHome

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Representa una regla de automatización para el hogar inteligente.
 *
 * Una automatización consta de:
 * - **Triggers (disparadores)**: Eventos que inician la automatización
 * - **Conditions (condiciones)**: Filtros opcionales que deben cumplirse
 * - **Actions (acciones)**: Qué hacer cuando se disparan los triggers y se cumplen las condiciones
 *
 * Este modelo es agnóstico al motor de ejecución (Home Assistant, Matter, local, etc.)
 * y permite serialización para persistencia y sincronización.
 *
 * @property ruleId Identificador único de la regla
 * @property name Nombre descriptivo
 * @property description Descripción detallada opcional
 * @property enabled Si la regla está activa
 * @property triggers Lista de disparadores (OR lógico: cualquiera dispara)
 * @property conditions Lista de condiciones (AND lógico: todas deben cumplirse)
 * @property actions Lista de acciones a ejecutar en orden
 * @property mode Modo de ejecución (single, restart, queued, parallel)
 * @property maxExecutions Máximo de ejecuciones concurrentes (para mode queued/parallel)
 * @property createdAt Timestamp de creación
 * @property updatedAt Timestamp de última modificación
 * @property lastTriggered Última vez que se ejecutó (null si nunca)
 * @property executionCount Contador total de ejecuciones
 * @property tags Etiquetas para organización/búsqueda
 * @property metadata Metadatos adicionales
 */
@Serializable
data class AutomationRule(
    val ruleId: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val triggers: List<AutomationTrigger>,
    val conditions: List<AutomationCondition> = emptyList(),
    val actions: List<AutomationAction>,
    val mode: AutomationMode = AutomationMode.SINGLE,
    val maxExecutions: Int = 1,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val lastTriggered: Instant? = null,
    val executionCount: Long = 0,
    val tags: Set<String> = emptySet(),
    @Contextual val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * Verifica si la regla puede ejecutarse (habilitada y tiene triggers/acciones).
     */
    val isValid: Boolean
        get() = enabled && triggers.isNotEmpty() && actions.isNotEmpty()

    /**
     * Crea una copia con ejecución registrada.
     */
    fun withExecution(): AutomationRule = copy(
        lastTriggered = Clock.System.now(),
        executionCount = executionCount + 1,
        updatedAt = Clock.System.now(),
    )

    /**
     * Crea una copia habilitada/deshabilitada.
     */
    fun withEnabled(enabled: Boolean): AutomationRule = copy(
        enabled = enabled,
        updatedAt = Clock.System.now(),
    )
}

/**
 * Modos de ejecución de una automatización.
 */
enum class AutomationMode {
    /** Ejecución única: ignora nuevos triggers mientras se ejecuta */
    SINGLE,
    /** Reinicia: cancela ejecución actual y empieza nueva */
    RESTART,
    /** Encola: espera a que termine la actual */
    QUEUED,
    /** Paralelo: ejecuta múltiples instancias simultáneamente (hasta maxExecutions) */
    PARALLEL,
}

/**
 * Disparador (trigger) de una automatización.
 *
 * Tipos soportados:
 * - State change: cambio de estado de entidad
 * - Time: hora específica, amanecer/atardecer, intervalo
 * - Event: evento del bus (button press, webhook, etc.)
 * - Numeric state: valor numérico cruza umbral
 * - Template: evaluación de plantilla Jinja2
 */
@Serializable
sealed class AutomationTrigger {
    /** Trigger por cambio de estado de entidad */
    @Serializable
    data class StateChange(
        val entityId: String,
        val from: String? = null,
        val to: String? = null,
        val forDuration: Duration? = null,
        val notFrom: String? = null,
        val notTo: String? = null,
    ) : AutomationTrigger()

    /** Trigger por hora específica */
    @Serializable
    data class Time(
        val at: String, // HH:MM:SS o "sunrise"/"sunset"
        val offset: Duration? = null,
    ) : AutomationTrigger()

    /** Trigger por intervalo de tiempo */
    @Serializable
    data class TimeInterval(
        val interval: Duration,
    ) : AutomationTrigger()

    /** Trigger por evento del bus */
    @Serializable
    data class Event(
        val eventType: String,
        val eventData: Map<String, String>? = null,
    ) : AutomationTrigger()

    /** Trigger por estado numérico cruzando umbral */
    @Serializable
    data class NumericState(
        val entityId: String,
        val above: Double? = null,
        val below: Double? = null,
        val forDuration: Duration? = null,
    ) : AutomationTrigger()

    /** Trigger por evaluación de plantilla */
    @Serializable
    data class Template(
        val valueTemplate: String,
        val forDuration: Duration? = null,
    ) : AutomationTrigger()

    /** Trigger por zona (entrada/salida) */
    @Serializable
    data class Zone(
        val entityId: String,
        val zone: String,
        val event: ZoneEvent,
    ) : AutomationTrigger()

    /** Trigger por dispositivo (aparece/desaparece) */
    @Serializable
    data class Device(
        val deviceId: String,
        val event: DeviceEvent,
    ) : AutomationTrigger()
}

/**
 * Eventos de zona.
 */
enum class ZoneEvent {
    ENTER,
    LEAVE,
}

/**
 * Eventos de dispositivo.
 */
enum class DeviceEvent {
    APPEARED,
    DISAPPEARED,
}

/**
 * Duración en segundos (para serialización simple).
 */
@Serializable
data class Duration(
    val seconds: Long,
) {
    companion object {
        fun fromSeconds(seconds: Long): Duration = Duration(seconds)
        fun fromMinutes(minutes: Long): Duration = Duration(minutes * 60)
        fun fromHours(hours: Long): Duration = Duration(hours * 3600)
        fun fromDays(days: Long): Duration = Duration(days * 86400)
    }

    val inMinutes: Long
        get() = seconds / 60

    val inHours: Long
        get() = seconds / 3600
}

/**
 * Condición que debe cumplirse para ejecutar las acciones.
 *
 * Todas las condiciones se evalúan con AND lógico.
 */
@Serializable
sealed class AutomationCondition {
    /** Condición por estado de entidad */
    @Serializable
    data class State(
        val entityId: String,
        val state: String,
    ) : AutomationCondition()

    /** Condición por estado numérico */
    @Serializable
    data class NumericState(
        val entityId: String,
        val above: Double? = null,
        val below: Double? = null,
    ) : AutomationCondition()

    /** Condición por hora (entre dos horas) */
    @Serializable
    data class Time(
        val after: String, // HH:MM:SS
        val before: String, // HH:MM:SS
        val weekday: List<Int>? = null, // 1=Mon ... 7=Sun
    ) : AutomationCondition()

    /** Condición por zona */
    @Serializable
    data class Zone(
        val entityId: String,
        val zone: String,
    ) : AutomationCondition()

    /** Condición por evaluación de plantilla */
    @Serializable
    data class Template(
        val valueTemplate: String,
    ) : AutomationCondition()

    /** Condición por estado del sol (arriba/abajo) */
    @Serializable
    data class Sun(
        val beforeSunset: Boolean? = null,
        val afterSunrise: Boolean? = null,
    ) : AutomationCondition()

    /** Condición AND explícita (agrupa sub-condiciones) */
    @Serializable
    data class And(
        val conditions: List<AutomationCondition>,
    ) : AutomationCondition()

    /** Condición OR explícita (agrupa sub-condiciones) */
    @Serializable
    data class Or(
        val conditions: List<AutomationCondition>,
    ) : AutomationCondition()

    /** Condición NOT (niega sub-condición) */
    @Serializable
    data class Not(
        val condition: AutomationCondition,
    ) : AutomationCondition()
}

/**
 * Acción a ejecutar cuando se dispara la automatización.
 */
@Serializable
sealed class AutomationAction {
    /** Llamada a servicio de Home Assistant */
    @Serializable
    data class ServiceCall(
        val domain: String,
        val service: String,
        val target: HATarget? = null,
        val data: Map<String, String> = emptyMap(),
    ) : AutomationAction()

    /** Activar una escena */
    @Serializable
    data class ActivateScene(
        val sceneId: String,
    ) : AutomationAction()

    /** Esperar un tiempo */
    @Serializable
    data class Delay(
        val duration: Duration,
    ) : AutomationAction()

    /** Esperar a que se cumpla una condición (con timeout) */
    @Serializable
    data class WaitForTrigger(
        val triggers: List<AutomationTrigger>,
        val timeout: Duration? = null,
        val continueOnTimeout: Boolean = false,
    ) : AutomationAction()

    /** Ejecutar otra automatización */
    @Serializable
    data class TriggerAutomation(
        val ruleId: String,
    ) : AutomationAction()

    /** Elegir entre múltiples opciones (primer match) */
    @Serializable
    data class Choose(
        val options: List<ChooseOption>,
        val default: List<AutomationAction>? = null,
    ) : AutomationAction()

    /** Repetir acciones mientras se cumpla condición */
    @Serializable
    data class Repeat(
        val whileCondition: List<AutomationCondition>,
        val actions: List<AutomationAction>,
        val until: List<AutomationTrigger>? = null,
        val count: Int? = null,
    ) : AutomationAction()

    /** Paralelo: ejecutar acciones simultáneamente */
    @Serializable
    data class Parallel(
        val actions: List<AutomationAction>,
    ) : AutomationAction()

    /** Notificación */
    @Serializable
    data class Notify(
        val message: String,
        val title: String? = null,
        val target: List<String>? = null,
        val data: Map<String, String> = emptyMap(),
    ) : AutomationAction()

    /** Log personalizado */
    @Serializable
    data class Log(
        val message: String,
        val level: LogLevel = LogLevel.INFO,
    ) : AutomationAction()

    /** Establecer variable (para uso en plantillas posteriores) */
    @Serializable
    data class SetVariable(
        val name: String,
        val value: String,
    ) : AutomationAction()
}

/**
 * Opción en una acción Choose.
 */
@Serializable
data class ChooseOption(
    val conditions: List<AutomationCondition>,
    val actions: List<AutomationAction>,
)

/**
 * Niveles de log para acción Log.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

