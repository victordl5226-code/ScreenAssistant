package com.screenassistant.service.system.bridge

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.bridge.CommandBridge
import com.screenassistant.core.domain.bridge.CommandClassifier
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec
import com.screenassistant.core.domain.bridge.model.TipoComando
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.core.domain.model.VolumeAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Puente Tasker end-to-end (ADR-013, H1): un test feliz por wire (22) con MockK
 * sobre SystemAction — verifica el comando recibido, el estado "ok" y la
 * clasificación CONSULTA/ACCION — más los caminos de fallo (ActionResult.Error,
 * excepción) y la emisión literal de "resultado":null.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskerBridgeTest {

    private lateinit var systemAction: SystemAction
    private lateinit var bridge: CommandBridge

    private val json = Json { ignoreUnknownKeys = false }

    @Before
    fun setup() {
        systemAction = mockk()
        bridge = SystemCommandBridgeImpl(systemAction, SystemCommandJsonCodec())
    }

    private suspend fun handle(input: String): String = bridge.handle(input)

    private fun assertOk(respuesta: String, id: String? = null, resultado: String) {
        val obj = json.parseToJsonElement(respuesta).jsonObject
        assertEquals("ok", obj["estado"]?.jsonPrimitive?.contentOrNull)
        assertEquals(id, obj["id"]?.jsonPrimitive?.contentOrNull)
        assertEquals(resultado, obj["resultado"]?.jsonPrimitive?.contentOrNull)
    }

    // ===== 22 felices: un wire por test =====

    @Test
    fun `llamar_contacto ejecuta Call con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.Call("Ana")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Llamando a Ana...")
        val respuesta = handle("""{"version":1,"id":"t-1","accion":"llamar_contacto","contacto":"Ana"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, id = "t-1", resultado = "Éxito: Llamando a Ana...")
    }

    @Test
    fun `enviar_sms ejecuta SendSms con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.SendSms("Ana", "Hola")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: SMS enviado a Ana.")
        val respuesta = handle("""{"version":1,"accion":"enviar_sms","contacto":"Ana","mensaje":"Hola"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: SMS enviado a Ana.")
    }

    @Test
    fun `poner_alarma ejecuta SetAlarm con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.SetAlarm(7, 30, "despertarme")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Alarma configurada para las 7:30.")
        val respuesta = handle("""{"version":1,"accion":"poner_alarma","hora":7,"minuto":30,"etiqueta":"despertarme"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Alarma configurada para las 7:30.")
    }

    @Test
    fun `cancelar_alarma ejecuta CancelAlarm con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.CancelAlarm(7, 30)
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Alarma de las 7:30 cancelada.")
        val respuesta = handle("""{"version":1,"accion":"cancelar_alarma","hora":7,"minuto":30}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Alarma de las 7:30 cancelada.")
    }

    @Test
    fun `abrir_app ejecuta OpenApp con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.OpenApp("whatsapp")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Abriendo WhatsApp.")
        val respuesta = handle("""{"version":1,"accion":"abrir_app","aplicacion":"whatsapp"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Abriendo WhatsApp.")
    }

    @Test
    fun `buscar_archivo ejecuta SearchFile con estado ok y es CONSULTA`() = runTest {
        val comando = SystemCommand.SearchFile("informe")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Encontrado informe.pdf.")
        val respuesta = handle("""{"version":1,"accion":"buscar_archivo","busqueda":"informe"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.CONSULTA, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Encontrado informe.pdf.")
    }

    @Test
    fun `encolar_mensaje ejecuta QueueMessage con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.QueueMessage("whatsapp", "Ana", "Hola")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Mensaje encolado para Ana.")
        val respuesta = handle("""{"version":1,"accion":"encolar_mensaje","plataforma":"whatsapp","contacto":"Ana","mensaje":"Hola"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Mensaje encolado para Ana.")
    }

    @Test
    fun `abrir_alarmas ejecuta OpenAlarms con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.OpenAlarms
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Abriendo alarmas.")
        val respuesta = handle("""{"version":1,"accion":"abrir_alarmas"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Abriendo alarmas.")
    }

    @Test
    fun `buscar_google ejecuta SearchGoogle con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.SearchGoogle("gatos")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Buscando gatos...")
        val respuesta = handle("""{"version":1,"accion":"buscar_google","busqueda":"gatos"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Buscando gatos...")
    }

    @Test
    fun `abrir_youtube ejecuta OpenYouTube con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.OpenYouTube("queen")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Abriendo YouTube.")
        val respuesta = handle("""{"version":1,"accion":"abrir_youtube","busqueda":"queen"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Abriendo YouTube.")
    }

    @Test
    fun `abrir_whatsapp ejecuta OpenWhatsApp con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.OpenWhatsApp
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Abriendo WhatsApp.")
        val respuesta = handle("""{"version":1,"accion":"abrir_whatsapp"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Abriendo WhatsApp.")
    }

    @Test
    fun `reproducir_musica ejecuta PlayMusic con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.PlayMusic("queen")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Reproduciendo música.")
        val respuesta = handle("""{"version":1,"accion":"reproducir_musica","busqueda":"queen"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Reproduciendo música.")
    }

    @Test
    fun `poner_volumen ejecuta SetVolume con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.SetVolume(VolumeAction.UP)
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Volumen subido.")
        val respuesta = handle("""{"version":1,"accion":"poner_volumen","valor":"subir"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Volumen subido.")
    }

    @Test
    fun `poner_idioma ejecuta SetLanguage con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.SetLanguage(AssistantLanguage.SPANISH)
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Entendido, hablaré en español.")
        val respuesta = handle("""{"version":1,"accion":"poner_idioma","idioma":"espanol"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Entendido, hablaré en español.")
    }

    @Test
    fun `poner_temporizador ejecuta SetTimer con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.SetTimer(90)
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Temporizador configurado para 90 minutos.")
        val respuesta = handle("""{"version":1,"accion":"poner_temporizador","minutos":90}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Temporizador configurado para 90 minutos.")
    }

    @Test
    fun `navegar_a ejecuta Navigate con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.Navigate("la oficina")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Abriendo Maps hacia la oficina.")
        val respuesta = handle("""{"version":1,"accion":"navegar_a","destino":"la oficina"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Abriendo Maps hacia la oficina.")
    }

    @Test
    fun `abrir_ajustes ejecuta OpenSettings con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.OpenSettings
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Abriendo ajustes del sistema.")
        val respuesta = handle("""{"version":1,"accion":"abrir_ajustes"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Abriendo ajustes del sistema.")
    }

    @Test
    fun `llamar_numero ejecuta CallNumber con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.CallNumber("+34600123456")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Llamando al 600123456...")
        val respuesta = handle("""{"version":1,"accion":"llamar_numero","telefono":"+34600123456"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Llamando al 600123456...")
    }

    @Test
    fun `recordar_dato ejecuta SaveMemory con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.SaveMemory("me gusta el cafe")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Entendido, lo recordaré.")
        val respuesta = handle("""{"version":1,"accion":"recordar_dato","dato":"me gusta el cafe"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Entendido, lo recordaré.")
    }

    @Test
    fun `crear_nota ejecuta CreateNote con estado ok y es ACCION`() = runTest {
        val comando = SystemCommand.CreateNote("comprar leche")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Nota guardada. Empieza así: «comprar leche»")
        val respuesta = handle("""{"version":1,"accion":"crear_nota","texto":"comprar leche"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.ACCION, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Nota guardada. Empieza así: «comprar leche»")
    }

    @Test
    fun `leer_notas ejecuta ReadNotes con estado ok y es CONSULTA`() = runTest {
        val comando = SystemCommand.ReadNotes
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: Tienes 2 notas. La más reciente empieza así: «comprar leche»")
        val respuesta = handle("""{"version":1,"accion":"leer_notas"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.CONSULTA, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: Tienes 2 notas. La más reciente empieza así: «comprar leche»")
    }

    @Test
    fun `leer_nota ejecuta ReadNote con estado ok y es CONSULTA`() = runTest {
        val comando = SystemCommand.ReadNote("pan")
        coEvery { systemAction.execute(comando) } returns ActionResult.Success("Éxito: La nota dice: «pan»")
        val respuesta = handle("""{"version":1,"accion":"leer_nota","busqueda":"pan"}""")
        coVerify(exactly = 1) { systemAction.execute(comando) }
        assertEquals(TipoComando.CONSULTA, CommandClassifier.clasificar(comando))
        assertOk(respuesta, resultado = "Éxito: La nota dice: «pan»")
    }

    // ===== Fallos de ejecución =====

    @Test
    fun `ActionResult Error devuelve fallo_ejecucion con reason verbatim`() = runTest {
        coEvery { systemAction.execute(any()) } returns
            ActionResult.Error("No se pudo configurar el temporizador.")
        val respuesta = handle("""{"version":1,"id":"t-7","accion":"poner_temporizador","minutos":5}""")
        val obj = json.parseToJsonElement(respuesta).jsonObject
        assertEquals("error", obj["estado"]?.jsonPrimitive?.contentOrNull)
        assertEquals("fallo_ejecucion", obj["error"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Error: No se pudo configurar el temporizador.", obj["mensaje"]?.jsonPrimitive?.contentOrNull)
        assertEquals("t-7", obj["id"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `excepcion en execute devuelve fallo_ejecucion con el mensaje`() = runTest {
        coEvery { systemAction.execute(any()) } throws RuntimeException("boom")
        val respuesta = handle("""{"version":1,"accion":"abrir_app","aplicacion":"x"}""")
        val obj = json.parseToJsonElement(respuesta).jsonObject
        assertEquals("error", obj["estado"]?.jsonPrimitive?.contentOrNull)
        assertEquals("fallo_ejecucion", obj["error"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Error: boom", obj["mensaje"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `data object con fallo emite resultado null literal`() = runTest {
        coEvery { systemAction.execute(SystemCommand.OpenAlarms) } returns
            ActionResult.Error("Dime a qué hora quieres la alarma.")
        val respuesta = handle("""{"version":1,"accion":"abrir_alarmas"}""")
        assertTrue("la respuesta debe emitir \"resultado\":null literal: $respuesta", respuesta.contains("\"resultado\":null"))
        val obj = json.parseToJsonElement(respuesta).jsonObject
        assertEquals(null, obj["resultado"]?.jsonPrimitive?.contentOrNull)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenAlarms) }
    }

    @Test
    fun `data object con id en el wire devuelve el id en la respuesta`() = runTest {
        coEvery { systemAction.execute(SystemCommand.OpenAlarms) } returns
            ActionResult.Success("Éxito: Abriendo alarmas.")
        val respuesta = handle("""{"version":1,"accion":"abrir_alarmas","id":"t-9"}""")
        val obj = json.parseToJsonElement(respuesta).jsonObject
        assertEquals("ok", obj["estado"]?.jsonPrimitive?.contentOrNull)
        assertEquals("t-9", obj["id"]?.jsonPrimitive?.contentOrNull)
        coVerify(exactly = 1) { systemAction.execute(SystemCommand.OpenAlarms) }
    }

    // ===== B7 (ADR-B7): fallo FUNCIONAL vs éxito real (solo rama Success) =====

    @Test
    fun `Success con mensaje de error devuelve fallo_accion con el mensaje verbatim`() = runTest {
        // La acción se ejecutó pero devolvió "Error: ..." (ADR-009) — el wire debe
        // informar fallo_accion, no éxito. La rama Error estructural NO cambia.
        coEvery { systemAction.execute(any()) } returns
            ActionResult.Success("Error: No encontré ninguna aplicación llamada 'x'.")
        val respuesta = handle("""{"version":1,"id":"t-20","accion":"abrir_app","aplicacion":"x"}""")
        val obj = json.parseToJsonElement(respuesta).jsonObject
        assertEquals("error", obj["estado"]?.jsonPrimitive?.contentOrNull)
        assertEquals("fallo_accion", obj["error"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Error: No encontré ninguna aplicación llamada 'x'.", obj["mensaje"]?.jsonPrimitive?.contentOrNull)
        assertEquals("t-20", obj["id"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `Success con mensaje de error emite resultado null literal`() = runTest {
        coEvery { systemAction.execute(any()) } returns
            ActionResult.Success("Error: No hay alarmas que cancelar.")
        val respuesta = handle("""{"version":1,"accion":"cancelar_alarma"}""")
        assertTrue("el fallo funcional debe emitir \"resultado\":null literal: $respuesta", respuesta.contains("\"resultado\":null"))
        val obj = json.parseToJsonElement(respuesta).jsonObject
        assertEquals(null, obj["resultado"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `Success con error en minusculas se trata como ok por case sensitive`() = runTest {
        // "error: ..." NO es el prefijo ADR-009 exacto → contenido, no fallo.
        coEvery { systemAction.execute(any()) } returns
            ActionResult.Success("error: algo salió mal")
        val respuesta = handle("""{"version":1,"accion":"leer_notas"}""")
        val obj = json.parseToJsonElement(respuesta).jsonObject
        assertEquals("ok", obj["estado"]?.jsonPrimitive?.contentOrNull)
        assertEquals("error: algo salió mal", obj["resultado"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `Success con Error sin prefijo exacto se trata como ok`() = runTest {
        // "Error de conexión" (sin ": ") no matchea "Error: " → éxito con contenido.
        coEvery { systemAction.execute(any()) } returns
            ActionResult.Success("Error de conexión inesperado")
        val respuesta = handle("""{"version":1,"accion":"leer_nota","busqueda":"pan"}""")
        val obj = json.parseToJsonElement(respuesta).jsonObject
        assertEquals("ok", obj["estado"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Error de conexión inesperado", obj["resultado"]?.jsonPrimitive?.contentOrNull)
    }
}
