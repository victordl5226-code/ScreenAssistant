package com.screenassistant.core.iot.domain.model.smartHome

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Representa una entidad de Home Assistant.
 *
 * Home Assistant expone todos los dispositivos y sensores como "entidades"
 * con un entity_id único (ej: light.salon, sensor.temperatura_exterior).
 * Este modelo mapea la respuesta de la API REST de Home Assistant.
 *
 * @property entityId Identificador único en Home Assistant (dominio.objeto)
 * @property state Estado actual como string (ej: "on", "off", "23.5", "unavailable")
 * @property attributes Atributos adicionales específicos del dominio
 * @property lastChanged Último cambio de estado
 * @property lastUpdated Última actualización (incluye actualizaciones de atributos)
 * @property context Contexto de la actualización (user_id, parent_id, etc.)
 */
@Serializable
data class HomeAssistantEntity(
    val entityId: String,
    val state: String,
    val attributes: Map<String, String>,
    val lastChanged: Instant,
    val lastUpdated: Instant,
    val context: HAContext,
) {
    /** Dominio de la entidad (light, switch, sensor, climate, lock, cover, fan, etc.) */
    val domain: String
        get() = entityId.substringBefore('.').lowercase()

    /** Nombre del objeto (parte después del punto) */
    val objectId: String
        get() = entityId.substringAfterLast('.')

    /** Nombre amigable desde atributos o entityId como fallback */
    val friendlyName: String
        get() = attributes["friendly_name"] ?: entityId

    /** Icono desde atributos */
    val icon: String?
        get() = attributes["icon"]

    /** Unidad de medida para sensores */
    val unitOfMeasurement: String?
        get() = attributes["unit_of_measurement"]

    /** Clase de dispositivo (device_class) */
    val deviceClass: String?
        get() = attributes["device_class"]

    /** Verifica si la entidad está disponible */
    val isAvailable: Boolean
        get() = state != "unavailable" && state != "unknown"

    /** Verifica si la entidad está "encendida" (para dominios binarios) */
    val isOn: Boolean
        get() = state == "on" || state == "true" || state == "1"

    /** Convierte el estado a Double para sensores numéricos */
    fun stateAsDouble(): Double? = state.toDoubleOrNull()

    /** Convierte el estado a Int para sensores enteros */
    fun stateAsInt(): Int? = state.toIntOrNull()

    /** Convierte el estado a Boolean */
    fun stateAsBoolean(): Boolean? = when (state.lowercase()) {
        "on", "true", "1", "yes", "open", "home", "detected" -> true
        "off", "false", "0", "no", "closed", "away", "not_detected", "clear" -> false
        else -> null
    }

    /** Obtiene un atributo tipado de forma segura */
    fun <T> getAttribute(key: String): T? = attributes[key] as? T

    /** Obtiene un atributo como String */
    fun getStringAttribute(key: String): String? = attributes[key]

    /** Obtiene un atributo como Int */
    fun getIntAttribute(key: String): Int? = attributes[key]?.toIntOrNull()

    /** Obtiene un atributo como Double */
    fun getDoubleAttribute(key: String): Double? = attributes[key]?.toDoubleOrNull()

    /** Obtiene un atributo como Boolean */
    fun getBooleanAttribute(key: String): Boolean? = attributes[key]?.toBoolean()
}

/**
 * Contexto de actualización de Home Assistant.
 */
@Serializable
data class HAContext(
    val id: String,
    val parentId: String? = null,
    val userId: String? = null,
)

/**
 * Dominios conocidos de Home Assistant.
 * Cada dominio tiene un conjunto específico de atributos y servicios.
 */
