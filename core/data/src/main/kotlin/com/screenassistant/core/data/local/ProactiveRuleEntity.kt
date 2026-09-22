package com.screenassistant.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.screenassistant.core.domain.model.proactive.ProactivePriority

/**
 * Entity Room para persistir reglas proactivas del asistente.
 *
 * Las reglas definen cuándo y cómo el asistente debe actuar de forma proactiva
 * sin que el usuario lo solicite. Cada regla tiene condiciones de activación,
 * una acción a ejecutar y un nivel de prioridad.
 *
 * Serialización:
 * - [conditionsJson] almacena la lista de [RuleCondition] como JSON.
 *   La serialización/deserialización se realiza en el repositorio
 *   ([com.screenassistant.core.data.repository.ProactiveRuleRepositoryImpl])
 *   porque [RuleCondition] es una sealed class sin @Serializable.
 * - [actionData] almacena la representación JSON de [ProactiveAction].
 *   El mapeo tipo-datos también se realiza en el repositorio.
 * - [priority] se almacena como el nombre del enum [ProactivePriority].
 *
 * @property id Identificador único de la regla (UUID)
 * @property name Nombre descriptivo de la regla
 * @property conditionsJson JSON con la lista de condiciones de activación
 * @property actionType Tipo de la acción (e.g., "Speak", "ShowNotification")
 * @property actionData JSON con los datos de la acción
 * @property priority Nombre del enum [ProactivePriority] (e.g., "MEDIUM")
 * @property cooldownMinutes Tiempo mínimo en minutos entre activaciones consecutivas
 * @property enabled true si la regla está activa
 */
@Entity(tableName = "proactive_rules")
data class ProactiveRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val conditionsJson: String,
    val actionType: String,
    val actionData: String,
    val priority: String,
    val cooldownMinutes: Int = 30,
    val enabled: Boolean = true
) {
    /**
     * Convierte el [priority] de String a [ProactivePriority].
     * Si el nombre no coincide con un valor válido, retorna [ProactivePriority.MEDIUM].
     */
    fun priorityDomain(): ProactivePriority =
        runCatching { ProactivePriority.valueOf(priority) }.getOrDefault(ProactivePriority.MEDIUM)
}
