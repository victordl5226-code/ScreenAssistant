package com.screenassistant.core.iot.domain.repository

import kotlinx.serialization.Serializable

/**
 * Comando para enviar al vehículo.
 */
sealed class VehicleCommand {
    /** Climatización */
    @Serializable
    data class Climate(
        val action: ClimateAction,
        val value: String? = null,
    ) : VehicleCommand()

    /** Puertas/ventanas */
    @Serializable
    data class Doors(
        val action: DoorAction,
        val doorId: String? = null, // null = todas
    ) : VehicleCommand()

    /** Ventanas */
    @Serializable
    data class Windows(
        val action: WindowAction,
        val windowId: String? = null,
    ) : VehicleCommand()

    /** Carga EV */
    @Serializable
    data class Charging(
        val action: ChargingAction,
    ) : VehicleCommand()

    /** Bocina/luces (para localizar coche) */
    @Serializable
    data class LocateVehicle(
        val honk: Boolean = true,
        val flashLights: Boolean = true,
    ) : VehicleCommand()
}

/**
 * Acciones de climatización.
 */
enum class ClimateAction {
    SET_TEMPERATURE,
    SET_FAN_SPEED,
    TOGGLE_AC,
    TOGGLE_AUTO,
    TOGGLE_RECIRCULATION,
    TOGGLE_DEFROST,
    TOGGLE_SEAT_HEATING,
    TOGGLE_STEERING_HEATING,
    TOGGLE_SEAT_VENTILATION,
}

/**
 * Acciones de puertas.
 */
enum class DoorAction {
    LOCK,
    UNLOCK,
    OPEN_TRUNK,
    OPEN_CHARGE_PORT,
    OPEN_FRUNK,
}

/**
 * Acciones de ventanas.
 */
enum class WindowAction {
    OPEN,
    CLOSE,
    VENT,
}

/**
 * Acciones de carga.
 */
enum class ChargingAction {
    START,
    STOP,
    OPEN_PORT,
    CLOSE_PORT,
    SET_LIMIT,
    SET_SCHEDULE,
}

/**
 * Resultado de comando al vehículo.
 */
@Serializable
data class VehicleCommandResult(
    val success: Boolean,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val data: Map<String, String> = emptyMap(),
)

/**
 * Destino de navegación.
 */
@Serializable
data class NavigationDestination(
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val name: String?,
    val poiCategory: String?,
)

/**
 * Resultado de navegación.
 */
@Serializable
data class NavigationResult(
    val success: Boolean,
    val routeId: String?,
    val eta: Long?, // timestamp
    val distanceMeters: Double?,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

/**
 * Acciones de control multimedia.
 */
enum class MediaControlAction {
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS,
    SET_VOLUME,
    SET_SHUFFLE,
    SET_REPEAT,
    SELECT_SOURCE,
    SELECT_QUEUE_ITEM,
}

/**
 * Eventos del vehículo.
 */
sealed class VehicleEvent {
    @Serializable
    data class SpeedChanged(val speedKmh: Double) : VehicleEvent()

    @Serializable
    data class GearChanged(val gear: String) : VehicleEvent()

    @Serializable
    data class DoorStateChanged(val doorId: String, val state: String) : VehicleEvent()

    @Serializable
    data class WindowStateChanged(val windowId: String, val state: String) : VehicleEvent()

    @Serializable
    data class FuelLevelChanged(val levelPercent: Double) : VehicleEvent()

    @Serializable
    data class BatteryLevelChanged(val levelPercent: Double) : VehicleEvent()

    @Serializable
    data class ChargingStateChanged(val isCharging: Boolean) : VehicleEvent()

    @Serializable
    data class DrivingStateChanged(val isDriving: Boolean) : VehicleEvent()

    @Serializable
    data class PassengerStateChanged(val isPassenger: Boolean) : VehicleEvent()

    @Serializable
    data class ClimateStateChanged(val climateState: String) : VehicleEvent() // JSON
}