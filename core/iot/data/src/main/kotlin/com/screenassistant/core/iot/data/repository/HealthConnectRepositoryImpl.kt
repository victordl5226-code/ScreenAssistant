package com.screenassistant.core.iot.data.repository

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import com.screenassistant.core.iot.domain.model.wearables.DataSource
import com.screenassistant.core.iot.domain.model.wearables.HealthMetrics
import com.screenassistant.core.iot.domain.repository.HealthRecord
import com.screenassistant.core.iot.domain.repository.HealthRecordType
import com.screenassistant.core.iot.domain.repository.HealthConnectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Implementación de [HealthConnectRepository] usando el SDK Health Connect.
 *
 * Health Connect SDK 1.1.0-alpha02 es inestable y su API cambia frecuentemente.
 * Esta implementación:
 * 1. Verifica disponibilidad del SDK al inicio
 * 2. Usa reflexión para operaciones de lectura (resistente a cambios de API)
 * 3. Cachea métricas en [MutableStateFlow] para snapshots rápidos
 * 4. Gestiona permisos via [HealthConnectClient.permissionController]
 * 5. Degradación graceful cuando el SDK no está disponible
 *
 * Lecturas: usa reflexión con `KClass` (requisito de alpha02).
 * Escrituras: stub pendiente de implementación cuando el SDK esté estable.
 *
 * @param context Contexto de la aplicación
 */
@Singleton
class HealthConnectRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectRepository {

    companion object {
        private const val TAG = "HealthConnectRepo"
    }

