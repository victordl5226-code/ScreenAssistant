package com.screenassistant.core.domain.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ADR-B7: clasificación del resultado de una acción para el wire.
 *
 * Contrato de `estadoDe`: el prefijo "Error: " se matchea EXACTO y
 * case-sensitive ("Error: " con E mayúscula, dos puntos y espacio — ADR-009).
 * Cualquier otra variante ("error: ", "Error de ...") es contenido, no fallo.
 */
class ResultadoWireTest {

    @Test
    fun `estadoDe con prefijo Error mayuscula exacto devuelve error`() {
        assertEquals("error", ResultadoWire.estadoDe("Error: No encontré la aplicación."))
    }

    @Test
    fun `estadoDe con mensaje de exito devuelve ok`() {
        assertEquals("ok", ResultadoWire.estadoDe("Éxito: Abriendo WhatsApp."))
    }

    @Test
    fun `estadoDe con texto libre sin prefijo devuelve ok`() {
        assertEquals("ok", ResultadoWire.estadoDe("Alarma configurada para las 7:30."))
    }

    @Test
    fun `estadoDe con error en minusculas devuelve ok por case sensitive`() {
        // "error: ..." en minúsculas NO es el prefijo ADR-009 → contenido, no fallo.
        assertEquals("ok", ResultadoWire.estadoDe("error: algo salió mal"))
    }

    @Test
    fun `estadoDe con Error sin dos puntos ni espacio devuelve ok`() {
        // "Error de conexión" no matchea el prefijo exacto "Error: " → no es fallo.
        assertEquals("ok", ResultadoWire.estadoDe("Error de conexión"))
    }
}
