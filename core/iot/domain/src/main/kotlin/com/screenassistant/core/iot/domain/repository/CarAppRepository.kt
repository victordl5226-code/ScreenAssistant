package com.screenassistant.core.iot.domain.repository

import com.screenassistant.core.iot.domain.model.auto.CarAppState
import com.screenassistant.core.iot.domain.model.auto.CarPermissions
import com.screenassistant.core.iot.domain.model.auto.VoiceCommand
import com.screenassistant.core.iot.domain.model.auto.VoiceResponse
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para funcionalidades Android Auto / AAOS.
 *
 * Gestiona la conexión con el vehículo, estado del coche,
 * comandos de voz en contexto automotriz y permisos específicos.
 */
interface CarAppRepository {
    /**
     * Emite el estado actual de la app en el coche.
     * Se actualiza automáticamente con cambios de conexión, conducción, etc.
     */
    fun observeCarState(): Flow<CarAppState>

    /**
     * Obtiene el estado actual (snapshot).
     */
    suspend fun getCurrentCarState(): CarAppState

    /**
     * Verifica si hay una sesión activa con el coche.
     */
    val isConnected: Boolean

    /**
     * Procesa un comando de voz en contexto automotriz.
     *
     * Incluye validación de seguridad (restricciones UX mientras se conduce),
     * enrutamiento a handlers apropiados y generación de respuesta.
     */
    suspend fun processVoiceCommand(command: VoiceCommand): VoiceResponse

    /**
     * Obtiene permisos actuales del coche.
     */
    suspend fun getCarPermissions(): CarPermissions

    /**
     * Solicita permisos específicos al usuario (via CarPermission API).
     */
    suspend fun requestCarPermissions(permissions: List<CarPermission>): CarPermissions

    /**
     * Obtiene datos del vehículo (velocidad, combustible, puertas, clima, etc.).
     * Disponible solo si se concede permiso VEHICLE_DATA.
     */
    suspend fun getVehicleData(): CarAppState?

    /**
     * Envía comando al vehículo (climatización, puertas, media, navegación).
     * Requiere permisos apropiados y validación de seguridad.
     */
    suspend fun sendVehicleCommand(command: VehicleCommand): VehicleCommandResult

    /**
     * Inicia navegación a un destino.
     */
    suspend fun startNavigation(destination: NavigationDestination): NavigationResult

    /**
     * Cancela navegación activa.
     */
    suspend fun cancelNavigation(): Boolean

    /**
     * Controla reproducción multimedia en el coche.
     */
    suspend fun controlMedia(action: MediaControlAction): Boolean

    /**
     * Registra listener para eventos del coche (velocidad, marcha, puertas).
     */
    fun observeVehicleEvents(): Flow<VehicleEvent>

    /**
     * Verifica si la app puede mostrar UI compleja (no conduciendo o pasajero).
     */
    fun canShowComplexUI(): Boolean

    /**
     * Notifica al sistema que la app está en primer plano en el coche.
     */
    suspend fun setAppForeground(inForeground: Boolean): Boolean
}