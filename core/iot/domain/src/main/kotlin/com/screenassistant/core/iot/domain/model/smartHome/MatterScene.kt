package com.screenassistant.core.iot.domain.model.smartHome

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Representa una escena Matter (escena predefinida de múltiples dispositivos).
 *
 * Una escena agrupa un conjunto de estados objetivo para múltiples dispositivos,
 * permitiendo activarlos simultáneamente con un solo comando.
 * Ejemplos: "Noche de cine", "Buenos días", "Salir de casa", "Relax".
 *
 * @property sceneId Identificador único de la escena
 * @property name Nombre amigable de la escena
 * @property icon Icono representativo (Material Design icon name o similar)
 * @property devices Estados objetivo para cada dispositivo participante
 * @property isFavorite Si la escena está marcada como favorita
 * @property createdAt Timestamp de creación
 * @property updatedAt Timestamp de última modificación
 * @property metadata Metadatos adicionales (tags, descripción, etc.)
 */
@Serializable
data class MatterScene(
    val sceneId: String,
    val name: String,
    val icon: String,
    val devices: List<SceneDeviceState>,
    val isFavorite: Boolean = false,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * Obtiene el estado objetivo para un dispositivo específico.
     *
     * @param deviceId ID del dispositivo
     * @return Estado objetivo o null si el dispositivo no participa en la escena
     */
    fun getDeviceState(deviceId: String): SceneDeviceState? =
        devices.find { it.deviceId == deviceId }

    /**
     * Verifica si un dispositivo participa en esta escena.
     */
    fun containsDevice(deviceId: String): Boolean =
        devices.any { it.deviceId == deviceId }

    /**
     * Número de dispositivos en la escena.
     */
    val deviceCount: Int
        get() = devices.size
}

/**
 * Estado objetivo de un dispositivo dentro de una escena.
 *
 * @property deviceId ID del dispositivo
 * @property targetAttributes Atributos objetivo a establecer
 * @property transitionTimeMs Tiempo de transición en milisegundos (opcional)
 */
@Serializable
data class SceneDeviceState(
    val deviceId: String,
    val targetAttributes: Map<String, String>,
    val transitionTimeMs: Long? = null,
) {
    /**
     * Crea un estado para encender/apagar un dispositivo.
     */
    companion object {
        fun onOff(deviceId: String, on: Boolean): SceneDeviceState =
            SceneDeviceState(deviceId, mapOf("onOff" to on.toString()))

        fun level(deviceId: String, level: Int): SceneDeviceState =
            SceneDeviceState(deviceId, mapOf("currentLevel" to level.coerceIn(0, 254).toString()))

        fun colorTemperature(deviceId: String, kelvin: Int): SceneDeviceState =
            SceneDeviceState(deviceId, mapOf("colorTemperatureMireds" to kelvin.coerceIn(153, 500).toString()))

        fun thermostatMode(deviceId: String, mode: Int): SceneDeviceState =
            SceneDeviceState(deviceId, mapOf("systemMode" to mode.toString()))

        fun heatSetpoint(deviceId: String, celsius: Double): SceneDeviceState =
            SceneDeviceState(deviceId, mapOf("occupiedHeatingSetpoint" to celsius.toString()))

        fun coolSetpoint(deviceId: String, celsius: Double): SceneDeviceState =
            SceneDeviceState(deviceId, mapOf("occupiedCoolingSetpoint" to celsius.toString()))

        fun windowCoveringPosition(deviceId: String, percent: Int): SceneDeviceState =
            SceneDeviceState(deviceId, mapOf("currentPositionLiftPercent100th" to percent.coerceIn(0, 100).toString()))

        fun lockState(deviceId: String, locked: Boolean): SceneDeviceState =
            SceneDeviceState(deviceId, mapOf("lockState" to locked.toString()))
    }
}

/**
 * Resultado de activar una escena.
 *
 * @property sceneId ID de la escena activada
 * @property success Si la activación fue exitosa globalmente
 * @property deviceResults Resultado por dispositivo
 * @property activatedAt Timestamp de activación
 * @property error Mensaje de error si falló globalmente
 */
@Serializable
data class SceneActivationResult(
    val sceneId: String,
    val success: Boolean,
    val deviceResults: List<DeviceActivationResult>,
    val activatedAt: Instant = Clock.System.now(),
    val error: String? = null,
) {
    /** Dispositivos que fallaron en la activación */
    val failedDevices: List<DeviceActivationResult>
        get() = deviceResults.filter { !it.success }

    /** Dispositivos que tuvieron éxito */
    val successfulDevices: List<DeviceActivationResult>
        get() = deviceResults.filter { it.success }
}

/**
 * Resultado de activación para un dispositivo individual.
 */
@Serializable
data class DeviceActivationResult(
    val deviceId: String,
    val success: Boolean,
    val error: String? = null,
    val targetAttributes: Map<String, String> = emptyMap(),
    val actualAttributes: Map<String, String> = emptyMap(),
)