package com.screenassistant.core.data.remote

import com.screenassistant.core.data.remote.openrouter.OpenRouterToolCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aceptación P5 (ADR-MATH §7/§9): 9ª tool `calculate` con `expression`
 * requerido; lógica de la rama cubierta por `MathEvaluatorTest` +
 * `MathExpressionNormalizerTest` (la rama es mapeo fino sin cómputo propio).
 */
class OpenRouterToolCatalogTest {

    @Test
    fun `catalogo expone 9 tools`() {
        assertEquals(9, OpenRouterToolCatalog.tools.size)
        assertEquals(9, OpenRouterToolCatalog.nombres.size)
    }

    @Test
    fun `calculate exige expression requerido`() {
        val calculate = OpenRouterToolCatalog.tools.first { it.function.name == "calculate" }
        assertEquals(listOf("expression"), calculate.function.parameters.required)
        assertTrue("expression" in calculate.function.parameters.properties.keys)
        assertEquals(
            "string",
            calculate.function.parameters.properties.getValue("expression").type
        )
    }

    @Test
    fun `calculate es la unica funcion sin wire`() {
        assertEquals(setOf("calculate"), OpenRouterToolCatalog.sinWire)
        assertTrue("calculate" in OpenRouterToolCatalog.nombres)
    }

    @Test
    fun `las 8 tools legacy siguen intactas`() {
        val legacy = setOf(
            "open_alarms", "set_alarm", "search_google", "open_youtube",
            "open_whatsapp", "play_music", "save_memory", "open_app"
        )
        assertTrue(legacy.all { it in OpenRouterToolCatalog.nombres })
        assertEquals(legacy, OpenRouterToolCatalog.wirePorNombre.keys)
    }
}
