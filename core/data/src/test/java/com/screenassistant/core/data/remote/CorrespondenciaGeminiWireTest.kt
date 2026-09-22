package com.screenassistant.core.data.remote

import com.screenassistant.core.data.remote.openrouter.OpenRouterToolCatalog
import com.screenassistant.core.domain.bridge.model.AccionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián de paridad del segundo catálogo de acciones (D3, Lote 9):
 * toda función LLM expuesta como tool (OpenRouterToolCatalog) debe tener
 * su wire Tasker registrado en AccionRegistry (core:domain), SALVO exclusión
 * documentada en [OpenRouterToolCatalog.sinWire] (MATH: `calculate`, §7).
 *
 * Paridad UNIDIRECCIONAL y documentada (veto D3 a unificar — capas incompatibles):
 * - Añadir una 10ª función SIN wire ni exclusión → ROJO (este test obliga a
 *   decidir: wire nuevo en el registro o exclusión documentada en el catálogo).
 * - Añadir un wire sin función LLM → VERDE (el LLM no cubre los 22 wires).
 *
 * P1-3: la tabla nombre→wire se consulta en el propio catálogo — este test NO
 * la duplica a mano (si el mapeo se queda corto, el primer test falla).
 * P1-4: save_memory → recordar_dato — decisión declarada AQUÍ y en el KDoc del
 * catálogo: la ejecución runtime va directa a MemoryRepository (no genera
 * SystemCommand); el wire recordar_dato existe y cubre la misma intención.
 * MATH: calculate → SIN wire — cálculo puro, el evaluador local produce el
 * valor (sin puente Tasker); decisión declarada AQUÍ y en el KDoc del catálogo.
 */
class CorrespondenciaGeminiWireTest {

    // 1. Toda función LLM con wire tiene su wire registrado; el mapeo cubre el
    // catálogo COMPLETO salvo la exclusión documentada (MATH: calculate).
    @Test
    fun `toda funcion LLM tiene su wire Tasker registrado en AccionRegistry`() {
        // El mapeo cubre el catálogo salvo sinWire (ni corto ni inventado).
        assertEquals(
            OpenRouterToolCatalog.nombres - OpenRouterToolCatalog.sinWire,
            OpenRouterToolCatalog.wirePorNombre.keys
        )

        for (nombre in OpenRouterToolCatalog.nombres - OpenRouterToolCatalog.sinWire) {
            val wire = OpenRouterToolCatalog.wirePorNombre[nombre]
            assertTrue(
                "la función LLM '$nombre' debe tener wire en el mapeo del catálogo",
                wire != null
            )
            assertTrue(
                "el wire '$wire' (función LLM '$nombre') debe estar registrado en AccionRegistry",
                wire != null && AccionRegistry.esRegistrado(wire)
            )
        }
    }

    // 2. Paridad unidireccional: cobertura parcial por diseño + decisión P1-4 declarada.
    @Test
    fun `la paridad es unidireccional y save_memory se resuelve de forma documentada`() {
        // P1-4: save_memory se mapea al wire recordar_dato (decisión explícita del
        // test y del KDoc del catálogo — no puede cambiar sin tocar este test).
        assertEquals("recordar_dato", OpenRouterToolCatalog.wirePorNombre["save_memory"])
        assertTrue(AccionRegistry.esRegistrado("recordar_dato"))

        // Unidireccional: el registro tiene 22 wires; el LLM solo expone 9 → la
        // cobertura es parcial POR DISEÑO (añadir wire sin función → verde).
        assertTrue(AccionRegistry.todosLosWires.size > OpenRouterToolCatalog.nombres.size)
        assertEquals(9, OpenRouterToolCatalog.nombres.size)

        // MATH: calculate sin wire por ser cálculo puro (decisión explícita del
        // test y del KDoc del catálogo — no puede cambiar sin tocar este test).
        assertEquals(setOf("calculate"), OpenRouterToolCatalog.sinWire)
        assertTrue("calculate" in OpenRouterToolCatalog.nombres)
        assertTrue(
            "calculate no debe tener wire en el mapeo del catálogo",
            "calculate" !in OpenRouterToolCatalog.wirePorNombre.keys
        )
    }
}
