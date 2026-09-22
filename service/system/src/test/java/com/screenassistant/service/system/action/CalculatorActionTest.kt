package com.screenassistant.service.system.action

import com.screenassistant.core.domain.model.CalculatorOperator
import com.screenassistant.core.domain.model.SystemCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorActionTest {

    private val action = CalculatorAction()

    @Test
    fun `suma 3 y 4 devuelve 7`() {
        val result = action.calculate(SystemCommand.Calculate(3.0, CalculatorOperator.ADD, 4.0))
        assertEquals("El resultado es 7.", result)
    }

    @Test
    fun `resta 10 menos 3 devuelve 7`() {
        val result = action.calculate(SystemCommand.Calculate(10.0, CalculatorOperator.SUBTRACT, 3.0))
        assertEquals("El resultado es 7.", result)
    }

    @Test
    fun `multiplica 3 por 4 devuelve 12`() {
        val result = action.calculate(SystemCommand.Calculate(3.0, CalculatorOperator.MULTIPLY, 4.0))
        assertEquals("El resultado es 12.", result)
    }

    @Test
    fun `divide 10 entre 2 devuelve 5`() {
        val result = action.calculate(SystemCommand.Calculate(10.0, CalculatorOperator.DIVIDE, 2.0))
        assertEquals("El resultado es 5.", result)
    }

    @Test
    fun `divide por cero devuelve error`() {
        val result = action.calculate(SystemCommand.Calculate(10.0, CalculatorOperator.DIVIDE, 0.0))
        assertEquals("Error: No se puede dividir por cero.", result)
    }

    @Test
    fun `resultado con decimales se formatea a 2`() {
        val result = action.calculate(SystemCommand.Calculate(1.0, CalculatorOperator.DIVIDE, 3.0))
        assertEquals("El resultado es 0.33.", result)
    }

    @Test
    fun `suma con decimales`() {
        val result = action.calculate(SystemCommand.Calculate(1.5, CalculatorOperator.ADD, 2.3))
        assertEquals("El resultado es 3.8.", result)
    }
}
