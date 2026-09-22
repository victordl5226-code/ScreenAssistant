package com.screenassistant.core.iot.domain.repository

import com.screenassistant.core.iot.domain.model.wearables.DataSource
import com.screenassistant.core.iot.domain.model.wearables.HealthMetrics
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para Health Connect (Android) / fuentes de datos de salud.
 *
 * Abstrae la lectura/escritura de datos de salud desde Health Connect
 * y otras fuentes (Google Fit, Samsung Health, wearables directos).
 *
 * Health Connect es el almacén centralizado de datos de salud en Android 14+.
 * Requiere permisos explícitos del usuario por tipo de dato.
 */
interface HealthConnectRepository {
    /**
     * Emite métricas de salud agregadas.
     * Se actualiza cuando hay nuevos datos en Health Connect.
     */
    fun observeHealthMetrics(): Flow<HealthMetrics>

    /**
     * Obtiene métricas actuales (snapshot).
     */
    suspend fun getCurrentHealthMetrics(): HealthMetrics

    /**
     * Fuerza sincronización desde todas las fuentes conectadas.
     */
    suspend fun forceSync(): HealthMetrics

    /**
     * Verifica si Health Connect está disponible en el dispositivo.
     */
    suspend fun isHealthConnectAvailable(): Boolean

    /**
     * Verifica si la app tiene permisos para leer un tipo de dato.
     */
    suspend fun hasReadPermission(recordType: HealthRecordType): Boolean

    /**
     * Verifica si la app tiene permisos para escribir un tipo de dato.
     */
    suspend fun hasWritePermission(recordType: HealthRecordType): Boolean

    /**
     * Solicita permisos al usuario para tipos de datos específicos.
     * Lanza la UI de permisos de Health Connect.
     */
    suspend fun requestPermissions(recordTypes: Set<HealthRecordType>): Set<HealthRecordType>

    /**
     * Obtiene los permisos concedidos actualmente.
     */
    suspend fun getGrantedPermissions(): Set<HealthRecordType>

    /**
     * Obtiene métricas de un rango de tiempo específico.
     */
    suspend fun getMetricsInRange(
        startTime: Long,
        endTime: Long,
        recordTypes: Set<HealthRecordType> = HealthRecordType.all,
    ): HealthMetrics

    /**
     * Escribe un registro de salud (ej: peso, presión arterial manual).
     */
    suspend fun writeRecord(record: HealthRecord): Boolean

    /**
     * Elimina un registro de salud.
     */
    suspend fun deleteRecord(recordId: String, recordType: HealthRecordType): Boolean

    /**
     * Obtiene la fuente de datos principal configurada.
     */
    suspend fun getPrimaryDataSource(): DataSource

    /**
     * Cambia la fuente de datos principal.
     */
    suspend fun setPrimaryDataSource(source: DataSource): Boolean

    /**
     * Registra callback para cambios en permisos.
     */
    fun observePermissionChanges(): Flow<Set<HealthRecordType>>
}