package com.screenassistant.service.system.action

import com.screenassistant.core.domain.model.SystemCommand
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Aceptación P3 (ADR-MATH §9): `calculateExpression` habla el mismo formato
 * que el binario legacy (7 tests de [CalculatorActionTest] intactos).
 */
class CalculatorActionExpressionTest {

    private val action = CalculatorAction()

    @Test
    fun `precedencia 2+3x4 es 14`() {
        assertEquals(
            "El resultado es 14.",
            action.calculateExpression(SystemCommand.CalculateExpression("2+3*4"))
        )
    }

    @Test
    fun `parentesis dan 20`() {
        assertEquals(
            "El resultado es 20.",
            action.calculateExpression(SystemCommand.CalculateExpression("(2+3)*4"))
        )
    }

    @Test
    fun `potencia right-assoc da 512`() {
        assertEquals(
            "El resultado es 512.",
            action.calculateExpression(SystemCommand.CalculateExpression("2^3^2"))
        )
    }

    @Test
    fun `raiz de 81 es 9`() {
        assertEquals(
            "El resultado es 9.",
            action.calculateExpression(SystemCommand.CalculateExpression("sqrt(81)"))
        )
    }

    @Test
    fun `division por cero reusa el mensaje legacy`() {
        assertEquals(
            "Error: No se puede dividir por cero.",
            action.calculateExpression(SystemCommand.CalculateExpression("10/0"))
        )
    }

    @Test
    fun `expresion invalida habla error`() {
        assertEquals(
            "Error: Expresión inválida.",
            action.calculateExpression(SystemCommand.CalculateExpression("sqrt(-1)"))
        )
        assertEquals(
            "Error: Expresión inválida.",
            action.calculateExpression(SystemCommand.CalculateExpression("__import__"))
        )
    }

    @Test
    fun `decimales se formatean a 2`() {
        assertEquals(
            "El resultado es 3.8.",
            action.calculateExpression(SystemCommand.CalculateExpression("1.5+2.3"))
        )
        assertEquals(
            "El resultado es 0.33.",
            action.calculateExpression(SystemCommand.CalculateExpression("1/3"))
        )
    }
}