enum class HADomain(
    val services: List<String>,
) {
    LIGHT(listOf("turn_on", "turn_off", "toggle", "set_brightness", "set_color_temp", "set_rgb_color")),
    SWITCH(listOf("turn_on", "turn_off", "toggle")),
    SENSOR(emptyList()),
    BINARY_SENSOR(emptyList()),
    CLIMATE(listOf("set_temperature", "set_hvac_mode", "set_fan_mode", "set_preset_mode", "turn_on", "turn_off")),
    LOCK(listOf("lock", "unlock", "open")),
    COVER(listOf("open_cover", "close_cover", "stop_cover", "set_cover_position", "set_cover_tilt_position")),
    FAN(listOf("turn_on", "turn_off", "set_percentage", "set_preset_mode", "oscillate")),
    MEDIA_PLAYER(listOf("play_media", "media_play", "media_pause", "media_stop", "volume_up", "volume_down", "volume_mute", "select_source")),
    VACUUM(listOf("start", "stop", "return_to_base", "clean_spot", "set_fan_speed", "send_command")),
    CAMERA(emptyList()),
    PERSON(emptyList()),
    DEVICE_TRACKER(emptyList()),
    ALARM_CONTROL_PANEL(listOf("alarm_arm_away", "alarm_arm_home", "alarm_arm_night", "alarm_disarm")),
    INPUT_BOOLEAN(listOf("turn_on", "turn_off", "toggle")),
    INPUT_NUMBER(listOf("set_value", "increment", "decrement")),
    INPUT_SELECT(listOf("select_option", "select_first", "select_last", "select_next", "select_previous")),
    INPUT_TEXT(listOf("set_value")),
    SCENE(listOf("turn_on")),
    SCRIPT(listOf("turn_on", "turn_off", "toggle")),
    AUTOMATION(listOf("turn_on", "turn_off", "toggle", "trigger")),
    GROUP(listOf("turn_on", "turn_off", "toggle")),
    ZONE(emptyList()),
    SUN(emptyList()),
    WEATHER(emptyList()),
    REMOTE(listOf("send_command")),
    BUTTON(listOf("press")),
    NUMBER(listOf("set_value")),
    SELECT(listOf("select_option")),
    TEXT(listOf("set_value")),
    UPDATE(emptyList()),
    VALVE(listOf("open_valve", "close_valve", "set_valve_position")),
    WATER_HEATER(listOf("set_temperature", "set_operation_mode")),
    HUMIDIFIER(listOf("set_humidity", "set_mode", "turn_on", "turn_off")),
    LAWN_MOWER(listOf("start_mowing", "pause", "dock")),
    TODO(emptyList()),
    EVENT(emptyList()),
    CONVERSATION(emptyList()),
    STT(emptyList()),
    TTS(emptyList()),
    WAKE_WORD(emptyList()),
    NOTIFY(emptyList()),
    IMAGE(emptyList()),
    NOTIFICATION(emptyList()),
}

/**
 * Servicio de Home Assistant para invocar acciones.
 *
 * @property domain Dominio del servicio
 * @property service Nombre del servicio
 * @property target Entidades objetivo (entity_id, device_id, area_id)
 * @property data Parámetros del servicio
 */
@Serializable
data class HAServiceCall(
    val domain: String,
    val service: String,
    val target: HATarget? = null,
    val data: Map<String, String> = emptyMap(),
) {
    companion object {
        /** Crea una llamada a servicio para encender una luz */
        fun lightTurnOn(entityId: String, brightness: Int? = null, colorTemp: Int? = null, rgbColor: List<Int>? = null): HAServiceCall {
            val data = mutableMapOf<String, String>()
            brightness?.let { data["brightness"] = it.toString() }
            colorTemp?.let { data["color_temp_kelvin"] = it.toString() }
            rgbColor?.let { data["rgb_color"] = it.joinToString(",") }
            return HAServiceCall("light", "turn_on", HATarget(entityIds = listOf(entityId)), data)
        }

        /** Crea una llamada a servicio para apagar una luz */
        fun lightTurnOff(entityId: String): HAServiceCall =
            HAServiceCall("light", "turn_off", HATarget(entityIds = listOf(entityId)))

        /** Crea una llamada a servicio para toggle de switch */
        fun switchToggle(entityId: String): HAServiceCall =
            HAServiceCall("switch", "toggle", HATarget(entityIds = listOf(entityId)))

        /** Crea una llamada a servicio para clima */
        fun climateSetTemperature(entityId: String, temperature: Double, hvacMode: String? = null): HAServiceCall {
            val data = mutableMapOf<String, String>("temperature" to temperature.toString())
            hvacMode?.let { data["hvac_mode"] = it }
            return HAServiceCall("climate", "set_temperature", HATarget(entityIds = listOf(entityId)), data)
        }

        /** Crea una llamada a servicio para cerradura */
        fun lock(entityId: String): HAServiceCall =
            HAServiceCall("lock", "lock", HATarget(entityIds = listOf(entityId)))

        fun unlock(entityId: String): HAServiceCall =
            HAServiceCall("lock", "unlock", HATarget(entityIds = listOf(entityId)))

        /** Crea una llamada a servicio para persianas */
        fun coverSetPosition(entityId: String, position: Int): HAServiceCall =
            HAServiceCall("cover", "set_cover_position", HATarget(entityIds = listOf(entityId)), mapOf("position" to position.coerceIn(0, 100).toString()))
    }
}

/**
 * Objetivo de una llamada a servicio.
 */
@Serializable
data class HATarget(
    val entityIds: List<String> = emptyList(),
    val deviceIds: List<String> = emptyList(),
    val areaIds: List<String> = emptyList(),
)