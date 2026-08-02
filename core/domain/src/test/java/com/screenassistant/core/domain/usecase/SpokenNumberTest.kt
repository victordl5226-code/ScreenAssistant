package com.screenassistant.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1: tests directos del vocabulario compartido SpokenNumber
 * (duraciones del temporizador + minutos hablados de horas).
 */
class SpokenNumberTest {

    // ===== resolve(token1, token2) =====

    @Test
    fun `resolve veinte con 0 es null por rango del token2`() {
        assertNull(SpokenNumber.resolve("veinte", "0"))
    }

    @Test
    fun `resolve uno con 5 es null por no ser decena`() {
        // token2 solo se admite si token1 es decena
        assertNull(SpokenNumber.resolve("uno", "5"))
    }

    @Test
    fun `resolve 05 sin token2 es 5`() {
        assertEquals(5, SpokenNumber.resolve("05", null))
    }

    @Test
    fun `resolve veinte con veinte es null por no ser unidad 1-9`() {
        assertNull(SpokenNumber.resolve("veinte", "veinte"))
    }

    @Test
    fun `resolve cuarto sin token2 es 15`() {
        assertEquals(15, SpokenNumber.resolve("cuarto", null))
    }

    @Test
    fun `resolve veinte con 5 es 25`() {
        // ADR-TMP-7: token2 dígito 1-9 con la misma validación que las palabras
        assertEquals(25, SpokenNumber.resolve("veinte", "5"))
    }

    // ===== quantityTokens =====

    @Test
    fun `quantityTokens excluye las fracciones`() {
        val tokens = SpokenNumber.quantityTokens()
        assertFalse(tokens.contains("media"))
        assertFalse(tokens.contains("cuarto"))
    }

    @Test
    fun `quantityTokens ordena por longitud descendente para el alternation`() {
        val tokens = SpokenNumber.quantityTokens()
        assertEquals(tokens.sortedByDescending { it.length }, tokens)
    }

    @Test
    fun `quantityTokens cubre SIMPLE y TENS sin duplicados`() {
        val tokens = SpokenNumber.quantityTokens()
        assertTrue(tokens.containsAll(SpokenNumber.SIMPLE.keys))
        assertTrue(tokens.containsAll(SpokenNumber.TENS.keys))
        assertEquals((SpokenNumber.SIMPLE.keys + SpokenNumber.TENS.keys).distinct().size, tokens.size)
    }

    // ===== unitTokens =====

    @Test
    fun `unitTokens solo contiene unidades 1-9`() {
        val units = SpokenNumber.unitTokens()
        assertFalse(units.contains("cero"))
        assertFalse(units.contains("diez"))
        assertFalse(units.contains("veinte"))
        assertTrue(units.contains("cinco"))
        assertTrue(units.contains("uno"))
        assertEquals(units.sortedByDescending { it.length }, units)
    }
}
