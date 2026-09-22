package com.screenassistant.core.data.repository

import com.screenassistant.core.data.local.ProactiveRuleDao
import com.screenassistant.core.data.local.ProactiveRuleEntity
import com.screenassistant.core.domain.model.proactive.ProactiveAction
import com.screenassistant.core.domain.model.proactive.ProactivePriority
import com.screenassistant.core.domain.model.proactive.ProactiveRule
import com.screenassistant.core.domain.model.proactive.RuleCondition
import com.screenassistant.core.domain.repository.ProactiveRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [ProactiveRuleRepository] que persiste las reglas proactivas
 * en Room vía [ProactiveRuleDao].
 *
 * Las reglas contienen [RuleCondition] (sealed class) y [ProactiveAction]
 * (sealed class) que se serializan a JSON para almacenamiento en la entity.
 * La serialización se realiza en esta clase usando kotlinx.serialization con
 * modelos internos serializables que replican la estructura de los tipos de dominio.
 *
 * @property dao DAO de acceso a la tabla `proactive_rules`
 * @property json Instancia de [Json] configurada para el proyecto
 */
@Singleton
class ProactiveRuleRepositoryImpl @Inject constructor(
    private val dao: ProactiveRuleDao,
    private val json: Json
) : ProactiveRuleRepository {

    override fun getAllRules(): Flow<List<ProactiveRule>> {
        return dao.getAllRules().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getEnabledRules(): List<ProactiveRule> {
        return dao.getEnabledRules().map { it.toDomain() }
    }

    override suspend fun getRule(id: String): ProactiveRule? {
        return dao.getRule(id)?.toDomain()
    }

    override suspend fun saveRule(rule: ProactiveRule) {
        dao.insertRule(fromDomain(rule))
    }

    override suspend fun deleteRule(id: String) {
        dao.deleteRule(id)
    }

    override suspend fun toggleRule(id: String, enabled: Boolean) {
        val existing = dao.getRule(id)
            ?: throw IllegalArgumentException("No existe regla con id: $id")
        dao.toggleRule(id, enabled)
    }

    // ── Entity ↔ Domain mapping ───────────────────────────────────────────

    /**
     * Convierte una [ProactiveRuleEntity] a [ProactiveRule] de dominio.
     */
    private fun ProactiveRuleEntity.toDomain(): ProactiveRule {
        val conditions = deserializeConditions(conditionsJson)
        val action = deserializeAction(actionType, actionData)
        return ProactiveRule(
            id = id,
            name = name,
            conditions = conditions,
            action = action,
            priority = priorityDomain(),
            enabled = enabled,
            cooldownMinutes = cooldownMinutes
        )
    }

    /**
     * Convierte un [ProactiveRule] de dominio a [ProactiveRuleEntity] para persistencia.
     */
    private fun fromDomain(rule: ProactiveRule): ProactiveRuleEntity {
        return ProactiveRuleEntity(
            id = rule.id,
            name = rule.name,
            conditionsJson = serializeConditions(rule.conditions),
            actionType = getActionType(rule.action),
            actionData = serializeAction(rule.action),
            priority = rule.priority.name,
            cooldownMinutes = rule.cooldownMinutes,
            enabled = rule.enabled
        )
    }

    // ── RuleCondition serialization ───────────────────────────────────────

    /**
     * Serializa una lista de [RuleCondition] a JSON.
     */
    private fun serializeConditions(conditions: List<RuleCondition>): String {
        val jsonArray = buildJsonArray {
            for (condition in conditions) {
                add(serializeCondition(condition))
            }
        }
        return jsonArray.toString()
    }

    /**
     * Serializa un [RuleCondition] individual a JSON.
     */
    private fun serializeCondition(condition: RuleCondition): JsonObject {
        return buildJsonObject {
            when (condition) {
                is RuleCondition.TimeRange -> {
                    put("type", JsonPrimitive("TimeRange"))
                    put("start", JsonPrimitive(condition.start.toSecondOfDay()))
                    put("end", JsonPrimitive(condition.end.toSecondOfDay()))
                }
                is RuleCondition.DayOfWeek -> {
                    put("type", JsonPrimitive("DayOfWeek"))
                    put("days", JsonArray(condition.days.map { JsonPrimitive(it.name) }))
                }
                is RuleCondition.BatteryBelow -> {
                    put("type", JsonPrimitive("BatteryBelow"))
                    put("threshold", JsonPrimitive(condition.threshold))
                }
                is RuleCondition.BatteryAbove -> {
                    put("type", JsonPrimitive("BatteryAbove"))
                    put("threshold", JsonPrimitive(condition.threshold))
                }
                is RuleCondition.IsCharging -> {
                    put("type", JsonPrimitive("IsCharging"))
                }
                is RuleCondition.HasUpcomingEvent -> {
                    put("type", JsonPrimitive("HasUpcomingEvent"))
                    put("withinMinutes", JsonPrimitive(condition.withinMinutes))
                }
                is RuleCondition.ScreenContains -> {
                    put("type", JsonPrimitive("ScreenContains"))
                    put("text", JsonPrimitive(condition.text))
                }
                is RuleCondition.AppInForeground -> {
                    put("type", JsonPrimitive("AppInForeground"))
                    put("packageName", JsonPrimitive(condition.packageName))
                }
                is RuleCondition.AtLocation -> {
                    put("type", JsonPrimitive("AtLocation"))
                    put("location", JsonPrimitive(condition.location.name))
                }
                is RuleCondition.IsWeekend -> {
                    put("type", JsonPrimitive("IsWeekend"))
                }
                is RuleCondition.IsConnected -> {
                    put("type", JsonPrimitive("IsConnected"))
                }
                is RuleCondition.And -> {
                    put("type", JsonPrimitive("And"))
                    put("left", serializeCondition(condition.left))
                    put("right", serializeCondition(condition.right))
                }
                is RuleCondition.Or -> {
                    put("type", JsonPrimitive("Or"))
                    put("left", serializeCondition(condition.left))
                    put("right", serializeCondition(condition.right))
                }
                is RuleCondition.Not -> {
                    put("type", JsonPrimitive("Not"))
                    put("condition", serializeCondition(condition.condition))
                }
            }
        }
    }

    /**
     * Deserializa un JSON de condiciones a lista de [RuleCondition].
     */
    private fun deserializeConditions(jsonString: String): List<RuleCondition> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            val jsonArray = json.parseToJsonElement(jsonString).jsonArray
            jsonArray.map { deserializeCondition(it.jsonObject) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Deserializa un [JsonObject] a [RuleCondition].
     */
    private fun deserializeCondition(obj: JsonObject): RuleCondition {
        val type = obj["type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Condition sin tipo")

        return when (type) {
            "TimeRange" -> RuleCondition.TimeRange(
                start = LocalTime.ofSecondOfDay(obj["start"]!!.jsonPrimitive.content.toLong()),
                end = LocalTime.ofSecondOfDay(obj["end"]!!.jsonPrimitive.content.toLong())
            )
            "DayOfWeek" -> RuleCondition.DayOfWeek(
                days = obj["days"]!!.jsonArray.map {
                    DayOfWeek.valueOf(it.jsonPrimitive.content)
                }.toSet()
            )
            "BatteryBelow" -> RuleCondition.BatteryBelow(
                threshold = obj["threshold"]!!.jsonPrimitive.content.toInt()
            )
            "BatteryAbove" -> RuleCondition.BatteryAbove(
                threshold = obj["threshold"]!!.jsonPrimitive.content.toInt()
            )
            "IsCharging" -> RuleCondition.IsCharging
            "HasUpcomingEvent" -> RuleCondition.HasUpcomingEvent(
                withinMinutes = obj["withinMinutes"]!!.jsonPrimitive.content.toInt()
            )
            "ScreenContains" -> RuleCondition.ScreenContains(
                text = obj["text"]!!.jsonPrimitive.content
            )
            "AppInForeground" -> RuleCondition.AppInForeground(
                packageName = obj["packageName"]!!.jsonPrimitive.content
            )
            "AtLocation" -> RuleCondition.AtLocation(
                location = com.screenassistant.core.domain.model.proactive.LocationType.valueOf(
                    obj["location"]!!.jsonPrimitive.content
                )
            )
            "IsWeekend" -> RuleCondition.IsWeekend
            "IsConnected" -> RuleCondition.IsConnected
            "And" -> RuleCondition.And(
                left = deserializeCondition(obj["left"]!!.jsonObject),
                right = deserializeCondition(obj["right"]!!.jsonObject)
            )
            "Or" -> RuleCondition.Or(
                left = deserializeCondition(obj["left"]!!.jsonObject),
                right = deserializeCondition(obj["right"]!!.jsonObject)
            )
            "Not" -> RuleCondition.Not(
                condition = deserializeCondition(obj["condition"]!!.jsonObject)
            )
            else -> throw IllegalArgumentException("Tipo de condition desconocido: $type")
        }
    }

    // ── ProactiveAction serialization ─────────────────────────────────────

    /**
     * Obtiene el tipo de acción como String para la entity.
     */
    private fun getActionType(action: ProactiveAction): String {
        return when (action) {
            is ProactiveAction.SuggestSystemAction -> "SuggestSystemAction"
            is ProactiveAction.ShowNotification -> "ShowNotification"
            is ProactiveAction.Speak -> "Speak"
            is ProactiveAction.SuggestAutomation -> "SuggestAutomation"
        }
    }

    /**
     * Serializa un [ProactiveAction] a JSON.
     */
    private fun serializeAction(action: ProactiveAction): String {
        val jsonObject = buildJsonObject {
            when (action) {
                is ProactiveAction.SuggestSystemAction -> {
                    put("actionId", JsonPrimitive(action.actionId))
                    put("params", JsonObject(
                        action.params.mapValues { JsonPrimitive(it.value) }
                    ))
                }
                is ProactiveAction.ShowNotification -> {
                    put("title", JsonPrimitive(action.title))
                    put("body", JsonPrimitive(action.body))
                }
                is ProactiveAction.Speak -> {
                    put("message", JsonPrimitive(action.message))
                }
                is ProactiveAction.SuggestAutomation -> {
                    put("name", JsonPrimitive(action.name))
                    put("description", JsonPrimitive(action.description))
                }
            }
        }
        return jsonObject.toString()
    }

    /**
     * Deserializa un JSON de acción a [ProactiveAction].
     */
    private fun deserializeAction(type: String, jsonString: String): ProactiveAction {
        if (jsonString.isBlank()) {
            return ProactiveAction.Speak(message = "")
        }
        return try {
            val obj = json.parseToJsonElement(jsonString).jsonObject
            when (type) {
                "SuggestSystemAction" -> ProactiveAction.SuggestSystemAction(
                    actionId = obj["actionId"]!!.jsonPrimitive.content,
                    params = obj["params"]?.jsonObject?.mapValues {
                        it.value.jsonPrimitive.content
                    } ?: emptyMap()
                )
                "ShowNotification" -> ProactiveAction.ShowNotification(
                    title = obj["title"]!!.jsonPrimitive.content,
                    body = obj["body"]!!.jsonPrimitive.content
                )
                "Speak" -> ProactiveAction.Speak(
                    message = obj["message"]!!.jsonPrimitive.content
                )
                "SuggestAutomation" -> ProactiveAction.SuggestAutomation(
                    name = obj["name"]!!.jsonPrimitive.content,
                    description = obj["description"]!!.jsonPrimitive.content
                )
                else -> ProactiveAction.Speak(message = "")
            }
        } catch (_: Exception) {
            ProactiveAction.Speak(message = "")
        }
    }
}
