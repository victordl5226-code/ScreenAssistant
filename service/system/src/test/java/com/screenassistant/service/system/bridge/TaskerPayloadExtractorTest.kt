package com.screenassistant.service.system.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TaskerPayloadExtractor — 100% JVM (QA #3, ADR-014/T3): tabla de precedencia
 * exhaustiva message > cmd, silencio deliberado (H3), convención "silencioso"
 * sin falsos positivos, passthrough sin límites (H5) e idDe best-effort (H6).
 * "Sin android.*" es verificación de CODE REVIEW (H8), no test.
 */
class TaskerPayloadExtractorTest {

    private val wire = """{"version":1,"id":"t-1","accion":"poner_volumen","valor":"subir"}"""

    // ===== Precedencia message (AutoRemote) > cmd (Tasker), D3 =====

    @Test
    fun `message gana a cmd cuando ambos estan presentes`() {
        val payload = TaskerPayloadExtractor.extraer(message = wire, cmd = "cmd-viejo")
        assertEquals(wire, payload.texto)
        assertFalse(payload.silencioso)
    }

    @Test
    fun `solo message se usa`() {
        val payload = TaskerPayloadExtractor.extraer(message = wire, cmd = null)
        assertEquals(wire, payload.texto)
        assertFalse(payload.silencioso)
    }

    @Test
    fun `solo cmd se usa como fallback`() {
        val payload = TaskerPayloadExtractor.extraer(message = null, cmd = wire)
        assertEquals(wire, payload.texto)
        assertFalse(payload.silencioso)
    }

    @Test
    fun `message vacio gana a cmd valido`() {
        // Un message vacío es un COMANDO vacío → el puente responde json_invalido
        // estructurado (F0), mejor que un silencio (D3).
        val payload = TaskerPayloadExtractor.extraer(message = "", cmd = wire)
        assertEquals("", payload.texto)
    }

    @Test
    fun `message en blanco gana a cmd valido`() {
        val payload = TaskerPayloadExtractor.extraer(message = "   ", cmd = wire)
        assertEquals("   ", payload.texto)
        assertFalse(payload.silencioso)
    }

    // ===== Silencio deliberado (H3): sin extras no es un comando =====

    @Test
    fun `ambos extras null producen silencio`() {
        val payload = TaskerPayloadExtractor.extraer(message = null, cmd = null)
        assertNull(payload.texto)
        assertFalse(payload.silencioso)
    }

    // ===== Convención "contexto":"silencioso" (D3) =====

    @Test
    fun `contexto silencioso en cmd marca silencioso true`() {
        val wireSilencioso =
            """{"version":1,"accion":"poner_volumen","valor":"subir","contexto":"silencioso"}"""
        val payload = TaskerPayloadExtractor.extraer(message = null, cmd = wireSilencioso)
        assertTrue(payload.silencioso)
        // El texto viaja ÍNTEGRO (la supresión de TTS es del emitter, no del extractor).
        assertEquals(wireSilencioso, payload.texto)
    }

    @Test
    fun `contexto silencioso en message tambien se detecta`() {
        val wireSilencioso =
            """{"version":1,"accion":"crear_nota","texto":"x","contexto":"silencioso"}"""
        val payload = TaskerPayloadExtractor.extraer(message = wireSilencioso, cmd = null)
        assertTrue(payload.silencioso)
    }

    @Test
    fun `contexto distinto de silencioso no marca silencioso`() {
        val payload = TaskerPayloadExtractor.extraer(
            message = null,
            cmd = """{"version":1,"accion":"poner_volumen","valor":"subir","contexto":"normal"}""",
        )
        assertFalse(payload.silencioso)
    }

    @Test
    fun `json invalido con silencioso en el texto no da falso positivo`() {
        // Subcadena "silencioso" en texto/valor NO debe activar la convención.
        val payload = TaskerPayloadExtractor.extraer(
            message = null,
            cmd = """{"version":1,"accion":"crear_nota","texto":"el contexto silencioso es importante"}""",
        )
        assertFalse(payload.silencioso)
    }

    @Test
    fun `contexto no string no da falso positivo`() {
        val payload = TaskerPayloadExtractor.extraer(
            message = null,
            cmd = """{"version":1,"accion":"crear_nota","texto":"x","contexto":{"valor":"silencioso"}}""",
        )
        assertFalse(payload.silencioso)
    }

    @Test
    fun `contexto con mayusculas no da falso positivo`() {
        val payload = TaskerPayloadExtractor.extraer(
            message = null,
            cmd = """{"version":1,"accion":"crear_nota","texto":"x","contexto":"Silencioso"}""",
        )
        assertFalse(payload.silencioso)
    }

    @Test
    fun `wire roto no lanza y no marca silencioso`() {
        val payload = TaskerPayloadExtractor.extraer(message = null, cmd = "{roto")
        assertEquals("{roto", payload.texto) // el transporte NO valida ni trunca
        assertFalse(payload.silencioso)
    }

    @Test
    fun `blank cmd pasa como texto sin marca silencioso`() {
        val payload = TaskerPayloadExtractor.extraer(message = null, cmd = "  ")
        assertEquals("  ", payload.texto)
        assertFalse(payload.silencioso)
    }

    // ===== Passthrough sin límites (H5): el transporte NO trunca =====

    @Test
    fun `wire de 9000 chars pasa integro al handler sin truncar`() {
        val largo = "a".repeat(9000)
        val wireLargo = """{"version":1,"accion":"buscar_google","busqueda":"$largo"}"""
        val payload = TaskerPayloadExtractor.extraer(message = null, cmd = wireLargo)
        assertEquals(wireLargo, payload.texto)
        assertEquals(wireLargo.length, payload.texto!!.length)
        // El límite 8192 lo decide el puente (ADR-013 S6/S7), no el transporte.
        assertTrue(wireLargo.length > 8192)
    }

    // ===== idDe best-effort (H6) =====

    @Test
    fun `idDe extrae el id string del wire`() {
        assertEquals("t-1", TaskerPayloadExtractor.idDe(wire))
    }

    @Test
    fun `idDe devuelve null si el id no es string`() {
        assertNull(TaskerPayloadExtractor.idDe("""{"version":1,"id":42,"accion":"x"}"""))
    }

    @Test
    fun `idDe devuelve null si el id esta ausente`() {
        assertNull(TaskerPayloadExtractor.idDe("""{"version":1,"accion":"x"}"""))
    }

    @Test
    fun `idDe devuelve null si el wire no es json`() {
        assertNull(TaskerPayloadExtractor.idDe("no-json"))
        assertNull(TaskerPayloadExtractor.idDe(""))
        assertNull(TaskerPayloadExtractor.idDe("  "))
    }
}
