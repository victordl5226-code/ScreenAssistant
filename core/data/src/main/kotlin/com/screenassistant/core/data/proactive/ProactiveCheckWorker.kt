package com.screenassistant.core.data.proactive

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.screenassistant.core.domain.engine.ProactiveEngine
import com.screenassistant.core.domain.engine.PredictiveEngine
import com.screenassistant.core.domain.repository.ContextAggregatorRepository
import com.screenassistant.core.domain.repository.ProactiveRuleRepository
import com.screenassistant.core.domain.repository.UserPatternRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker periódico que evalúa reglas proactivas y entrega sugerencias al
 * [ProactiveSuggestionManager] para su presentación al usuario.
 *
 * Se ejecuta cada 15 minutos (configurable via [ProactivePreferences]).
 * Obtiene las reglas habilitadas, las evalúa contra el contexto agregado
 * actual y entrega las sugerencias generadas al manager, que se encarga
 * de los filtros de negocio (cooldown, quiet hours, expiración) y de
 * exponerlas a la UI.
 *
 * @constructor Crea el worker con las dependencias inyectadas por Hilt.
 * @param context Contexto Android del worker
 * @param params Parámetros de trabajo (WorkManager)
 * @param proactiveEngine Motor de evaluación de reglas proactivas
 * @param ruleRepository Repositorio de acceso a reglas proactivas
 * @param contextAggregator Repositorio de contexto agregado del dispositivo
 * @param suggestionManager Gestor centralizado de sugerencias proactivas
 */
@HiltWorker
class ProactiveCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val proactiveEngine: ProactiveEngine,
    private val predictiveEngine: PredictiveEngine,
    private val ruleRepository: ProactiveRuleRepository,
    private val contextAggregator: ContextAggregatorRepository,
    private val patternRepository: UserPatternRepository,
    private val suggestionManager: ProactiveSuggestionManager
) : CoroutineWorker(context, params) {

    /**
     * Ejecuta la evaluación de reglas proactivas.
     *
     * Flujo:
     * 1. Obtiene las reglas habilitadas del repositorio
     * 2. Si no hay reglas, termina exitosamente
     * 3. Obtiene el contexto agregado actual del dispositivo
     * 4. Evalúa todas las reglas contra el contexto
     * 5. Entrega las sugerencias al [ProactiveSuggestionManager] para su
     *    presentación (el manager aplica cooldown, quiet hours y expiración)
     *
     * @return [Result.success] si la evaluación se completó,
     *         [Result.retry] si ocurrió un error recuperable
     */
    override suspend fun doWork(): Result {
        return try {
            val rules = ruleRepository.getEnabledRules()
            val context = contextAggregator.getAggregatedContext()

            // Evaluación de reglas (existente)
            val ruleSuggestions = if (rules.isNotEmpty()) {
                proactiveEngine.evaluate(rules, context)
            } else {
                emptyList()
            }

            // Evaluación de patrones predictivos (ADR-028)
            // minFrequency=5 asegura confianza >= 0.74, superando ESTABLISHED_THRESHOLD (0.6)
            val establishedPatterns = patternRepository.getFrequentPatterns(minFrequency = 5)
                .filter { it.isEstablished }
            val predictiveSuggestions = predictiveEngine.generateSuggestions(context, establishedPatterns)

            // Combinar y entregar sugerencias al manager
            val allSuggestions = ruleSuggestions + predictiveSuggestions
            if (allSuggestions.isNotEmpty()) {
                suggestionManager.submitSuggestions(allSuggestions)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating rules", e)
            Result.retry()
        }
    }

    companion object {
        /** Tag para logs del worker. */
        private const val TAG = "ProactiveCheck"

        /** Nombre único del trabajo periódico para WorkManager. */
        const val WORK_NAME = "proactive_check"
    }
}