    private val healthConnectClient: HealthConnectClient? by lazy {
        try {
            val status = HealthConnectClient.getSdkStatus(context)
            if (status == HealthConnectClient.SDK_AVAILABLE) {
                Log.d(TAG, "Health Connect SDK available")
                HealthConnectClient.getOrCreate(context)
            } else {
                Log.w(TAG, "Health Connect SDK not available (status=$status)")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Health Connect SDK", e)
            null
        }
    }

    private val _metricsFlow = MutableStateFlow(HealthMetrics())
    private val _permissionChangesFlow = MutableStateFlow(emptySet<HealthRecordType>())

    // ── Lectura ──────────────────────────────────────────────────────────────

    override fun observeHealthMetrics(): Flow<HealthMetrics> = _metricsFlow.asStateFlow()

    override suspend fun getCurrentHealthMetrics(): HealthMetrics = _metricsFlow.value

    override suspend fun forceSync(): HealthMetrics {
        val client = healthConnectClient
            ?: return _metricsFlow.value.copy(source = DataSource.UNKNOWN)

        return try {
            val now = Clock.System.now()
            val startMs = now.toEpochMilliseconds() - (24 * 60 * 60 * 1000)
            val endMs = now.toEpochMilliseconds()

            val steps = safeReadLong(client, "StepsRecord", "getCount", startMs, endMs)
            val calories = safeReadDouble(client, "ActiveCaloriesBurnedRecord", "getEnergy", "getInKilocalories", startMs, endMs)
            val distance = safeReadDouble(client, "DistanceRecord", "getDistance", "getInMeters", startMs, endMs)
            val heartRate = safeReadInt(client, "HeartRateRecord", "getBpm", startMs, endMs)
            val bloodOxygen = safeReadPercent(client, "BloodOxygenRecord", startMs, endMs)
            val weightKg = safeReadDouble(client, "WeightRecord", "getWeight", "getInKilograms", startMs, endMs)
            val sleepHours = safeReadDurationHours(client, "SleepSessionRecord", startMs, endMs)

            val metrics = HealthMetrics(
                steps = if (steps > 0) com.screenassistant.core.iot.domain.model.wearables.StepsMetric(
                    timestamp = now, value = steps,
                ) else null,
                heartRate = if (heartRate > 0) com.screenassistant.core.iot.domain.model.wearables.HeartRateMetric(
                    timestamp = now, value = heartRate,
                ) else null,
                caloriesBurned = if (calories > 0.0) com.screenassistant.core.iot.domain.model.wearables.CaloriesMetric(
                    timestamp = now, value = calories,
                ) else null,
                distanceMeters = if (distance > 0.0) com.screenassistant.core.iot.domain.model.wearables.DistanceMetric(
                    timestamp = now, value = distance,
                ) else null,
                sleepHours = if (sleepHours > 0.0) com.screenassistant.core.iot.domain.model.wearables.SleepHoursMetric(
                    timestamp = now, value = sleepHours,
                ) else null,
                bloodOxygen = if (bloodOxygen > 0) com.screenassistant.core.iot.domain.model.wearables.BloodOxygenMetric(
                    timestamp = now, value = bloodOxygen,
                ) else null,
                weight = if (weightKg > 0.0) com.screenassistant.core.iot.domain.model.wearables.WeightMetric(
                    timestamp = now, value = weightKg,
                ) else null,
                lastSynced = now,
                source = DataSource.HEALTH_CONNECT,
            )

            _metricsFlow.value = metrics
            Log.d(TAG, "Health Connect sync completed: ${metrics.summary()}")
            metrics
        } catch (e: Exception) {
            Log.e(TAG, "Error during Health Connect sync", e)
            _metricsFlow.value.copy(source = DataSource.HEALTH_CONNECT)
        }
    }

    override suspend fun getMetricsInRange(
        startTime: Long,
        endTime: Long,
        recordTypes: Set<HealthRecordType>,
    ): HealthMetrics = forceSync()

    // ── Permisos ─────────────────────────────────────────────────────────────

    override suspend fun isHealthConnectAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun hasReadPermission(recordType: HealthRecordType): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val kclass = getKClassForRecordType(recordType) ?: return false
            val granted = client.permissionController.getGrantedPermissions()
            granted.any { it.toString().contains(kclass.simpleName ?: "") }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions for $recordType", e)
            false
        }
    }

    override suspend fun hasWritePermission(recordType: HealthRecordType): Boolean =
        hasReadPermission(recordType)

    override suspend fun requestPermissions(recordTypes: Set<HealthRecordType>): Set<HealthRecordType> {
        val client = healthConnectClient ?: return emptySet()
        return try {
            val kclasses = recordTypes.mapNotNull { getKClassForRecordType(it) }
            if (kclasses.isEmpty()) return emptySet()

            // En alpha02, requestPermissions puede no existir con esta firma.
            // Usamos getGrantedPermissions como fallback.
            val granted = try {
                client.permissionController.getGrantedPermissions()
            } catch (e: Exception) {
                emptySet()
            }

            val grantedTypes = recordTypes.filter { type ->
                val kclass = getKClassForRecordType(type)
                kclass != null && granted.any { it.toString().contains(kclass.simpleName ?: "") }
            }.toSet()

            _permissionChangesFlow.update { grantedTypes }
            grantedTypes
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permissions", e)
            emptySet()
        }
    }

    override suspend fun getGrantedPermissions(): Set<HealthRecordType> {
        val client = healthConnectClient ?: return emptySet()
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            HealthRecordType.all.filter { type ->
                val kclass = getKClassForRecordType(type)
                kclass != null && granted.any { it.toString().contains(kclass.simpleName ?: "") }
            }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting granted permissions", e)
            emptySet()
        }
    }

    override fun observePermissionChanges(): Flow<Set<HealthRecordType>> = _permissionChangesFlow.asStateFlow()

    // ── Escritura ────────────────────────────────────────────────────────────

    override suspend fun writeRecord(record: HealthRecord): Boolean {
        Log.d(TAG, "Write record requested: ${record::class.simpleName} — pending SDK stabilization")
        return false
    }

    override suspend fun deleteRecord(recordId: String, recordType: HealthRecordType): Boolean {
        Log.w(TAG, "Delete by ID not supported in Health Connect")
        return false
    }

    // ── Fuente de datos ──────────────────────────────────────────────────────

    override suspend fun getPrimaryDataSource(): DataSource = DataSource.HEALTH_CONNECT

    override suspend fun setPrimaryDataSource(source: DataSource): Boolean =
        source == DataSource.HEALTH_CONNECT

    // ── Helpers de lectura via reflexión ──────────────────────────────────────
    // Usa KClass (requisito de Health Connect alpha02) para operaciones de lectura.

    @Suppress("UNCHECKED_CAST")
    private suspend fun safeReadLong(
        client: HealthConnectClient,
        recordSimpleName: String,
        getterMethod: String,
        startMs: Long,
        endMs: Long,
    ): Long {
        return try {
            val recordKClass = findKClass("androidx.health.connect.client.records.$recordSimpleName")
                ?: return 0L
            val requestKClass = findKClass("androidx.health.connect.client.request.ReadRecordsRequest")
                ?: return 0L
            val timeRangeKClass = findKClass("androidx.health.connect.client.time.TimeRangeFilter")
                ?: return 0L

            val timeRange = timeRangeKClass.java.getMethod(
                "between",
                java.time.Instant::class.java,
                java.time.Instant::class.java,
            ).invoke(
                null,
                java.time.Instant.ofEpochMilli(startMs),
                java.time.Instant.ofEpochMilli(endMs),
            )

            val constructor = requestKClass.java.constructors.firstOrNull { it.parameterCount >= 2 } ?: return 0L
            val request = constructor.newInstance(recordKClass.java, timeRange)

            val response = client.readRecords(request as androidx.health.connect.client.request.ReadRecordsRequest<androidx.health.connect.client.records.Record>)
            val records = response.records
            records.sumOf { record ->
                try {
                    (record.javaClass.getMethod(getterMethod).invoke(record) as? Number)?.toLong() ?: 0L
                } catch (e: Exception) { 0L }
            }
        } catch (e: Exception) {
            Log.d(TAG, "$recordSimpleName not available: ${e.message}")
            0L
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun safeReadDouble(
        client: HealthConnectClient,
        recordSimpleName: String,
        getterMethod: String,
        innerGetter: String,
        startMs: Long,
        endMs: Long,
    ): Double {
        return try {
            val recordKClass = findKClass("androidx.health.connect.client.records.$recordSimpleName")
                ?: return 0.0
            val requestKClass = findKClass("androidx.health.connect.client.request.ReadRecordsRequest")
                ?: return 0.0
            val timeRangeKClass = findKClass("androidx.health.connect.client.time.TimeRangeFilter")
                ?: return 0.0

            val timeRange = timeRangeKClass.java.getMethod(
                "between",
                java.time.Instant::class.java,
                java.time.Instant::class.java,
            ).invoke(
                null,
                java.time.Instant.ofEpochMilli(startMs),
                java.time.Instant.ofEpochMilli(endMs),
            )

            val constructor = requestKClass.java.constructors.firstOrNull { it.parameterCount >= 2 } ?: return 0.0
            val request = constructor.newInstance(recordKClass.java, timeRange)

            val response = client.readRecords(request as androidx.health.connect.client.request.ReadRecordsRequest<androidx.health.connect.client.records.Record>)
            val records = response.records
            records.sumOf { record ->
                try {
                    val inner = record.javaClass.getMethod(getterMethod).invoke(record)
                    (inner.javaClass.getMethod(innerGetter).invoke(inner) as? Number)?.toDouble() ?: 0.0
                } catch (e: Exception) { 0.0 }
            }
        } catch (e: Exception) {
            Log.d(TAG, "$recordSimpleName.$getterMethod not available: ${e.message}")
            0.0
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun safeReadInt(
        client: HealthConnectClient,
        recordSimpleName: String,
        getterMethod: String,
        startMs: Long,
        endMs: Long,
    ): Int {
        return try {
            val recordKClass = findKClass("androidx.health.connect.client.records.$recordSimpleName")
                ?: return 0
            val requestKClass = findKClass("androidx.health.connect.client.request.ReadRecordsRequest")
                ?: return 0
            val timeRangeKClass = findKClass("androidx.health.connect.client.time.TimeRangeFilter")
                ?: return 0

            val timeRange = timeRangeKClass.java.getMethod(
                "between",
                java.time.Instant::class.java,
                java.time.Instant::class.java,
            ).invoke(
                null,
                java.time.Instant.ofEpochMilli(startMs),
                java.time.Instant.ofEpochMilli(endMs),
            )

            val constructor = requestKClass.java.constructors.firstOrNull { it.parameterCount >= 2 } ?: return 0
            val request = constructor.newInstance(recordKClass.java, timeRange)

            val response = client.readRecords(request as androidx.health.connect.client.request.ReadRecordsRequest<androidx.health.connect.client.records.Record>)
            val records = response.records
            records.maxOfOrNull { record ->
                try {
                    (record.javaClass.getMethod(getterMethod).invoke(record) as? Number)?.toInt() ?: 0
                } catch (e: Exception) { 0 }
            } ?: 0
        } catch (e: Exception) {
            Log.d(TAG, "$recordSimpleName.$getterMethod not available: ${e.message}")
            0
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun safeReadPercent(
        client: HealthConnectClient,
        recordSimpleName: String,
        startMs: Long,
        endMs: Long,
    ): Int {
        return try {
            val recordKClass = findKClass("androidx.health.connect.client.records.$recordSimpleName")
                ?: return 0
            val requestKClass = findKClass("androidx.health.connect.client.request.ReadRecordsRequest")
                ?: return 0
            val timeRangeKClass = findKClass("androidx.health.connect.client.time.TimeRangeFilter")
                ?: return 0

            val timeRange = timeRangeKClass.java.getMethod(
                "between",
                java.time.Instant::class.java,
                java.time.Instant::class.java,
            ).invoke(
                null,
                java.time.Instant.ofEpochMilli(startMs),
                java.time.Instant.ofEpochMilli(endMs),
            )

            val constructor = requestKClass.java.constructors.firstOrNull { it.parameterCount >= 2 } ?: return 0
            val request = constructor.newInstance(recordKClass.java, timeRange)

            val response = client.readRecords(request as androidx.health.connect.client.request.ReadRecordsRequest<androidx.health.connect.client.records.Record>)
            val records = response.records
            val latest = records.lastOrNull() ?: return 0

            val percentage = latest.javaClass.getMethod("getPercentage").invoke(latest)
            val value = percentage.javaClass.getMethod("getValue").invoke(percentage) as? Number
            ((value?.toDouble() ?: 0.0) * 100).toInt()
        } catch (e: Exception) {
            Log.d(TAG, "$recordSimpleName percentage not available: ${e.message}")
            0
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun safeReadDurationHours(
        client: HealthConnectClient,
        recordSimpleName: String,
        startMs: Long,
        endMs: Long,
    ): Double {
        return try {
            val recordKClass = findKClass("androidx.health.connect.client.records.$recordSimpleName")
                ?: return 0.0
            val requestKClass = findKClass("androidx.health.connect.client.request.ReadRecordsRequest")
                ?: return 0.0
            val timeRangeKClass = findKClass("androidx.health.connect.client.time.TimeRangeFilter")
                ?: return 0.0

            val timeRange = timeRangeKClass.java.getMethod(
                "between",
                java.time.Instant::class.java,
                java.time.Instant::class.java,
            ).invoke(
                null,
                java.time.Instant.ofEpochMilli(startMs),
                java.time.Instant.ofEpochMilli(endMs),
            )

            val constructor = requestKClass.java.constructors.firstOrNull { it.parameterCount >= 2 } ?: return 0.0
            val request = constructor.newInstance(recordKClass.java, timeRange)

            val response = client.readRecords(request as androidx.health.connect.client.request.ReadRecordsRequest<androidx.health.connect.client.records.Record>)
            val records = response.records
            val totalMinutes = records.sumOf { record ->
                try {
                    val start = record.javaClass.getMethod("getStartTime").invoke(record) as java.time.Instant
                    val end = record.javaClass.getMethod("getEndTime").invoke(record) as java.time.Instant
                    java.time.Duration.between(start, end).toMinutes()
                } catch (e: Exception) { 0L }
            }
            totalMinutes / 60.0
        } catch (e: Exception) {
            Log.d(TAG, "$recordSimpleName duration not available: ${e.message}")
            0.0
        }
    }

    // ── Helpers generales ────────────────────────────────────────────────────

    private fun findKClass(fqn: String): kotlin.reflect.KClass<*>? {
        return try {
            Class.forName(fqn).kotlin
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getKClassForRecordType(type: HealthRecordType): kotlin.reflect.KClass<*>? {
        val fqn = when (type) {
            HealthRecordType.STEPS -> "androidx.health.connect.client.records.StepsRecord"
            HealthRecordType.HEART_RATE -> "androidx.health.connect.client.records.HeartRateRecord"
            HealthRecordType.SLEEP_SESSION -> "androidx.health.connect.client.records.SleepSessionRecord"
            HealthRecordType.BLOOD_OXYGEN -> "androidx.health.connect.client.records.BloodOxygenRecord"
            HealthRecordType.WEIGHT -> "androidx.health.connect.client.records.WeightRecord"
            HealthRecordType.ACTIVE_CALORIES_BURNED -> "androidx.health.connect.client.records.ActiveCaloriesBurnedRecord"
            HealthRecordType.DISTANCE -> "androidx.health.connect.client.records.DistanceRecord"
            HealthRecordType.EXERCISE_SESSION -> "androidx.health.connect.client.records.ExerciseSessionRecord"
            HealthRecordType.HEIGHT -> "androidx.health.connect.client.records.HeightRecord"
            HealthRecordType.BLOOD_PRESSURE -> "androidx.health.connect.client.records.BloodPressureRecord"
            HealthRecordType.BODY_TEMPERATURE -> "androidx.health.connect.client.records.BodyTemperatureRecord"
            HealthRecordType.RESPIRATORY_RATE -> "androidx.health.connect.client.records.RespiratoryRateRecord"
            HealthRecordType.VO2_MAX -> "androidx.health.connect.client.records.Vo2MaxRecord"
            HealthRecordType.BODY_FAT -> "androidx.health.connect.client.records.BodyFatRecord"
            HealthRecordType.HYDRATION -> "androidx.health.connect.client.records.HydrationRecord"
            HealthRecordType.BASAL_BODY_TEMPERATURE -> "androidx.health.connect.client.records.BasalBodyTemperatureRecord"
            HealthRecordType.BODY_WATER_MASS -> "androidx.health.connect.client.records.BodyWaterMassRecord"
            HealthRecordType.BONE_MASS -> "androidx.health.connect.client.records.BoneMassRecord"
            HealthRecordType.LEAN_BODY_MASS -> "androidx.health.connect.client.records.LeanBodyMassRecord"
            HealthRecordType.MENSTRUATION_FLOW -> "androidx.health.connect.client.records.MenstruationFlowRecord"
            HealthRecordType.OVULATION_TEST -> "androidx.health.connect.client.records.OvulationTestRecord"
            HealthRecordType.CERVICAL_MUCUS -> "androidx.health.connect.client.records.CervicalMucusRecord"
            else -> return null
        }
        return findKClass(fqn)
    }
}
