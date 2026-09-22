package com.screenassistant.core.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameValidatorTest {

    // --- Valid cases ---

    @Test
    fun `validate nombre valido retorna isValid true`() {
        val result = NameValidator.validate("J.A.R.V.I.S.")
        assertTrue(result.isValid)
        assertNull(result.error)
    }

    @Test
    fun `validate nombre solo letras es valido`() {
        val result = NameValidator.validate("Friday")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate nombre con numeros es valido`() {
        val result = NameValidator.validate("Bot2000")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate nombre con guiones es valido`() {
        val result = NameValidator.validate("My-Bot")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate nombre con espacios es valido`() {
        val result = NameValidator.validate("Mi Asistente")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate nombre con puntos es valido`() {
        val result = NameValidator.validate("A.I.")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate nombre de un solo caracter es valido`() {
        val result = NameValidator.validate("A")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate nombre en la longitud maxima exacta es valido`() {
        val name = "A".repeat(NameValidator.MAX_LENGTH)
        val result = NameValidator.validate(name)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate nombre con caracteres unicode es valido`() {
        val result = NameValidator.validate("Asistente Caf\u00e9")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate nombre con tildes es valido`() {
        val result = NameValidator.validate("Jose Maria")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate nombre con sena es valido`() {
        val result = NameValidator.validate("Espana")
        assertTrue(result.isValid)
    }

    // --- Trim ---

    @Test
    fun `validate nombre con espacios al inicio hace trim`() {
        val result = NameValidator.validate("  J.A.R.V.I.S.")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate nombre con espacios al final hace trim`() {
        val result = NameValidator.validate("J.A.R.V.I.S.  ")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate nombre solo espacios se trimea a vacio y retorna error`() {
        val result = NameValidator.validate("   ")
        assertFalse(result.isValid)
        assertEquals("El nombre no puede estar vac\u00edo", result.error)
    }

    // --- Empty ---

    @Test
    fun `validate nombre vacio retorna error`() {
        val result = NameValidator.validate("")
        assertFalse(result.isValid)
        assertEquals("El nombre no puede estar vac\u00edo", result.error)
    }

    // --- Max length ---

    @Test
    fun `validate nombre que excede longitud maxima retorna error`() {
        val name = "A".repeat(NameValidator.MAX_LENGTH + 1)
        val result = NameValidator.validate(name)
        assertFalse(result.isValid)
        assertEquals("M\u00e1ximo ${NameValidator.MAX_LENGTH} caracteres", result.error)
    }

    @Test
    fun `validate nombre largo pero dentro del limite es valido`() {
        val name = "A".repeat(NameValidator.MAX_LENGTH - 1)
        val result = NameValidator.validate(name)
        assertTrue(result.isValid)
    }

    // --- Invalid characters ---

    @Test
    fun `validate nombre con signos de exclamacion es invalido`() {
        val result = NameValidator.validate("Hola!")
        assertFalse(result.isValid)
        assertEquals("Solo letras, n\u00fameros, puntos y guiones", result.error)
    }

    @Test
    fun `validate nombre con arroba es invalido`() {
        val result = NameValidator.validate("bot@email")
        assertFalse(result.isValid)
    }

    @Test
    fun `validate nombre con hashtag es invalido`() {
        val result = NameValidator.validate("bot#1")
        assertFalse(result.isValid)
    }

    @Test
    fun `validate nombre con parentesis es invalido`() {
        val result = NameValidator.validate("bot()")
        assertFalse(result.isValid)
    }

    @Test
    fun `validate nombre con guion bajo es invalido`() {
        val result = NameValidator.validate("my_bot")
        assertFalse(result.isValid)
    }

    @Test
    fun `validate nombre con slash es invalido`() {
        val result = NameValidator.validate("bot/test")
        assertFalse(result.isValid)
    }

    // --- Constants ---

    @Test
    fun `MAX_LENGTH es 20`() {
        assertEquals(20, NameValidator.MAX_LENGTH)
    }

    @Test
    fun `MIN_LENGTH es 1`() {
        assertEquals(1, NameValidator.MIN_LENGTH)
    }
}
