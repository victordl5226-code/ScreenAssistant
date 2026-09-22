package com.screenassistant.core.domain.model

/**
 * Feedback por paso emitido durante la ejecución multi-paso.
 * Se usa para actualizar la UI en tiempo real (ejecutando paso X/Y: descripción).
 */
data class StepFeedback(
    /** Índice del paso actual (1-based para mostrar al usuario) */
    val stepIndex: Int,
    /** Total de pasos en la secuencia */
    val totalSteps: Int,
    /** Estado del paso actual */
    val status: StepStatus,
    /** Texto descriptivo del paso */
    val description: String,
    /** Paso actual (opcional) */
    val step: CommandStep? = null
)

/** Estado de ejecución de un paso */
enum class StepStatus {
    STARTING,       // "Iniciando paso 1/3..."
    EXECUTING,      // "Ejecutando paso 2/3: llama a Ana"
    COMPLETED,      // "Paso 2/3 completado"
    FAILED,         // "Paso 2/3 falló: ..."
    CANCELLED       // "Secuencia cancelada"
}