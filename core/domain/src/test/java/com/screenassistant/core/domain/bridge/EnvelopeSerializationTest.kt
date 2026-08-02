package com.screenassistant.core.domain.bridge

import com.screenassistant.core.domain.bridge.model.CommandEnvelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serialización de los 22 subtipos (ADR-013, H4): roundtrip encode→decode→equals,
 * discriminador "accion" presente, opcionales (id/contexto/version) omitidos al
 * emitir con su default, y configuración explicitNulls=true del codec.
 */
class EnvelopeSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = true
    }

    private fun roundtrip(envelope: CommandEnvelope): CommandEnvelope =
        json.decodeFromString(CommandEnvelope.serializer(), json.encodeToString(CommandEnvelope.serializer(), envelope))

    private fun emitido(envelope: CommandEnvelope): String =
        json.encodeToString(CommandEnvelope.serializer(), envelope)

    private fun verificarEnvelope(
        envelope: CommandEnvelope,
        wire: String,
        conId: Boolean = false,
    ) {
        // Roundtrip: encode → decode → equals por subclase.
        assertEquals(envelope, roundtrip(envelope))
        val s = emitido(envelope)
        // Discriminador "accion" presente con el wire exacto.
        assertTrue("debe emitir el discriminador accion para $wire: $s", s.contains("\"accion\":\"$wire\""))
        // Opcionales con default (version/id/contexto) omitidos al emitir.
        assertFalse("version default no debe emitirse: $s", s.contains("\"version\""))
        assertFalse("id null no debe emitirse: $s", s.contains("\"id\""))
        assertFalse("contexto null no debe emitirse: $s", s.contains("\"contexto\""))
        if (conId) {
            val conIdYContexto = envelope.copyIdContexto()
            val s2 = emitido(conIdYContexto)
            assertTrue("id debe emitirse si viene fijado: $s2", s2.contains("\"id\":\"t-42\""))
            assertTrue("contexto debe emitirse si viene fijado: $s2", s2.contains("\"contexto\":\"overlay\""))
            assertEquals(conIdYContexto, roundtrip(conIdYContexto))
        }
    }

    private fun CommandEnvelope.copyIdContexto(): CommandEnvelope = when (this) {
        is CommandEnvelope.LlamarContacto -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.EnviarSms -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.PonerAlarma -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.CancelarAlarma -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.AbrirApp -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.BuscarArchivo -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.EncolarMensaje -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.AbrirAlarmas -> CommandEnvelope.AbrirAlarmas
        is CommandEnvelope.BuscarGoogle -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.AbrirYouTube -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.AbrirWhatsApp -> CommandEnvelope.AbrirWhatsApp
        is CommandEnvelope.ReproducirMusica -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.PonerVolumen -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.PonerIdioma -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.PonerTemporizador -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.NavegarA -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.AbrirAjustes -> CommandEnvelope.AbrirAjustes
        is CommandEnvelope.LlamarNumero -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.RecordarDato -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.CrearNota -> copy(id = "t-42", contexto = "overlay")
        is CommandEnvelope.LeerNotas -> CommandEnvelope.LeerNotas
        is CommandEnvelope.LeerNota -> copy(id = "t-42", contexto = "overlay")
    }

    @Test fun `roundtrip LlamarContacto con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.LlamarContacto(contacto = "Ana"), "llamar_contacto", conId = true)

    @Test fun `roundtrip EnviarSms con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.EnviarSms("Ana", "Hola"), "enviar_sms", conId = true)

    @Test fun `roundtrip PonerAlarma con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.PonerAlarma(7, 30), "poner_alarma", conId = true)

    @Test fun `roundtrip CancelarAlarma con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.CancelarAlarma(7, 30), "cancelar_alarma", conId = true)

    @Test fun `roundtrip AbrirApp con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.AbrirApp("whatsapp"), "abrir_app", conId = true)

    @Test fun `roundtrip BuscarArchivo con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.BuscarArchivo("informe"), "buscar_archivo", conId = true)

    @Test fun `roundtrip EncolarMensaje con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.EncolarMensaje("whatsapp", "Ana", "Hola"), "encolar_mensaje", conId = true)

    @Test fun `roundtrip AbrirAlarmas con discriminador`() =
        verificarEnvelope(CommandEnvelope.AbrirAlarmas, "abrir_alarmas")

    @Test fun `roundtrip BuscarGoogle con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.BuscarGoogle("gatos"), "buscar_google", conId = true)

    @Test fun `roundtrip AbrirYouTube con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.AbrirYouTube("queen"), "abrir_youtube", conId = true)

    @Test fun `roundtrip AbrirWhatsApp con discriminador`() =
        verificarEnvelope(CommandEnvelope.AbrirWhatsApp, "abrir_whatsapp")

    @Test fun `roundtrip ReproducirMusica con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.ReproducirMusica("queen"), "reproducir_musica", conId = true)

    @Test fun `roundtrip PonerVolumen con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.PonerVolumen("subir"), "poner_volumen", conId = true)

    @Test fun `roundtrip PonerIdioma con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.PonerIdioma("espanol"), "poner_idioma", conId = true)

    @Test fun `roundtrip PonerTemporizador con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.PonerTemporizador(90), "poner_temporizador", conId = true)

    @Test fun `roundtrip NavegarA con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.NavegarA("la oficina"), "navegar_a", conId = true)

    @Test fun `roundtrip AbrirAjustes con discriminador`() =
        verificarEnvelope(CommandEnvelope.AbrirAjustes, "abrir_ajustes")

    @Test fun `roundtrip LlamarNumero con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.LlamarNumero("+34600123456"), "llamar_numero", conId = true)

    @Test fun `roundtrip RecordarDato con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.RecordarDato("me gusta el cafe"), "recordar_dato", conId = true)

    @Test fun `roundtrip CrearNota con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.CrearNota("comprar leche"), "crear_nota", conId = true)

    @Test fun `roundtrip LeerNotas con discriminador`() =
        verificarEnvelope(CommandEnvelope.LeerNotas, "leer_notas")

    @Test fun `roundtrip LeerNota con discriminador y opcionales omitidos`() =
        verificarEnvelope(CommandEnvelope.LeerNota("pan"), "leer_nota", conId = true)

    @Test fun `decode acepta id explicito null con explicitNulls`() {
        val envelope = json.decodeFromString(
            CommandEnvelope.serializer(),
            """{"accion":"llamar_contacto","contacto":"Ana","id":null}"""
        )
        assertEquals(CommandEnvelope.LlamarContacto(contacto = "Ana"), envelope)
    }
}
