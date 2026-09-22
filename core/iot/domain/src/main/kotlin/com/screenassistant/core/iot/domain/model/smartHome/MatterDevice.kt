package com.screenassistant.core.iot.domain.model.smartHome

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Representa un dispositivo Matter en el ecosistema Smart Home.
 *
 * Matter es el estándar unificado de conectividad para el hogar inteligente.
 * Este modelo abstrae las propiedades comunes de cualquier dispositivo Matter
 * independientemente de su tipo (luz, termostato, cerradura, sensor, etc.).
 *
 * @property deviceId Identificador único del dispositivo en la red Matter
 * @property name Nombre amigable asignado por el usuario
 * @property type Tipo de dispositivo según la especificación Matter
 * @property room Habitación donde está ubicado el dispositivo
 * @property isOnline Estado de conectividad actual
 * @property attributes Mapa de atributos específicos del tipo de dispositivo
 * @property lastSeen Timestamp de la última comunicación exitosa
 * @property firmwareVersion Versión de firmware instalada
 * @property manufacturer Fabricante del dispositivo
 * @property model Modelo del dispositivo
 * @property serialNumber Número de serie
 */
@Serializable
data class MatterDevice(
    val deviceId: String,
    val name: String,
    val type: MatterDeviceType,
    val room: String,
    val isOnline: Boolean,
    @Contextual val attributes: Map<String, MatterAttribute>,
    val lastSeen: Instant,
    val firmwareVersion: String,
    val manufacturer: String,
    val model: String,
    val serialNumber: String,
) {
    /**
     * Obtiene un atributo específico del dispositivo de forma segura.
     *
     * @param key Clave del atributo
     * @return El valor del atributo o null si no existe
     */
    fun getAttribute(key: String): MatterAttribute? = attributes[key]

    /**
     * Verifica si el dispositivo soporta una capacidad específica.
     *
     * @param capability Capacidad a verificar
     * @return true si el dispositivo tiene la capacidad
     */
    fun supportsCapability(capability: MatterCapability): Boolean =
        attributes.containsKey(capability.attributeKey)
}

/**
 * Tipos de dispositivos Matter según la especificación oficial.
 *
 * Cada tipo define un conjunto de clusters y atributos obligatorios.
 */
enum class MatterDeviceType {
    /** Bombilla o luminaria regulable */
    ON_OFF_LIGHT,
    /** Bombilla con control de color y temperatura */
    COLOR_TEMPERATURE_LIGHT,
    /** Bombilla con control RGB completo */
    EXTENDED_COLOR_LIGHT,
    /** Termostato para control de climatización */
    THERMOSTAT,
    /** Cerradura inteligente */
    DOOR_LOCK,
    /** Sensor de temperatura */
    TEMPERATURE_SENSOR,
    /** Sensor de humedad */
    HUMIDITY_SENSOR,
    /** Sensor de ocupación/movimiento */
    OCCUPANCY_SENSOR,
    /** Sensor de contacto (puerta/ventana) */
    CONTACT_SENSOR,
    /** Enchufe inteligente */
    ON_OFF_PLUGIN_UNIT,
    /** Interruptor de pared */
    ON_OFF_SWITCH,
    /** Controlador de persianas/cortinas */
    WINDOW_COVERING,
    /** Ventilador */
    FAN,
    /** Purificador de aire */
    AIR_PURIFIER,
    /** Sensor de calidad del aire */
    AIR_QUALITY_SENSOR,
    /** Dispositivo genérico/no clasificado */
    GENERIC,
}

/**
 * Capacidades soportadas por dispositivos Matter.
 * Cada capacidad mapea a un cluster y atributo específico.
 */
