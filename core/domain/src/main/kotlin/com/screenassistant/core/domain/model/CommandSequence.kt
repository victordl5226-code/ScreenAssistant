package com.screenassistant.core.domain.model

/**
 * Tipo de conector que une los pasos en una secuencia.
 * El orden define la precedencia en el parsing (PRIMERO_LUEGO > Y_DESPUES > Y_LUEGO > COMA_LUEGO > PUNTOCOMA_LUEGO).
 */
enum class ConnectorType {
    PRIMERO_LUEGO,      // "primero ... luego ..."
    Y_DESPUES,          // "... y después ..." / "... y luego ..."
    Y_LUEGO,            // "... y luego ..."
    COMA_LUEGO,         // "..., luego ..."
    PUNTOCOMA_LUEGO     // "...; luego ..."
}

/**
 * Secuencia ordenada de pasos ejecutables con metadatos.
 */
data class CommandSequence(
    val steps: List<CommandStep>,
    val originalText: String,
    val connectorType: ConnectorType
) {
    /** Indica si es una secuencia multi-paso (más de 1 paso) */
    val isMultiStep: Boolean
        get() = steps.size > 1
}