package com.screenassistant.core.domain.bridge

import com.screenassistant.core.domain.bridge.model.AccionRegistry
import com.screenassistant.core.domain.bridge.model.CommandEnvelope
import com.screenassistant.core.domain.model.SystemCommand
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián de la biyección wire↔envelope↔SystemCommand (ADR-013, H1/H3):
 * los 22 wires, sin duplicados, con envelope decodificable y mapeo a SystemCommand.
 * Un único test: si alguien toca la tabla sin actualizar el guardián, falla en rojo.
 */
class CorrespondenciaWireTest {

    private val codec = SystemCommandJsonCodec()
    private val json = Json { explicitNulls = true }

    // Triple (wire, envelope de ejemplo, SystemCommand esperado de la biyección).
    private val tabla: List<Triple<String, CommandEnvelope, SystemCommand>> = listOf(
        Triple("llamar_contacto", CommandEnvelope.LlamarContacto(contacto = "Ana"), SystemCommand.Call("Ana")),
        Triple("enviar_sms", CommandEnvelope.EnviarSms("Ana", "Hola"), SystemCommand.SendSms("Ana", "Hola")),
        Triple("poner_alarma", CommandEnvelope.PonerAlarma(7, 30), SystemCommand.SetAlarm(7, 30, null)),
        Triple("cancelar_alarma", CommandEnvelope.CancelarAlarma(7, 30), SystemCommand.CancelAlarm(7, 30)),
        Triple("abrir_app", CommandEnvelope.AbrirApp("whatsapp"), SystemCommand.OpenApp("whatsapp")),
        Triple("buscar_archivo", CommandEnvelope.BuscarArchivo("informe"), SystemCommand.SearchFile("informe")),
        Triple(
            "encolar_mensaje",
            CommandEnvelope.EncolarMensaje("whatsapp", "Ana", "Hola"),
            SystemCommand.QueueMessage("whatsapp", "Ana", "Hola"),
        ),
        Triple("abrir_alarmas", CommandEnvelope.AbrirAlarmas, SystemCommand.OpenAlarms),
        Triple("buscar_google", CommandEnvelope.BuscarGoogle("gatos"), SystemCommand.SearchGoogle("gatos")),
        Triple("abrir_youtube", CommandEnvelope.AbrirYouTube("queen"), SystemCommand.OpenYouTube("queen")),
        Triple("abrir_whatsapp", CommandEnvelope.AbrirWhatsApp, SystemCommand.OpenWhatsApp),
        Triple("reproducir_musica", CommandEnvelope.ReproducirMusica("queen"), SystemCommand.PlayMusic("queen")),
        Triple("poner_volumen", CommandEnvelope.PonerVolumen("subir"), SystemCommand.SetVolume(com.screenassistant.core.domain.model.VolumeAction.UP)),
        Triple("poner_idioma", CommandEnvelope.PonerIdioma("espanol"), SystemCommand.SetLanguage(com.screenassistant.core.domain.model.AssistantLanguage.SPANISH)),
        Triple("poner_temporizador", CommandEnvelope.PonerTemporizador(90), SystemCommand.SetTimer(90)),
        Triple("navegar_a", CommandEnvelope.NavegarA("la oficina"), SystemCommand.Navigate("la oficina")),
        Triple("abrir_ajustes", CommandEnvelope.AbrirAjustes, SystemCommand.OpenSettings),
        Triple("llamar_numero", CommandEnvelope.LlamarNumero("+34600123456"), SystemCommand.CallNumber("+34600123456")),
        Triple("recordar_dato", CommandEnvelope.RecordarDato("me gusta el cafe"), SystemCommand.SaveMemory("me gusta el cafe")),
        Triple("crear_nota", CommandEnvelope.CrearNota("comprar leche"), SystemCommand.CreateNote("comprar leche")),
        Triple("leer_notas", CommandEnvelope.LeerNotas, SystemCommand.ReadNotes),
        Triple("leer_nota", CommandEnvelope.LeerNota("pan"), SystemCommand.ReadNote("pan")),
    )

