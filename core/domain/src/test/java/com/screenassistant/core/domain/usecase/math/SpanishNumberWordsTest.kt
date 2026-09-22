package com.screenassistant.core.domain.usecase.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Aceptación P2a (ADR-MATH §6): vocabulario 0-100 + cientos atómicos,
 * compuestos `X y Y`, tolerancia `un/una`, fuera de rango → null.
 */
class SpanishNumberWordsTest {

    @Test
    fun `simples 0 a 29`() {
        assertEquals(0, SpanishNumberWords.resolve("cero"))
        assertEquals(1, SpanishNumberWords.resolve("uno"))
        assertEquals(1, SpanishNumberWords.resolve("un"))
        assertEquals(1, SpanishNumberWords.resolve("una"))
        assertEquals(15, SpanishNumberWords.resolve("quince"))
        assertEquals(29, SpanishNumberWords.resolve("veintinueve"))
    }

    @Test
    fun `tildes y case se toleran`() {
        assertEquals(22, SpanishNumberWords.resolve("Veintidós"))
        assertEquals(3, SpanishNumberWords.resolve("TRES"))
    }

    @Test
    fun `decenas nuevas 60 a 90`() {
        assertEquals(60, SpanishNumberWords.resolve("sesenta"))
        assertEquals(70, SpanishNumberWords.resolve("setenta"))
        assertEquals(80, SpanishNumberWords.resolve("ochenta"))
        assertEquals(90, SpanishNumberWords.resolve("noventa"))
    }

    @Test
    fun `compuestos hasta 99`() {
        assertEquals(99, SpanishNumberWords.resolve("noventa y nueve"))
        assertEquals(35, SpanishNumberWords.resolve("treinta y cinco"))
        assertEquals(25, SpanishNumberWords.resolve("veinte y 5"))
    }

    @Test
    fun `compuesto invalido es null completo`() {
        assertNull(SpanishNumberWords.resolve("veinte y diez"))
        assertNull(SpanishNumberWords.resolve("treinta y veinte"))
    }

    @Test
    fun `cientos atomicos resuelven y compuestos son null`() {
        assertEquals(100, SpanishNumberWords.resolve("cien"))
        assertEquals(100, SpanishNumberWords.resolve("ciento"))
        assertEquals(200, SpanishNumberWords.resolve("doscientos"))
        assertNull(SpanishNumberWords.resolve("ciento uno"))
        assertNull(SpanishNumberWords.resolve("doscientos cincuenta"))
    }

    @Test
    fun `digitos y basura`() {
        assertEquals(7, SpanishNumberWords.resolve("7"))
        assertNull(SpanishNumberWords.resolve("hola"))
        assertNull(SpanishNumberWords.resolve(""))
    }
}
