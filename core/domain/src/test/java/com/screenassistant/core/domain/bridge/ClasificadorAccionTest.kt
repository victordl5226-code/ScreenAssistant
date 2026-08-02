package com.screenassistant.core.domain.bridge

import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec.RespuestaCodec
import com.screenassistant.core.domain.bridge.model.CommandEnvelope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Clasificación F1 feliz: un JSON válido por wire (de los 22) decodifica al
 * envelope correcto. Todos incluyen "version":1 (obligatoria por contrato).
 *
 * Nota de alcance: este test cubre SOLO el decode F1 feliz (wire → envelope).
 * La clasificación CONSULTA/ACCION (CommandClassifier) se asserta en
 * TaskerBridgeTest (service:system), que verifica el tipo de cada comando.
 */
class ClasificadorAccionTest {

    private val codec = SystemCommandJsonCodec()

    private fun clasificar(json: String): CommandEnvelope {
        val resultado = codec.decode(json)
        return (resultado as RespuestaCodec.Success).envelope
    }

    @Test fun `json llamar_contacto clasifica a LlamarContacto`() {
        assertEquals(
            CommandEnvelope.LlamarContacto(contacto = "Ana"),
            clasificar("""{"accion":"llamar_contacto","contacto":"Ana","version":1}""")
        )
    }

    @Test fun `json enviar_sms clasifica a EnviarSms`() {
        assertEquals(
            CommandEnvelope.EnviarSms(contacto = "Ana", mensaje = "Te veo a las 7"),
            clasificar("""{"accion":"enviar_sms","contacto":"Ana","mensaje":"Te veo a las 7","version":1}""")
        )
    }

    @Test fun `json poner_alarma clasifica a PonerAlarma`() {
        assertEquals(
            CommandEnvelope.PonerAlarma(hora = 7, minuto = 30),
            clasificar("""{"accion":"poner_alarma","hora":7,"minuto":30,"version":1}""")
        )
    }

    @Test fun `json cancelar_alarma clasifica a CancelarAlarma`() {
        assertEquals(
            CommandEnvelope.CancelarAlarma(hora = 7, minuto = 30),
            clasificar("""{"accion":"cancelar_alarma","hora":7,"minuto":30,"version":1}""")
        )
    }

    @Test fun `json abrir_app clasifica a AbrirApp`() {
        assertEquals(
            CommandEnvelope.AbrirApp(aplicacion = "whatsapp"),
            clasificar("""{"accion":"abrir_app","aplicacion":"whatsapp","version":1}""")
        )
    }

    @Test fun `json buscar_archivo clasifica a BuscarArchivo`() {
        assertEquals(
            CommandEnvelope.BuscarArchivo(busqueda = "informe"),
            clasificar("""{"accion":"buscar_archivo","busqueda":"informe","version":1}""")
        )
    }

    @Test fun `json encolar_mensaje clasifica a EncolarMensaje`() {
        assertEquals(
            CommandEnvelope.EncolarMensaje(plataforma = "whatsapp", contacto = "Ana", mensaje = "Hola"),
            clasificar(
                """{"accion":"encolar_mensaje","plataforma":"whatsapp","contacto":"Ana","mensaje":"Hola","version":1}"""
            )
        )
    }

    @Test fun `json abrir_alarmas clasifica a AbrirAlarmas`() {
        assertEquals(
            CommandEnvelope.AbrirAlarmas,
            clasificar("""{"accion":"abrir_alarmas","version":1}""")
        )
    }

    @Test fun `json buscar_google clasifica a BuscarGoogle`() {
        assertEquals(
            CommandEnvelope.BuscarGoogle(busqueda = "gatos"),
            clasificar("""{"accion":"buscar_google","busqueda":"gatos","version":1}""")
        )
    }

    @Test fun `json abrir_youtube clasifica a AbrirYouTube`() {
        assertEquals(
            CommandEnvelope.AbrirYouTube(busqueda = "queen"),
            clasificar("""{"accion":"abrir_youtube","busqueda":"queen","version":1}""")
        )
    }

    @Test fun `json abrir_whatsapp clasifica a AbrirWhatsApp`() {
        assertEquals(
            CommandEnvelope.AbrirWhatsApp,
            clasificar("""{"accion":"abrir_whatsapp","version":1}""")
        )
    }

    @Test fun `json reproducir_musica clasifica a ReproducirMusica`() {
        assertEquals(
            CommandEnvelope.ReproducirMusica(busqueda = "queen"),
            clasificar("""{"accion":"reproducir_musica","busqueda":"queen","version":1}""")
        )
    }

    @Test fun `json poner_volumen clasifica a PonerVolumen`() {
        assertEquals(
            CommandEnvelope.PonerVolumen(valor = "subir"),
            clasificar("""{"accion":"poner_volumen","valor":"subir","version":1}""")
        )
    }

    @Test fun `json poner_idioma clasifica a PonerIdioma`() {
        assertEquals(
            CommandEnvelope.PonerIdioma(idioma = "espanol"),
            clasificar("""{"accion":"poner_idioma","idioma":"espanol","version":1}""")
        )
    }

    @Test fun `json poner_temporizador clasifica a PonerTemporizador`() {
        assertEquals(
            CommandEnvelope.PonerTemporizador(minutos = 90),
            clasificar("""{"accion":"poner_temporizador","minutos":90,"version":1}""")
        )
    }

    @Test fun `json navegar_a clasifica a NavegarA`() {
        assertEquals(
            CommandEnvelope.NavegarA(destino = "la oficina"),
            clasificar("""{"accion":"navegar_a","destino":"la oficina","version":1}""")
        )
    }

    @Test fun `json abrir_ajustes clasifica a AbrirAjustes`() {
        assertEquals(
            CommandEnvelope.AbrirAjustes,
            clasificar("""{"accion":"abrir_ajustes","version":1}""")
        )
    }

    @Test fun `json llamar_numero clasifica a LlamarNumero`() {
        assertEquals(
            CommandEnvelope.LlamarNumero(telefono = "+34600123456"),
            clasificar("""{"accion":"llamar_numero","telefono":"+34600123456","version":1}""")
        )
    }

    @Test fun `json recordar_dato clasifica a RecordarDato`() {
        assertEquals(
            CommandEnvelope.RecordarDato(dato = "me gusta el cafe"),
            clasificar("""{"accion":"recordar_dato","dato":"me gusta el cafe","version":1}""")
        )
    }

    @Test fun `json crear_nota clasifica a CrearNota`() {
        assertEquals(
            CommandEnvelope.CrearNota(texto = "comprar leche"),
            clasificar("""{"accion":"crear_nota","texto":"comprar leche","version":1}""")
        )
    }

    @Test fun `json leer_notas clasifica a LeerNotas`() {
        assertEquals(
            CommandEnvelope.LeerNotas,
            clasificar("""{"accion":"leer_notas","version":1}""")
        )
    }

    @Test fun `json leer_nota clasifica a LeerNota`() {
        assertEquals(
            CommandEnvelope.LeerNota(busqueda = "pan"),
            clasificar("""{"accion":"leer_nota","busqueda":"pan","version":1}""")
        )
    }
}