    @Test
    fun `tabla wire envelope SystemCommand es biyectiva y consistente con el registro`() {
        // Los 22 sin duplicados.
        assertEquals(22, tabla.size)
        val wires = tabla.map { it.first }
        assertEquals(wires.size, wires.toSet().size)
        // M4: guardián de ADICIONES — si se añade un wire al registro sin tocar la
        // tabla, la igualdad contra todosLosWires falla en rojo (antes se pasaba).
        assertEquals(AccionRegistry.todosLosWires, wires)

        for ((wire, envelope, comando) in tabla) {
            // Registro: biyección wire↔subtipo.
            assertEquals("wireDe(${envelope::class.simpleName})", wire, AccionRegistry.wireDe(envelope::class))
            assertEquals("subtipoDe($wire)", envelope::class, AccionRegistry.subtipoDe(wire))

            // El wire emitido por el serializador es el del registro (H1).
            val emitido = json.encodeToString(CommandEnvelope.serializer(), envelope)
            assertTrue("el JSON de $wire debe emitir el discriminador accion", emitido.contains("\"accion\":\"$wire\""))

            // Decode F1 feliz (version:1 inyectada): el wire decodifica al envelope original.
            val conVersion = emitido.replaceFirst("{", "{\"version\":1,")
            val respuesta = codec.decode(conVersion)
            assertTrue("el wire $wire debe decodificar (Success): $conVersion", respuesta is SystemCommandJsonCodec.RespuestaCodec.Success)
            assertEquals(envelope, (respuesta as SystemCommandJsonCodec.RespuestaCodec.Success).envelope)

            // Mapeo: envelope → SystemCommand (biyección).
            assertEquals("mapToSystemCommand($wire)", comando, codec.mapToSystemCommand(envelope))
        }
    }

    @Test
    fun `el codec consulta los limites del registro una unica fuente`() {
        // Rangos declarados en el registro (ADR-013, S7) y reflejados en el codec.
        assertEquals(0..23, AccionRegistry.accionDe("poner_alarma")!!.limites.rangoHora)
        assertEquals(0..59, AccionRegistry.accionDe("poner_alarma")!!.limites.rangoMinuto)
        assertEquals(0..23, AccionRegistry.accionDe("cancelar_alarma")!!.limites.rangoHora)
        assertEquals(0..59, AccionRegistry.accionDe("cancelar_alarma")!!.limites.rangoMinuto)
        assertEquals(1..1440, AccionRegistry.accionDe("poner_temporizador")!!.limites.rangoMinutos)

        // Comportamiento del codec derivado de esos límites (no hardcodeados).
        assertTrue(codec.decode("""{"version":1,"accion":"poner_alarma","hora":23,"minuto":59}""") is SystemCommandJsonCodec.RespuestaCodec.Success)
        assertTrue(codec.decode("""{"version":1,"accion":"poner_alarma","hora":24,"minuto":0}""") is SystemCommandJsonCodec.RespuestaCodec.Error)
        assertTrue(codec.decode("""{"version":1,"accion":"poner_temporizador","minutos":1440}""") is SystemCommandJsonCodec.RespuestaCodec.Success)
        assertTrue(codec.decode("""{"version":1,"accion":"poner_temporizador","minutos":1441}""") is SystemCommandJsonCodec.RespuestaCodec.Error)

        // Teléfono: constantes y longitud máxima del registro == regla del codec.
        assertEquals(3, AccionRegistry.TELEFONO_MIN_DIGITOS)
        assertEquals(15, AccionRegistry.TELEFONO_MAX_DIGITOS)
        assertEquals(16, AccionRegistry.longitudMaxima("llamar_numero", "telefono"))
        assertTrue(codec.decode("""{"version":1,"accion":"llamar_numero","telefono":"+346001234567890"}""") is SystemCommandJsonCodec.RespuestaCodec.Success)
        assertTrue(codec.decode("""{"version":1,"accion":"llamar_numero","telefono":"3460012345678901"}""") is SystemCommandJsonCodec.RespuestaCodec.Error)

        // Base del envelope: id/contexto ≤ 200 == regla del codec.
        assertEquals(200, AccionRegistry.MAX_ID_CONTEXTO_CHARS)
        assertTrue(codec.decode("""{"version":1,"accion":"abrir_alarmas","id":"${"i".repeat(200)}"}""") is SystemCommandJsonCodec.RespuestaCodec.Success)
        assertTrue(codec.decode("""{"version":1,"accion":"abrir_alarmas","id":"${"i".repeat(201)}"}""") is SystemCommandJsonCodec.RespuestaCodec.Error)
    }
}
