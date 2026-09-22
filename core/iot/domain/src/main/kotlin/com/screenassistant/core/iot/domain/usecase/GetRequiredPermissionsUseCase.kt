package com.screenassistant.core.iot.domain.usecase

import com.screenassistant.core.iot.domain.model.auto.CarPermissions
import com.screenassistant.core.iot.domain.repository.CarPermission
import com.screenassistant.core.iot.domain.repository.HealthConnectRepository
import com.screenassistant.core.iot.domain.repository.CarAppRepository
import com.screenassistant.core.iot.domain.repository.HealthRecordType
import javax.inject.Inject

/**
 * UseCase: Obtiene los permisos requeridos por la app para funcionalidades IoT.
 *
 * Determina qué permisos son necesarios según las features habilitadas
 * y cuáles ya están concedidos. No solicita permisos, solo informa.
 */
class GetRequiredPermissionsUseCase @Inject constructor(
    private val healthRepository: HealthConnectRepository,
    private val carRepository: CarAppRepository,
) {
    /**
     * Ejecuta y retorna el estado de permisos requeridos vs concedidos.
     */
    operator suspend fun invoke(): PermissionsReport = PermissionsReport(
        healthPermissions = getHealthPermissionsReport(),
        carPermissions = getCarPermissionsReport(),
    )

    /**
     * Obtiene reporte de permisos de Health Connect.
     */
    private suspend fun getHealthPermissionsReport(): HealthPermissionsReport {
        val essentialTypes = HealthRecordType.essentialTypes
        val allTypes = HealthRecordType.all

        return HealthPermissionsReport(
            requiredTypes = essentialTypes,
            optionalTypes = allTypes - essentialTypes,
            grantedTypes = healthRepository.getGrantedPermissions(),
        )
    }

    /**
     * Obtiene reporte de permisos del coche.
     */
    private suspend fun getCarPermissionsReport(): CarPermissionsReport {
        val currentPermissions = carRepository.getCarPermissions()
        val essentialPermissions = setOf(
            CarPermission.MICROPHONE,
            CarPermission.LOCATION,
        )
        val optionalPermissions = setOf(
            CarPermission.CONTACTS,
            CarPermission.SMS,
            CarPermission.CALL_LOGS,
            CarPermission.PHONE_STATE,
            CarPermission.VEHICLE_DATA,
            CarPermission.PASSENGER_DETECTION,
        )

        return CarPermissionsReport(
            requiredPermissions = essentialPermissions,
            optionalPermissions = optionalPermissions,
            grantedPermissions = currentPermissions.toGrantedSet(),
        )
    }
}

/**
 * Reporte completo de permisos.
 */
data class PermissionsReport(
    val healthPermissions: HealthPermissionsReport,
    val carPermissions: CarPermissionsReport,
) {
    /** Verifica si todos los permisos esenciales están concedidos */
    val hasAllEssentialPermissions: Boolean
        get() = healthPermissions.hasAllRequired && carPermissions.hasAllRequired

    /** Permisos esenciales faltantes (health + car) */
    val missingEssential: List<String>
        get() = healthPermissions.missingRequired.map { "Health: $it" } +
                carPermissions.missingRequired.map { "Car: $it" }

    /** Resumen para UI */
    fun summary(): String {
        val healthStatus = if (healthPermissions.hasAllRequired) "OK" else "FALTAN: ${healthPermissions.missingRequired.joinToString(", ")}"
        val carStatus = if (carPermissions.hasAllRequired) "OK" else "FALTAN: ${carPermissions.missingRequired.joinToString(", ")}"
        return "Health: $healthStatus | Car: $carStatus"
    }
}

/**
 * Reporte de permisos de Health Connect.
 */
data class HealthPermissionsReport(
    val requiredTypes: Set<HealthRecordType>,
    val optionalTypes: Set<HealthRecordType>,
    val grantedTypes: Set<HealthRecordType>,
) {
    /** Tipos requeridos que faltan */
    val missingRequired: Set<HealthRecordType>
        get() = requiredTypes - grantedTypes

    /** Tipos opcionales que faltan */
    val missingOptional: Set<HealthRecordType>
        get() = optionalTypes - grantedTypes

    /** Verifica si tiene todos los requeridos */
    val hasAllRequired: Boolean
        get() = missingRequired.isEmpty()

    /** Porcentaje de permisos concedidos */
    val grantPercentage: Int
        get() = if ((requiredTypes + optionalTypes).isEmpty()) 100 else
            (grantedTypes.size * 100) / (requiredTypes + optionalTypes).size
}

/**
 * Reporte de permisos del coche.
 */
data class CarPermissionsReport(
    val requiredPermissions: Set<CarPermission>,
    val optionalPermissions: Set<CarPermission>,
    val grantedPermissions: Set<CarPermission>,
) {
    /** Permisos requeridos que faltan */
    val missingRequired: Set<CarPermission>
        get() = requiredPermissions - grantedPermissions

    /** Permisos opcionales que faltan */
    val missingOptional: Set<CarPermission>
        get() = optionalPermissions - grantedPermissions

    /** Verifica si tiene todos los requeridos */
    val hasAllRequired: Boolean
        get() = missingRequired.isEmpty()

    /** Porcentaje de permisos concedidos */
    val grantPercentage: Int
        get() = if ((requiredPermissions + optionalPermissions).isEmpty()) 100 else
            (grantedPermissions.size * 100) / (requiredPermissions + optionalPermissions).size
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