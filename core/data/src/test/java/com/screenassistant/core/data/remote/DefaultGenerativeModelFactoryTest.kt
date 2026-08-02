package com.screenassistant.core.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * La factory no debe tocar el SDK con key vacía; con key válida el
 * constructor del SDK solo construye objetos (sin red, verificado en el
 * bytecode: fullModelName es manipulación de strings y APIController
 * crea el cliente ktor de forma perezosa).
 */
class DefaultGenerativeModelFactoryTest {

    private val factory = DefaultGenerativeModelFactory()

    @Test
    fun `create con key vacia devuelve null sin tocar el SDK`() {
        val model = factory.create("", emptyList(), null)

        assertEquals(null, model)
    }

    @Test
    fun `create con key valida devuelve una instancia de GenerativeModel`() {
        val model = factory.create("test-key", emptyList(), null)

        assertNotNull(model)
    }
}
