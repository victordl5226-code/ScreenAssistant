package com.screenassistant.core.iot.data.proactive

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.screenassistant.core.data.local.AppDatabase
import com.screenassistant.core.data.local.iot.IotDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker periódico para sincronización de datos IoT.
 *
 * Se ejecuta periódicamente (configurable, por defecto cada 15 minutos)
 * para:
 * 1. Limpiar datos antiguos de la base de datos
 * 2. Sincronizar métricas de salud (Health Connect)
 * 3. Actualizar estado de Home Assistant
 * 4. Persistir snapshots en base de datos local
 *
 * Se programa via WorkManager con [ExistingPeriodicWorkPolicy.KEEP].
 */
@HiltWorker
class IotSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase,
) : CoroutineWorker(context, params) {

    private val iotDao: IotDao = database.iotDao()

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting IoT sync...")

            // 1. Limpiar datos antiguos (>30 días)
            cleanupOldData()

            // 2. Health Connect: ya se sincroniza vía HealthConnectRepositoryImpl
            //    No necesita sync adicional aquí

            // 3. Home Assistant: el repositorio maneja su propio caché
            //    No necesita sync adicional aquí

            // 4. Matter: el repositorio maneja su propio caché en memoria
            //    Cuando Matter SDK esté disponible, se sincronizará aquí

            Log.d(TAG, "IoT sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during IoT sync", e)
            Result.retry()
        }
    }

    /**
     * Limpia datos antiguos de todas las tablas IoT.
     * Umbral: 30 días.
     */
    private suspend fun cleanupOldData() {
        val cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days
        try {
            iotDao.deleteOldHealthMetrics(cutoffTime)
            iotDao.deleteOldCarAppStates(cutoffTime)
            Log.d(TAG, "Cleaned up IoT data older than 30 days")
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup failed (non-fatal)", e)
        }
    }

    companion object {
        /** Tag para logs. */
        private const val TAG = "IotSyncWorker"

        /** Nombre único del trabajo periódico. */
        const val WORK_NAME = "iot_sync"

        /** Intervalo por defecto: 15 minutos. */
        const val DEFAULT_INTERVAL_MINUTES = 15

        /** Flex interval por defecto: 5 minutos. */
        const val DEFAULT_FLEX_MINUTES = 5
    }
}
