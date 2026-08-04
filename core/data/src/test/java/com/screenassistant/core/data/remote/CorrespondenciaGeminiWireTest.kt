package com.screenassistant.core.data.remote

import com.screenassistant.core.domain.bridge.model.AccionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián de paridad del segundo catálogo de acciones (D3, Lote 9): toda
 * función Gemini expuesta como tool (GeminiFunctionCatalog) debe tener su wire
 * Tasker registrado en AccionRegistry (core:domain).
 *
 * Paridad UNIDIRECCIONAL y documentada (veto D3 a unificar — capas incompatibles:
 * el SDK de Gemini vive en core:data; AccionRegistry es core:domain JVM puro):
 * - Añadir una 9ª función Gemini SIN wire → ROJO (este test obliga a decidir:
 *   wire nuevo en el registro o exclusión documentada en el catálogo).
 * - Añadir un wire sin función Gemini → VERDE (el LLM no cubre los 22 wires).
 *
 * P1-3: la tabla nombre→wire se consulta en el propio catálogo — este test NO
 * la duplica a mano (si el mapeo se queda corto, el primer test falla).
 * P1-4: save_memory → recordar_dato — decisión declarada AQUÍ y en el KDoc del
 * catálogo: la ejecución runtime va directa a MemoryRepository (no genera
 * SystemCommand); el wire recordar_dato existe y cubre la misma intención.
 */
class CorrespondenciaGeminiWireTest {

    // 1. Toda función LLM tiene wire registrado; el mapeo cubre el catálogo COMPLETO.
    @Test
    fun `toda funcion Gemini tiene su wire Tasker registrado en AccionRegistry`() {
        // El mapeo no puede quedarse corto ni inventar funciones.
        assertEquals(GeminiFunctionCatalog.nombres, GeminiFunctionCatalog.wirePorNombre.keys)

        for (nombre in GeminiFunctionCatalog.nombres) {
            val wire = GeminiFunctionCatalog.wirePorNombre[nombre]
            assertTrue(
                "la función Gemini '$nombre' debe tener wire en el mapeo del catálogo",
                wire != null
            )
            assertTrue(
                "el wire '$wire' (función Gemini '$nombre') debe estar registrado en AccionRegistry",
                wire != null && AccionRegistry.esRegistrado(wire)
            )
        }
    }

    // 2. Paridad unidireccional: cobertura parcial por diseño + decisión P1-4 declarada.
    @Test
    fun `la paridad es unidireccional y save_memory se resuelve de forma documentada`() {
        // P1-4: save_memory se mapea al wire recordar_dato (decisión explícita del
        // test y del KDoc del catálogo — no puede cambiar sin tocar este test).
        assertEquals("recordar_dato", GeminiFunctionCatalog.wirePorNombre["save_memory"])
        assertTrue(AccionRegistry.esRegistrado("recordar_dato"))

        // Unidireccional: el registro tiene 22 wires; Gemini solo expone 8 → la
        // cobertura es parcial POR DISEÑO (añadir wire sin función → verde).
        assertTrue(AccionRegistry.todosLosWires.size > GeminiFunctionCatalog.nombres.size)
        assertEquals(8, GeminiFunctionCatalog.nombres.size)
    }
}
