package com.screenassistant.core.domain.model.proactive

import com.screenassistant.core.domain.model.TemporalContext

/**
 * Nivel de batería enriquecido con clasificaciones derivadas.
 *
 * @property level Nivel de batería (0-100)
 * @property isCharging true si el dispositivo está cargando
 * @property isLow true si el nivel está por debajo del umbral bajo (≤20%)
 * @property isCritical true si el nivel está en umbral crítico (≤5%)
 */
data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean
) {
    val isLow: Boolean get() = level <= 20
    val isCritical: Boolean get() = level <= 5

    companion object {
        /**
         * Crea [BatteryInfo] a partir de nivel y estado de carga.
         *
         * @param level Nivel de batería (0-100)
         * @param isCharging true si está cargando
         * @return BatteryInfo con clasificaciones calculadas
         */
        fun from(level: Int, isCharging: Boolean): BatteryInfo =
            BatteryInfo(level = level, isCharging = isCharging)
    }
}

/**
 * Información de la pantalla actual en formato de dominio.
 *
 * @property packageName Paquete de la app en primer plano (puede ser null)
 * @property text Texto visible en pantalla
 */
data class ScreenInfo(
    val packageName: String?,
    val text: String
)

/**
 * Estado de conectividad de red en formato de dominio.
 *
 * @property wifi true si hay WiFi activo
 * @property mobile true si hay datos móviles activos
 * @property isConnected true si hay alguna conexión de red disponible
 */
data class ConnectivityInfo(
    val wifi: Boolean,
    val mobile: Boolean
) {
    val isConnected: Boolean get() = wifi || mobile

    companion object {
        /**
         * Crea [ConnectivityInfo] a partir de los estados de red.
         *
         * @param wifi true si hay WiFi activo
         * @param mobile true si hay datos móviles activos
         * @return ConnectivityInfo con isConnected calculado
         */
        fun from(wifi: Boolean, mobile: Boolean): ConnectivityInfo =
            ConnectivityInfo(wifi = wifi, mobile = mobile)
    }
}

/**
 * Tipo de ubicación estimada del usuario.
 * Se usa para evaluar condiciones de ubicación en [ProactiveRule].
 */
enum class LocationType {
    /** El usuario está en su ubicación hogar */
    HOME,
    /** El usuario está en su ubicación de trabajo */
    WORK,
    /** El usuario está en movimiento (no estacionario) */
    MOVING,
    /** Ubicación desconocida o no determinada */
    UNKNOWN
}

/**
 * Contexto agregado que consolida todas las señales de [ContextSignal] en una
 * estructura unificada lista para evaluar contra [ProactiveRule].
 *
 * @property temporal Contexto temporal del momento
 * @property battery Estado de la batería enriquecido
 * @property upcomingEvents Próximos eventos del calendario
 * @property screen Información de la pantalla actual
 * @property connectivity Estado de conectividad de red
 * @property location Ubicación estimada del usuario
 */
data class AggregatedContext(
    val temporal: TemporalContext,
    val battery: BatteryInfo,
    val upcomingEvents: List<CalendarEvent>,
    val screen: ScreenInfo,
    val connectivity: ConnectivityInfo,
    val location: LocationType
) {
    companion object {
        /**
         * Crea un [AggregatedContext] con valores por defecto para testing.
         * Útil en tests y para inicialización del sistema.
         *
         * @param temporal Contexto temporal (requerido)
         * @return AggregatedContext con valores por defecto para el resto de campos
         */
        fun withDefaults(temporal: TemporalContext): AggregatedContext =
            AggregatedContext(
                temporal = temporal,
                battery = BatteryInfo(level = 100, isCharging = false),
                upcomingEvents = emptyList(),
                screen = ScreenInfo(packageName = null, text = ""),
                connectivity = ConnectivityInfo(wifi = true, mobile = false),
                location = LocationType.UNKNOWN
            )
    }
}
