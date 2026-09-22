package com.screenassistant.core.domain.usecase.math

/**
 * Formato único de resultados matemáticos hablados (ADR-MATH §5):
 * entero sin decimales; si no, `%.2f` recortado (`3.80`→`3.8`, `0.33`).
 * Fuente única para `CalculatorAction` (Nivel 1) y la rama `calculate` del
 * canal LLM (Nivel 2) — mismo texto en ambos niveles.
 */
object MathFormatter {

    fun format(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.2f", value).trimEnd('0').trimEnd('.')
        }
}
