package com.screenassistant.core.domain.bridge

import com.screenassistant.core.domain.bridge.model.AccionRegistry
import com.screenassistant.core.domain.bridge.model.CommandEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián del registro de wires (ADR-013, H1): un test por wire verificando
 * que existe, es único, cumple el patrón ^[a-z_]+$ y que la biyección
 * wire↔subtipo es exacta y case-sensitive.
 */
class AccionRegistryTest {

    private val patronWire = Regex("^[a-z_]+$")

    private fun verificar(wire: String, tipo: Class<out CommandEnvelope>) {
        // Existe en el registro.
        assertTrue("wire '$wire' debe estar registrado", AccionRegistry.esRegistrado(wire))
        // Único: el wire no se repite.
        assertEquals("wire '$wire' debe ser único", 1, AccionRegistry.todosLosWires.count { it == wire })
        // Patrón del wire: minúsculas + guion bajo.
        assertTrue("wire '$wire' debe cumplir ^[a-z_]+\$", patronWire.matches(wire))
        // Biyección: subtipoDe(wire) == tipo y wireDe(tipo) == wire.
        val subtipo = AccionRegistry.subtipoDe(wire)
        assertNotNull("wire '$wire' debe mapear a un subtipo", subtipo)
        assertEquals(tipo.kotlin, subtipo)
        assertEquals(wire, AccionRegistry.wireDe(tipo.kotlin))
    }

    @Test fun `wire llamar_contacto registrado unico y biyectivo`() =
        verificar("llamar_contacto", CommandEnvelope.LlamarContacto::class.java)

    @Test fun `wire enviar_sms registrado unico y biyectivo`() =
        verificar("enviar_sms", CommandEnvelope.EnviarSms::class.java)

    @Test fun `wire poner_alarma registrado unico y biyectivo`() =
        verificar("poner_alarma", CommandEnvelope.PonerAlarma::class.java)

    @Test fun `wire cancelar_alarma registrado unico y biyectivo`() =
        verificar("cancelar_alarma", CommandEnvelope.CancelarAlarma::class.java)

    @Test fun `wire abrir_app registrado unico y biyectivo`() =
        verificar("abrir_app", CommandEnvelope.AbrirApp::class.java)

    @Test fun `wire buscar_archivo registrado unico y biyectivo`() =
        verificar("buscar_archivo", CommandEnvelope.BuscarArchivo::class.java)

    @Test fun `wire encolar_mensaje registrado unico y biyectivo`() =
        verificar("encolar_mensaje", CommandEnvelope.EncolarMensaje::class.java)

    @Test fun `wire abrir_alarmas registrado unico y biyectivo`() =
        verificar("abrir_alarmas", CommandEnvelope.AbrirAlarmas::class.java)

    @Test fun `wire buscar_google registrado unico y biyectivo`() =
        verificar("buscar_google", CommandEnvelope.BuscarGoogle::class.java)

    @Test fun `wire abrir_youtube registrado unico y biyectivo`() =
        verificar("abrir_youtube", CommandEnvelope.AbrirYouTube::class.java)

    @Test fun `wire abrir_whatsapp registrado unico y biyectivo`() =
        verificar("abrir_whatsapp", CommandEnvelope.AbrirWhatsApp::class.java)

    @Test fun `wire reproducir_musica registrado unico y biyectivo`() =
        verificar("reproducir_musica", CommandEnvelope.ReproducirMusica::class.java)

    @Test fun `wire poner_volumen registrado unico y biyectivo`() =
        verificar("poner_volumen", CommandEnvelope.PonerVolumen::class.java)

    @Test fun `wire poner_idioma registrado unico y biyectivo`() =
        verificar("poner_idioma", CommandEnvelope.PonerIdioma::class.java)

    @Test fun `wire poner_temporizador registrado unico y biyectivo`() =
        verificar("poner_temporizador", CommandEnvelope.PonerTemporizador::class.java)

    @Test fun `wire navegar_a registrado unico y biyectivo`() =
        verificar("navegar_a", CommandEnvelope.NavegarA::class.java)

    @Test fun `wire abrir_ajustes registrado unico y biyectivo`() =
        verificar("abrir_ajustes", CommandEnvelope.AbrirAjustes::class.java)

    @Test fun `wire llamar_numero registrado unico y biyectivo`() =
        verificar("llamar_numero", CommandEnvelope.LlamarNumero::class.java)

    @Test fun `wire recordar_dato registrado unico y biyectivo`() =
        verificar("recordar_dato", CommandEnvelope.RecordarDato::class.java)

    @Test fun `wire crear_nota registrado unico y biyectivo`() =
        verificar("crear_nota", CommandEnvelope.CrearNota::class.java)

    @Test fun `wire leer_notas registrado unico y biyectivo`() =
        verificar("leer_notas", CommandEnvelope.LeerNotas::class.java)

    @Test fun `wire leer_nota registrado unico y biyectivo`() =
        verificar("leer_nota", CommandEnvelope.LeerNota::class.java)

    @Test fun `el registro tiene exactamente 22 wires`() {
        assertEquals(22, AccionRegistry.todosLosWires.size)
        assertEquals(22, AccionRegistry.todosLosWires.toSet().size)
    }

    @Test fun `emparejado case-sensitive rechaza mayusculas`() {
        assertNull(AccionRegistry.subtipoDe("LLAMAR_CONTACTO"))
        assertNull(AccionRegistry.subtipoDe("Poner_Volumen"))
        assertNull(AccionRegistry.subtipoDe("poner-volumen"))
    }
}
