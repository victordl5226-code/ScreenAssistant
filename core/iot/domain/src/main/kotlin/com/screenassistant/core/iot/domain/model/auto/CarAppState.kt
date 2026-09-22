package com.screenassistant.core.iot.domain.model.auto

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Estado de la app en Android Auto / AAOS.
 *
 * Representa el estado actual de la aplicación cuando se ejecuta
 * en el entorno automotriz (Android Auto proyección o AAOS nativo).
 *
 * @property isConnected Si hay una sesión activa con el coche
 * @property connectionType Tipo de conexión (proyección AAOS, AAOS nativo)
 * @property carModel Modelo del vehículo (si disponible)
 * @property headUnitType Tipo de unidad central (integrada, aftermarket)
 * @property screenSize Tamaño de pantalla en pulgadas
 * @property screenDensity Densidad de pantalla
 * @property isDriving Si el vehículo está en movimiento
 * @property speedKmh Velocidad actual en km/h
 * @property gearPosición actual de la marcha
 * @property fuelLevel Nivel de combustible (%)
 * @property batteryLevel Nivel de batería EV (%)
 * @property rangeKm Autonomía restante (km)
 * @property doorStates Estado de las puertas
 * @property windowStates Estado de las ventanas
 * @property climateState Estado de climatización
 * @property mediaState Estado del reproductor multimedia
 * @property navigationState Estado de navegación activa
 * @property voiceAssistantState Estado del asistente de voz
 * @property permissions Permisos concedidos (micrófono, ubicación, contactos, SMS)
 * @property lastUpdated Timestamp de última actualización
 */
@Serializable
data class CarAppState(
    val isConnected: Boolean = false,
    val connectionType: ConnectionType = ConnectionType.UNKNOWN,
    val carModel: String? = null,
    val headUnitType: HeadUnitType = HeadUnitType.UNKNOWN,
    val screenSize: Double? = null,
    val screenDensity: Int? = null,
    val isDriving: Boolean = false,
    val speedKmh: Double = 0.0,
    val gear: GearPosition = GearPosition.UNKNOWN,
    val fuelLevel: Double? = null,
    val batteryLevel: Double? = null,
    val rangeKm: Double? = null,
    @Contextual val doorStates: Map<String, DoorState> = emptyMap(),
    @Contextual val windowStates: Map<String, WindowState> = emptyMap(),
    val climateState: ClimateState? = null,
    val mediaState: MediaState? = null,
    val navigationState: NavigationState? = null,
    val voiceAssistantState: VoiceAssistantState = VoiceAssistantState.IDLE,
    val permissions: CarPermissions = CarPermissions(),
    val lastUpdated: Instant = Clock.System.now(),
) {
    /**
     * Verifica si la app puede mostrar UI compleja (no conduciendo o pasajero).
     * En Android Auto, la UX está restringida mientras se conduce.
     */
    val canShowComplexUI: Boolean
        get() = !isDriving || permissions.isPassenger

    /**
     * Verifica si el micrófono está disponible para comandos de voz.
     */
    val canUseMicrophone: Boolean
        get() = permissions.microphone && isConnected

    /**
     * Verifica si hay datos del vehículo disponibles.
     */
    val hasVehicleData: Boolean
        get() = fuelLevel != null || batteryLevel != null || rangeKm != null ||
                doorStates.isNotEmpty() || climateState != null

    /**
     * Crea un estado desconectado por defecto.
     */
    companion object {
        fun disconnected(): CarAppState = CarAppState(
            isConnected = false,
            connectionType = ConnectionType.DISCONNECTED,
        )
    }
}

/**
 * Tipo de conexión con el vehículo.
 */
enum class ConnectionType {
    /** Android Auto proyección (teléfono -> pantalla coche) */
    ANDROID_AUTO_PROJECTION,
    /** AAOS nativo (app instalada en el coche) */
    AAOS_NATIVE,
    /** CarPlay (iOS) - solo para referencia */
    CARPLAY,
    /** Desconectado */
    DISCONNECTED,
    /** Desconocido */
    UNKNOWN,
}

/**
 * Tipo de unidad central.
 */
enum class HeadUnitType {
    INTEGRATED,
    AFTERMARKET,
    PHONE_PROJECTION,
    UNKNOWN,
}

/**
 * Posición de la marcha.
 */
enum class GearPosition {
    PARK,
    REVERSE,
    NEUTRAL,
    DRIVE,
    SPORT,
    LOW,
    MANUAL_1,
    MANUAL_2,
    MANUAL_3,
    MANUAL_4,
    MANUAL_5,
    MANUAL_6,
    UNKNOWN,
}

/**
 * Estado de una puerta.
 */
enum class DoorState {
    CLOSED,
    OPEN,
    AJAR,
    UNKNOWN,
}

/**
 * Estado de una ventana.
 */
enum class WindowState {
    CLOSED,
    OPEN,
    VENTED,
    MOVING,
    UNKNOWN,
}

/**
 * Estado de climatización del vehículo.
 */
@Serializable
data class ClimateState(
    val driverTempC: Double?,
    val passengerTempC: Double?,
    val rearTempC: Double?,
    val fanSpeed: Int?, // 0-100%
    val isAcOn: Boolean,
    val isAutoMode: Boolean,
    val isRecirculationOn: Boolean,
    val isDefrostOn: Boolean,
    val isRearDefrostOn: Boolean,
    @Contextual val seatHeating: Map<String, Int> = emptyMap(), // seatId -> level 0-3
    @Contextual val seatVentilation: Map<String, Int> = emptyMap(),
    val steeringWheelHeating: Boolean = false,
)

/**
 * Estado del reproductor multimedia.
 */
@Serializable
data class MediaState(
    val isPlaying: Boolean,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtUri: String?,
    val durationMs: Long?,
    val positionMs: Long?,
    val source: MediaSource,
    val queue: List<MediaQueueItem> = emptyList(),
    val shuffleMode: ShuffleMode = ShuffleMode.OFF,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)

/**
 * Fuente de medios.
 */
enum class MediaSource {
    BLUETOOTH,
    USB,
    STREAMING,
    RADIO_FM,
    RADIO_AM,
    RADIO_DAB,
    LOCAL,
    UNKNOWN,
}

/**
 * Item en la cola de reproducción.
 */
@Serializable
data class MediaQueueItem(
    val id: String,
    val title: String,
    val artist: String?,
    val albumArtUri: String?,
    val durationMs: Long?,
)

/**
 * Modo aleatorio.
 */
enum class ShuffleMode {
    OFF,
    ALL,
    GROUP,
}

/**
 * Modo repetición.
 */
enum class RepeatMode {
    OFF,
    ONE,
    ALL,
}

/**
 * Estado de navegación activa.
 */
@Serializable
data class NavigationState(
    val isNavigating: Boolean,
    val destination: String?,
    val eta: Instant?,
    val remainingDistanceM: Double?,
    val remainingTimeSeconds: Long?,
    val nextInstruction: String?,
    val nextInstructionDistanceM: Double?,
    val trafficLevel: TrafficLevel,
    val routePreview: String?, // URL o encoded polyline
)

/**
 * Nivel de tráfico.
 */
enum class TrafficLevel {
    NONE,
    LIGHT,
    MODERATE,
    HEAVY,
    SEVERE,
    UNKNOWN,
}

/**
 * Estado del asistente de voz en el coche.
 */
enum class VoiceAssistantState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR,
    UNAVAILABLE,
}