enum class MatterCapability(
    val attributeKey: String,
    val clusterId: Int,
) {
    /** Encendido/apagado básico */
    ON_OFF("onOff", 0x0006),
    /** Nivel de brillo (0-254) */
    LEVEL_CONTROL("currentLevel", 0x0008),
    /** Control de temperatura de color (Kelvin) */
    COLOR_TEMPERATURE("colorTemperatureMireds", 0x0300),
    /** Control de color XY */
    COLOR_CONTROL_XY("currentX,currentY", 0x0300),
    /** Control de color HSV */
    COLOR_CONTROL_HSV("currentHue,currentSaturation", 0x0300),
    /** Modo de termostato */
    THERMOSTAT_MODE("systemMode", 0x0201),
    /** Punto de ajuste de calefacción */
    HEAT_SETPOINT("occupiedHeatingSetpoint", 0x0201),
    /** Punto de ajuste de refrigeración */
    COOL_SETPOINT("occupiedCoolingSetpoint", 0x0201),
    /** Temperatura ambiente */
    LOCAL_TEMPERATURE("localTemperature", 0x0201),
    /** Humedad relativa */
    RELATIVE_HUMIDITY("measuredValue", 0x0405),
    /** Estado de cerradura */
    LOCK_STATE("lockState", 0x0101),
    /** Detección de ocupación */
    OCCUPANCY("occupancy", 0x0406),
    /** Estado de contacto */
    CONTACT_STATE("contact", 0x0400),
    /** Posición de persiana (0-100%) */
    WINDOW_COVERING_POSITION("currentPositionLiftPercent100th", 0x0202),
    /** Velocidad de ventilador */
    FAN_SPEED("fanMode", 0x0203),
    /** Modo de purificador */
    AIR_PURIFIER_MODE("mode", 0x0204),
    /** Calidad del aire (PM2.5, CO2, VOC) */
    AIR_QUALITY("pm25,co2,voc", 0x0205),
}

/**
 * Valor de un atributo Matter con metadatos.
 *
 * @property value Valor del atributo (tipo dinámico según el cluster)
 * @property lastUpdated Última actualización del valor
 * @property unit Unidad de medida (opcional)
 * @property min Valor mínimo válido (opcional)
 * @property max Valor máximo válido (opcional)
 */
@Serializable
data class MatterAttribute(
    @Contextual val value: Any,
    val lastUpdated: Instant,
    val unit: String? = null,
    val min: Double? = null,
    val max: Double? = null,
) {
    /**
     * Convierte el valor a Double de forma segura.
     */
    fun toDouble(): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    /**
     * Convierte el valor a Boolean de forma segura.
     */
    fun toBoolean(): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value != 0
        is String -> value.toBoolean()
        else -> null
    }

    /**
     * Convierte el valor a Int de forma segura.
     */
    fun toInt(): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

/**
 * Comando para enviar a un dispositivo Matter.
 *
 * @property deviceId ID del dispositivo destino
 * @property capability Capacidad a controlar
 * @property value Valor a establecer
 * @property timestamp Momento de creación del comando
 */
@Serializable
data class MatterCommand(
    val deviceId: String,
    val capability: MatterCapability,
    @Contextual val value: Any,
    val timestamp: Instant = Clock.System.now(),
) {
    companion object {
        /** Crea un comando de encendido/apagado */
        fun onOff(deviceId: String, on: Boolean): MatterCommand =
            MatterCommand(deviceId, MatterCapability.ON_OFF, on)

        /** Crea un comando de nivel (brillo) */
        fun level(deviceId: String, level: Int): MatterCommand =
            MatterCommand(deviceId, MatterCapability.LEVEL_CONTROL, level.coerceIn(0, 254))

        /** Crea un comando de temperatura de color */
        fun colorTemperature(deviceId: String, kelvin: Int): MatterCommand =
            MatterCommand(deviceId, MatterCapability.COLOR_TEMPERATURE, kelvin.coerceIn(153, 500))

        /** Crea un comando de punto de ajuste de calefacción */
        fun heatSetpoint(deviceId: String, celsius: Double): MatterCommand =
            MatterCommand(deviceId, MatterCapability.HEAT_SETPOINT, celsius)

        /** Crea un comando de punto de ajuste de refrigeración */
        fun coolSetpoint(deviceId: String, celsius: Double): MatterCommand =
            MatterCommand(deviceId, MatterCapability.COOL_SETPOINT, celsius)

        /** Crea un comando de modo de termostato */
        fun thermostatMode(deviceId: String, mode: ThermostatMode): MatterCommand =
            MatterCommand(deviceId, MatterCapability.THERMOSTAT_MODE, mode.ordinal)

        /** Crea un comando de posición de persiana */
        fun windowCoveringPosition(deviceId: String, percent: Int): MatterCommand =
            MatterCommand(deviceId, MatterCapability.WINDOW_COVERING_POSITION, percent.coerceIn(0, 100))

        /** Crea un comando de estado de cerradura */
        fun lockState(deviceId: String, locked: Boolean): MatterCommand =
            MatterCommand(deviceId, MatterCapability.LOCK_STATE, locked)
    }
}

/**
 * Modos de operación del termostato Matter.
 */
enum class ThermostatMode {
    /** Apagado */
    OFF,
    /** Solo calefacción */
    HEAT,
    /** Solo refrigeración */
    COOL,
    /** Automático (calefacción y refrigeración) */
    AUTO,
    /** Solo ventilador */
    FAN_ONLY,
}