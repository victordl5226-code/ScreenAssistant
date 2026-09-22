package com.screenassistant.core.domain.model

/**
 * Resultado de la ejecución de un paso individual.
 */
data class StepResult(
    val step: CommandStep,
    val result: String,  // "Éxito: ..." | "Error: ..."
    val executedAt: Long = System.currentTimeMillis()
)

/**
 * Resultado agregado de la ejecución multi-paso.
 */
sealed class MultiStepResult {
    /** Ejecución exitosa de todos los pasos */
    data class Success(val stepResults: List<StepResult>) : MultiStepResult()

    /** Secuencia cancelada por el usuario (pasos completados antes de cancelar) */
    data class Cancelled(val completedSteps: List<StepResult>) : MultiStepResult()

    /** Error en un paso (índice del paso fallido, razón, pasos completados) */
    data class Error(
        val failedStepIndex: Int,
        val reason: String,
        val completedSteps: List<StepResult>
    ) : MultiStepResult()
}