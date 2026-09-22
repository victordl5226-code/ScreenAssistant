package com.screenassistant.core.data.proactive

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.screenassistant.core.domain.repository.UserPatternRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker que limpia patrones antiguos y recalcula frecuencias.
 *
 * Se ejecuta una vez al día para mantener el almacén de patrones
 * del usuario libre de entradas obsoletas (mayores a 30 días).
 * Esto evita la acumulación de datos y mejora el rendimiento
 * del motor de sugerencias proactivas.
 *
 * @constructor Crea el worker con las dependencias inyectadas por Hilt.
 * @param context Contexto Android del worker
 * @param params Parámetros de trabajo (WorkManager)
 * @param patternRepository Repositorio de acceso a patrones de usuario
 */
@HiltWorker
class PatternLearnerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val patternRepository: UserPatternRepository
) : CoroutineWorker(context, params) {

    /**
     * Ejecuta la limpieza de patrones antiguos.
     *
     * Elimina todos los patrones cuya última observación sea anterior
     * a 30 días. Si la limpieza falla, solicita reintentar.
     *
     * @return [Result.success] si la limpieza se completó,
     *         [Result.retry] si ocurrió un error recuperable
     */
    override suspend fun doWork(): Result {
        return try {
            patternRepository.clearOldPatterns(olderThanDays = 30)
            Log.d(TAG, "Patrones antiguos limpiados correctamente")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing old patterns", e)
            Result.retry()
        }
    }

    companion object {
        /** Tag para logs del worker. */
        private const val TAG = "PatternLearner"

        /** Nombre único del trabajo periódico para WorkManager. */
        const val WORK_NAME = "pattern_learner"
    }
}
