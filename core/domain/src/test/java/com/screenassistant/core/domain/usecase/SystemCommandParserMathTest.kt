package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.AssistantMode
import com.screenassistant.core.domain.model.CalculatorOperator
import com.screenassistant.core.domain.model.SystemCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Aceptación P4 (ADR-MATH §9 tabla 1-12): rama 18 extendida.
 * Legacy binario simple → `Calculate`; complejo → `CalculateExpression`;
 * inválido → null. No toca `SystemCommandParserTest` (12 tests intactos).
 */
class SystemCommandParserMathTest {

    private lateinit var systemAction: SystemAction
    private lateinit var parser: SystemCommandParser

    @Before
    fun setup() {
        systemAction = mockk()
        every { systemAction.assistantMode } returns MutableStateFlow(AssistantMode.CENTINELA)
        parser = SystemCommandParser(systemAction)
    }

    @Test
    fun `cuanto es dos mas dos emite Calculate legacy`() = runTest {
        coEvery { systemAction.execute(SystemCommand.Calculate(2.0, CalculatorOperator.ADD, 2.0)) } returns
            ActionResult.Success("El resultado es 4.")

        assertEquals("El resultado es 4.", parser.parse("cuanto es dos más dos"))
        coVerify { systemAction.execute(SystemCommand.Calculate(2.0, CalculatorOperator.ADD, 2.0)) }
    }

    @Test
    fun `cuanto es veinte por tres da 60`() = runTest {
        coEvery { systemAction.execute(SystemCommand.Calculate(20.0, CalculatorOperator.MULTIPLY, 3.0)) } returns
            ActionResult.Success("El resultado es 60.")

        assertEquals("El resultado es 60.", parser.parse("cuanto es veinte por tres"))
    }

    @Test
    fun `cuanto es 15 por ciento de 200 da 30`() = runTest {
        coEvery { systemAction.execute(SystemCommand.CalculateExpression("15/100*200")) } returns
            ActionResult.Success("El resultado es 30.")

        assertEquals("El resultado es 30.", parser.parse("cuanto es 15% de 200"))
        coVerify { systemAction.execute(SystemCommand.CalculateExpression("15/100*200")) }
    }

    @Test
    fun `quince por ciento de doscientos en palabras da 30`() = runTest {
        coEvery { systemAction.execute(SystemCommand.CalculateExpression("15/100*200")) } returns
            ActionResult.Success("El resultado es 30.")

        assertEquals(
            "El resultado es 30.",
            parser.parse("cuanto es el quince por ciento de doscientos")
        )
    }

    @Test
    fun `precedencia 2+3x4 da 14 y no 5 parcial`() = runTest {
        coEvery { systemAction.execute(SystemCommand.CalculateExpression("2+3*4")) } returns
            ActionResult.Success("El resultado es 14.")

        assertEquals("El resultado es 14.", parser.parse("cuanto es 2 + 3 * 4"))
        coVerify(exactly = 0) {
            systemAction.execute(SystemCommand.Calculate(2.0, CalculatorOperator.ADD, 3.0))
        }
    }

    @Test
    fun `parentesis dan 20`() = runTest {
        coEvery { systemAction.execute(SystemCommand.CalculateExpression("(2+3)*4")) } returns
            ActionResult.Success("El resultado es 20.")

        assertEquals("El resultado es 20.", parser.parse("cuanto es (2 + 3) * 4"))
    }

    @Test
    fun `2 al cuadrado da 4 y 2^3 da 8`() = runTest {
        coEvery { systemAction.execute(SystemCommand.CalculateExpression("2^2")) } returns
            ActionResult.Success("El resultado es 4.")
        coEvery { systemAction.execute(SystemCommand.CalculateExpression("2^3")) } returns
            ActionResult.Success("El resultado es 8.")

        assertEquals("El resultado es 4.", parser.parse("cuanto es 2 al cuadrado"))
        assertEquals("El resultado es 8.", parser.parse("cuanto es 2^3"))
    }

    @Test
    fun `raiz de 81 da 9`() = runTest {
        coEvery { systemAction.execute(SystemCommand.CalculateExpression("sqrt(81)")) } returns
            ActionResult.Success("El resultado es 9.")

        assertEquals("El resultado es 9.", parser.parse("cuanto es la raiz de 81"))
    }

    @Test
    fun `10 entre 0 habla division por cero`() = runTest {
        coEvery { systemAction.execute(SystemCommand.Calculate(10.0, CalculatorOperator.DIVIDE, 0.0)) } returns
            ActionResult.Success("Error: No se puede dividir por cero.")

        assertEquals(
            "Error: No se puede dividir por cero.",
            parser.parse("cuanto es 10 entre 0")
        )
    }

    @Test
    fun `regresion 5 por 7 sigue legacy`() = runTest {
        coEvery { systemAction.execute(SystemCommand.Calculate(5.0, CalculatorOperator.MULTIPLY, 7.0)) } returns
            ActionResult.Success("El resultado es 35.")

        assertEquals("El resultado es 35.", parser.parse("cuanto es 5 por 7"))
        coVerify { systemAction.execute(SystemCommand.Calculate(5.0, CalculatorOperator.MULTIPLY, 7.0)) }
    }

    @Test
    fun `coma decimal 1,5 mas 2,3 da 3 punto 8`() = runTest {
        coEvery { systemAction.execute(SystemCommand.Calculate(1.5, CalculatorOperator.ADD, 2.3)) } returns
            ActionResult.Success("El resultado es 3.8.")

        assertEquals("El resultado es 3.8.", parser.parse("cuanto es 1,5 mas 2,3"))
        coVerify { systemAction.execute(SystemCommand.Calculate(1.5, CalculatorOperator.ADD, 2.3)) }
    }

    @Test
    fun `ciento uno es null y no 1001`() = runTest {
        assertNull(parser.parse("cuanto es ciento uno"))
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `inyeccion es null`() = runTest {
        assertNull(parser.parse("cuanto es __import__('os')"))
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `cuanto es mas sigue null`() = runTest {
        assertNull(parser.parse("cuanto es mas"))
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `numero suelto es null sin eco legacy`() = runTest {
        assertNull(parser.parse("cuanto es 5"))
        assertNull(parser.parse("cuanto es noventa y nueve"))
        coVerify(exactly = 0) { systemAction.execute(any()) }
    }

    @Test
    fun `raiz de negativo habla error de expresion`() = runTest {
        coEvery { systemAction.execute(SystemCommand.CalculateExpression("sqrt((0-1))")) } returns
            ActionResult.Success("Error: Expresión inválida.")

        assertEquals(
            "Error: Expresión inválida.",
            parser.parse("cuanto es raiz de (0 menos 1)")
        )
    }
}
