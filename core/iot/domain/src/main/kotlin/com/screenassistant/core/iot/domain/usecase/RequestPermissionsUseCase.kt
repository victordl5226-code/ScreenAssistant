package com.screenassistant.core.iot.domain.usecase

import com.screenassistant.core.iot.domain.model.auto.CarPermissions
import com.screenassistant.core.iot.domain.repository.CarPermission
import com.screenassistant.core.iot.domain.repository.HealthConnectRepository
import com.screenassistant.core.iot.domain.repository.CarAppRepository
import com.screenassistant.core.iot.domain.repository.HealthRecordType
import javax.inject.Inject

/**
 * UseCase: Solicita permisos requeridos al usuario.
 *
 * Orquesta la solicitud de permisos para:
 * - Health Connect (datos de salud)
 * - Android Auto / AAOS (micrófono, ubicación, contactos, vehículo, etc.)
 *
 * Lanza las UIs de permisos nativas correspondientes.
 */
class RequestPermissionsUseCase @Inject constructor(
    private val healthRepository: HealthConnectRepository,
    private val carRepository: CarAppRepository,
) {
    /**
     * Solicita permisos de Health Connect.
     *
     * @param recordTypes Tipos de datos a solicitar (por defecto: esenciales)
     * @return Tipos de datos que el usuario concedió
     */
    suspend operator fun invoke(recordTypes: Set<HealthRecordType> = HealthRecordType.essentialTypes): Set<HealthRecordType> {
        return healthRepository.requestPermissions(recordTypes)
    }

    /**
     * Solicita permisos del coche.
     *
     * @param permissions Permisos a solicitar (por defecto: esenciales)
     * @return Permisos concedidos tras la solicitud
     */
    suspend fun requestCarPermissions(permissions: List<CarPermission> = listOf(
        CarPermission.MICROPHONE,
        CarPermission.LOCATION,
    )): Set<CarPermission> {
        val granted = carRepository.requestCarPermissions(permissions)
        return granted.toGrantedSet()
    }

    /**
     * Solicita todos los permisos esenciales (Health + Car).
     *
     * @return Reporte combinado de permisos concedidos
     */
    suspend fun requestAllEssential(): CombinedPermissionsResult {
        val healthGranted = invoke(HealthRecordType.essentialTypes)
        val carGranted = requestCarPermissions()

        return CombinedPermissionsResult(
            healthGranted = healthGranted,
            carGranted = carGranted,
            healthRequired = HealthRecordType.essentialTypes,
            carRequired = setOf(CarPermission.MICROPHONE, CarPermission.LOCATION),
        )
    }
}

/**
 * Resultado combinado de solicitud de permisos.
 */
data class CombinedPermissionsResult(
    val healthGranted: Set<HealthRecordType>,
    val carGranted: Set<CarPermission>,
    val healthRequired: Set<HealthRecordType>,
    val carRequired: Set<CarPermission>,
) {
    /** Verifica si se concedieron todos los esenciales */
    val allEssentialGranted: Boolean
        get() = (healthRequired - healthGranted).isEmpty() && (carRequired - carGranted).isEmpty()

    /** Permisos de salud faltantes */
    val missingHealth: Set<HealthRecordType>
        get() = healthRequired - healthGranted

    /** Permisos de coche faltantes */
    val missingCar: Set<CarPermission>
        get() = carRequired - carGranted

    /** Resumen para UI */
    fun summary(): String {
        val healthStatus = if (missingHealth.isEmpty()) "OK" else "FALTAN: ${missingHealth.joinToString(", ")}"
        val carStatus = if (missingCar.isEmpty()) "OK" else "FALTAN: ${missingCar.joinToString(", ")}"
        return "Health: $healthStatus | Car: $carStatus"
    }
}

/**
 * Extensión para convertir CarPermissions a Set<CarPermission>.
 */
private fun CarPermissions.toGrantedSet(): Set<CarPermission> {
    val set = mutableSetOf<CarPermission>()
    if (microphone) set += CarPermission.MICROPHONE
    if (location) set += CarPermission.LOCATION
    if (contacts) set += CarPermission.CONTACTS
    if (sms) set += CarPermission.SMS
    if (callLogs) set += CarPermission.CALL_LOGS
    if (phoneState) set += CarPermission.PHONE_STATE
    if (vehicleData) set += CarPermission.VEHICLE_DATA
    if (isPassenger) set += CarPermission.PASSENGER_DETECTION
    return set
}