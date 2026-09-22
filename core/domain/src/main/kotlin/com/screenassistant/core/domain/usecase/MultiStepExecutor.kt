package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.CommandSequence
import com.screenassistant.core.domain.model.MultiStepResult
import com.screenassistant.core.domain.model.StepFeedback
import kotlinx.coroutines.flow.StateFlow

/**
 * Ejecutor de secuencias multi-paso.
 * Orquesta la ejecución secuencial de pasos con feedback por paso y soporte de cancelación.
 */
interface MultiStepExecutor {
    /**
     * Ejecuta la secuencia completa de pasos.
     * Emite feedback por cada paso via stepFeedback.
     *
     * @param sequence Secuencia de comandos a ejecutar
     * @return Resultado agregado de la ejecución
     */
    suspend fun execute(sequence: CommandSequence): MultiStepResult

    /** Cancela la ejecución en curso */
    fun cancel()

    /** Feedback en tiempo real por paso (StateFlow para UI reactiva) */
    val stepFeedback: StateFlow<StepFeedback?>
}