package com.screenassistant.core.domain.usecase.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Aceptación P2b (ADR-MATH §6/§9 tabla 1-4 y 12): español libre → canónica.
 * `toCanonical` recibe el SEGMENTO post-trigger (segundo marcó el trigger).
 */
class MathExpressionNormalizerTest {

    private fun canon(segmentoOriginal: String): String? =
        MathExpressionNormalizer.toCanonical(segmentoOriginal, segmentoOriginal)

    @Test
    fun `dos mas dos es 2+2`() {
        assertEquals("2+2", canon("dos más dos"))
        assertEquals("2+2", canon("dos mas dos"))
    }

    @Test
    fun `veinte por tres es 20x3`() {
        assertEquals("20*3", canon("veinte por tres"))
        assertEquals("5*7", canon("5 por 7"))
    }

    @Test
    fun `porcentaje de es X entre 100 por Y`() {
        assertEquals("15/100*200", canon("15% de 200"))
        assertEquals("15/100*200", canon("el quince por ciento de doscientos"))
        assertEquals("50/100", canon("50%"))
    }

    @Test
    fun `precedencia se preserva en simbolos`() {
        assertEquals("2+3*4", canon("2 + 3 * 4"))
        assertEquals("(2+3)*4", canon("(2 + 3) * 4"))
    }

    @Test
    fun `potencias verbales`() {
        assertEquals("2^2", canon("2 al cuadrado"))
        assertEquals("3^3", canon("3 al cubo"))
        assertEquals("2^3", canon("2^3"))
        assertEquals("2^3", canon("2 elevado a 3"))
    }

    @Test
    fun `raiz verbal es sqrt`() {
        assertEquals("sqrt(81)", canon("raíz de 81"))
        assertEquals("sqrt(81)", canon("raiz cuadrada de 81"))
        assertEquals("sqrt(81)", canon("la raiz de 81"))
    }

    @Test
    fun `entre y dividido por son division`() {
        assertEquals("10/2", canon("10 entre 2"))
        assertEquals("10/2", canon("10 dividido por 2"))
        assertEquals("10/0", canon("10 entre 0"))
    }

    @Test
    fun `coma decimal es punto y miles se limpian`() {
        assertEquals("1.5+2.3", canon("1,5 mas 2,3"))
        assertEquals("1000.5+1", canon("1.000,5 mas 1"))
        assertEquals("1000+1", canon("1,000 mas 1"))
    }

    @Test
    fun `parentesis verbales`() {
        assertEquals(
            "(2+3)*4",
            canon("abre paréntesis 2 más 3 cierra paréntesis por 4")
        )
    }

    @Test
    fun `noventa y nueve resuelve 99 pero suelto es null sin eco`() {
        // El 99 lo cubre SpanishNumberWordsTest; aquí la canónica suelta
        // (sin operador) es fall-through, igual que el legacy `cuanto es 5`.
        assertNull(canon("noventa y nueve"))
        assertNull(canon("99"))
        assertNull(canon("5"))
        assertNull(canon("(5)"))
    }

    @Test
    fun `ciento uno es null y no 1001`() {
        assertNull(canon("ciento uno"))
    }

    @Test
    fun `cortesia por favor se ignora`() {
        assertEquals("2+2", canon("2 mas 2 por favor"))
    }

    @Test
    fun `y solo suma entre operandos`() {
        assertEquals("3+4", canon("3 y 4"))
        assertNull(canon("y qué hora es"))
    }

    @Test
    fun `no matematica es null`() {
        assertNull(canon("mas"))
        assertNull(canon("hola mundo"))
        assertNull(canon(""))
        assertNull(canon("   "))
    }

    @Test
    fun `incompleta es null para fall-through`() {
        assertNull(canon("2+"))
        assertNull(canon("(2+3"))
        assertNull(canon("2 mas"))
    }

    @Test
    fun `inyeccion es null`() {
        assertNull(canon("__import__('os')"))
        assertNull(canon("1;2"))
    }

    @Test
    fun `longitud mayor de 200 es null`() {
        assertNull(canon("1".repeat(201)))
    }

    @Test
    fun `profundidad mayor de 10 es null`() {
        assertNull(canon("(".repeat(11) + "1" + ")".repeat(11)))
    }

    @Test
    fun `menos unario inicial`() {
        assertEquals("-5", canon("menos cinco"))
        assertEquals("5-3", canon("5 menos 3"))
    }
}
