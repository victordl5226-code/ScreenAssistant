package com.screenassistant.service.system.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TaskerRespuestaTexto (P2, ADR-014/T6): parseo puro best-effort del JSON de
 * salida — idDe (eco plano para Tasker) y textoDe (canal humano TTS).
 * JSON inválido → null/null → no habla.
 */
class TaskerRespuestaTextoTest {

    private val respuestaOk =
        """{"version":1,"id":"t-1","estado":"ok","resultado":"Éxito: Volumen subido.","error":null,"mensaje":null}"""
    private val respuestaError =
        """{"version":1,"id":"t-2","estado":"error","resultado":null,"error":"fallo_ejecucion","mensaje":"Error: boom"}"""

    // ===== textoDe =====

    @Test
    fun `estado ok devuelve el texto de resultado`() {
        assertEquals("Éxito: Volumen subido.", TaskerRespuestaTexto.textoDe(respuestaOk))
    }

    @Test
    fun `estado error devuelve el texto de error humano (mensaje)`() {
        assertEquals("Error: boom", TaskerRespuestaTexto.textoDe(respuestaError))
    }

    @Test
    fun `estado ok con resultado null devuelve null`() {
        val json = """{"version":1,"id":"t-1","estado":"ok","resultado":null,"error":null,"mensaje":null}"""
        assertNull(TaskerRespuestaTexto.textoDe(json))
    }

    @Test
    fun `error sin mensaje devuelve el codigo de error como alternativa`() {
        val json = """{"version":1,"estado":"error","resultado":null,"error":"json_invalido","mensaje":null}"""
        assertEquals("json_invalido", TaskerRespuestaTexto.textoDe(json))
    }

    // ===== idDe =====

    @Test
    fun `idDe extrae el id plano del json`() {
        assertEquals("t-1", TaskerRespuestaTexto.idDe(respuestaOk))
        assertEquals("t-2", TaskerRespuestaTexto.idDe(respuestaError))
    }

    @Test
    fun `idDe devuelve null si el id no es string o falta`() {
        assertNull(TaskerRespuestaTexto.idDe("""{"version":1,"id":42,"estado":"ok"}"""))
        assertNull(TaskerRespuestaTexto.idDe("""{"version":1,"estado":"ok","resultado":"x"}"""))
    }

    // ===== Best-effort: JSON inválido → null/null =====

    @Test
    fun `json invalido devuelve null en idDe y textoDe`() {
        assertNull(TaskerRespuestaTexto.idDe("no-json"))
        assertNull(TaskerRespuestaTexto.textoDe("no-json"))
        assertNull(TaskerRespuestaTexto.textoDe(""))
        assertNull(TaskerRespuestaTexto.textoDe("  "))
    }
}
