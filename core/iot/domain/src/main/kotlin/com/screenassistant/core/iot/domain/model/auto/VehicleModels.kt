package com.screenassistant.core.iot.domain.model.auto

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Comando de voz o acción para enviar al vehículo.
 */
@Serializable
data class VehicleCommand(
    val commandId: String = java.util.UUID.randomUUID().toString(),
    val action: String, // "CLIMATE_SET_TEMP", "LOCK_DOORS", "START_ENGINE", etc.
    val params: Map<String, String> = emptyMap(),
    val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
)

/**
 * Resultado de ejecutar un comando en el vehículo.
 */
@Serializable
data class VehicleCommandResult(
    val commandId: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val responseData: Map<String, String> = emptyMap(),
    val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
)

/**
 * Evento del vehículo (estado, cambios, notificaciones).
 */
@Serializable
data class VehicleEvent(
    val eventId: String = java.util.UUID.randomUUID().toString(),
    val type: String, // "CLIMATE_CHANGED", "DOOR_OPENED", "FUEL_LOW", etc.
    val payload: Map<String, String> = emptyMap(),
    val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
)

/**
 * Destino de navegación.
 */
@Serializable
data class NavigationDestination(
    val destinationId: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
)

/**
 * Resultado de iniciar navegación.
 */
@Serializable
data class NavigationResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val routeId: String? = null,
    val estimatedDurationMinutes: Long? = null,
    val estimatedDistanceKm: Double? = null,
)