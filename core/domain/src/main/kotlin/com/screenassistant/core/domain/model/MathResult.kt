package com.screenassistant.core.domain.model

/**
 * Resultado del evaluador aritmético puro ([MathEvaluator]).
 *
 * Contrato (ADR-MATH §5): `reason` pertenece al conjunto cerrado
 * {"division por cero", "expresion invalida", "numero fuera de rango"}.
 */
sealed interface MathResult {
    data class Success(val value: Double) : MathResult
    data class Error(val reason: String) : MathResult

    companion object {
        const val DIVISION_POR_CERO: String = "division por cero"
        const val EXPRESION_INVALIDA: String = "expresion invalida"
        const val NUMERO_FUERA_DE_RANGO: String = "numero fuera de rango"
    }
}
