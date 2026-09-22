package com.screenassistant.service.system.action

import com.screenassistant.core.domain.model.CalculatorOperator
import com.screenassistant.core.domain.model.MathResult
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.core.domain.usecase.math.MathEvaluator
import com.screenassistant.core.domain.usecase.math.MathFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalculatorAction @Inject constructor() {

    fun calculate(command: SystemCommand.Calculate): String {
        if (command.operator == CalculatorOperator.DIVIDE && command.operand2 == 0.0) {
            return "Error: No se puede dividir por cero."
        }

        val result = when (command.operator) {
            CalculatorOperator.ADD -> command.operand1 + command.operand2
            CalculatorOperator.SUBTRACT -> command.operand1 - command.operand2
            CalculatorOperator.MULTIPLY -> command.operand1 * command.operand2
            CalculatorOperator.DIVIDE -> command.operand1 / command.operand2
        }

        val formatted = if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            String.format("%.2f", result).trimEnd('0').trimEnd('.')
        }
        return "El resultado es $formatted."
    }

    /**
     * MATH (ADR-MATH, P3): evalúa la expresión canónica con [MathEvaluator].
     * Reutiliza el formato de [calculate] y su mensaje de división por cero;
     * el resto de errores hablan `Error: Expresión inválida.` (test §9 #16).
     */
    fun calculateExpression(command: SystemCommand.CalculateExpression): String {
        return when (val r = MathEvaluator.evaluate(command.expression)) {
            is MathResult.Success -> "El resultado es ${MathFormatter.format(r.value)}."
            is MathResult.Error -> when (r.reason) {
                MathResult.DIVISION_POR_CERO -> "Error: No se puede dividir por cero."
                MathResult.NUMERO_FUERA_DE_RANGO -> "Error: Número fuera de rango."
                else -> "Error: Expresión inválida."
            }
        }
    }
}
