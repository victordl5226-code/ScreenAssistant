package com.screenassistant.core.domain.usecase.math

import com.screenassistant.core.domain.model.MathResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aceptación P1 (ADR-MATH §9/§12, canónica directa):
 * precedencia, right-assoc de `^`, `sqrt`, unario, `%` binario vs postfijo,
 * bordes (10/0, 0/0, sqrt(-1), inyección, longitud 201, profundidad 11).
 */
class MathEvaluatorTest {

    private fun success(canonical: String): Double {
        val r = MathEvaluator.evaluate(canonical)
        assertTrue("esperaba Success para '$canonical' pero fue $r", r is MathResult.Success)
        return (r as MathResult.Success).value
    }

    private fun errorReason(canonical: String): String {
        val r = MathEvaluator.evaluate(canonical)
        assertTrue("esperaba Error para '$canonical' pero fue $r", r is MathResult.Error)
        return (r as MathResult.Error).reason
    }

    @Test
    fun `precedencia 2+3x4 es 14 y no 20`() {
        assertEquals(14.0, success("2+3*4"), 0.0)
    }

    @Test
    fun `parentesis cambian precedencia`() {
        assertEquals(20.0, success("(2+3)*4"), 0.0)
    }

    @Test
    fun `potencia right-assoc 2^3^2 es 512`() {
        assertEquals(512.0, success("2^3^2"), 0.0)
    }

    @Test
    fun `cuadrado y cubo`() {
        assertEquals(4.0, success("2^2"), 0.0)
        assertEquals(8.0, success("2^3"), 0.0)
    }

    @Test
    fun `raiz cuadrada de 81 es 9`() {
        assertEquals(9.0, success("sqrt(81)"), 0.0)
    }

    @Test
    fun `division por cero es error`() {
        assertEquals(MathResult.DIVISION_POR_CERO, errorReason("10/0"))
        assertEquals(MathResult.DIVISION_POR_CERO, errorReason("0/0"))
        assertEquals(MathResult.DIVISION_POR_CERO, errorReason("10%0"))
    }

    @Test
    fun `raiz de negativo es error de expresion`() {
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("sqrt(-1)"))
    }

    @Test
    fun `modulo binario 10 resto 3 es 1`() {
        assertEquals(1.0, success("10%3"), 0.0)
    }

    @Test
    fun `porcentaje postfijo 50 por ciento es medio`() {
        assertEquals(0.5, success("50/100"), 0.0)
        assertEquals(30.0, success("15/100*200"), 0.0)
    }

    @Test
    fun `decimales con punto`() {
        assertEquals(3.8, success("1.5+2.3"), 1e-9)
        assertEquals(1.0/3.0, success("1/3"), 1e-9)
    }

    @Test
    fun `unario menos`() {
        assertEquals(-5.0, success("-5"), 0.0)
        assertEquals(5.0, success("2--3"), 0.0)
        assertEquals(2.0, success("-3+5"), 0.0)
    }

    @Test
    fun `expresion compleja anidada`() {
        assertEquals(8.0, success("(8+2)*(7-3)/5"), 0.0)
        assertEquals(148.0, success("12^2+sqrt(16)"), 0.0)
    }

    @Test
    fun `inyeccion es error sin ejecutar`() {
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("__import__('os')"))
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("1;2"))
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("; rm -rf"))
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("2+abc"))
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("sqrt 81"))
    }

    @Test
    fun `sintaxis rota es error`() {
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason(""))
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("   "))
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("()"))
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("2+"))
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("(2+3"))
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("2**3"))
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason("1.2.3"))
    }

    @Test
    fun `longitud mayor de 200 es error`() {
        val larga = "1".repeat(201)
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason(larga))
    }

    @Test
    fun `profundidad mayor de 10 es error y 10 pasa`() {
        val profunda11 = "(".repeat(11) + "1" + ")".repeat(11)
        assertEquals(MathResult.EXPRESION_INVALIDA, errorReason(profunda11))
        val profunda10 = "(".repeat(10) + "1" + ")".repeat(10)
        assertEquals(1.0, success(profunda10), 0.0)
    }

    @Test
    fun `overflow es numero fuera de rango`() {
        assertEquals(MathResult.NUMERO_FUERA_DE_RANGO, errorReason("10^1000"))
    }
}
