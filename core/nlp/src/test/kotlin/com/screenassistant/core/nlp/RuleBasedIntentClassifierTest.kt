package com.screenassistant.core.nlp.classifier

import com.screenassistant.core.domain.nlp.model.NlpIntent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitarios para RuleBasedIntentClassifier.
 */
class RuleBasedIntentClassifierTest {

    private val clasificador = RuleBasedIntentClassifier()

    @Test
    fun `crea una nota detecta CREATE_NOTE`() {
        val (intencion, confianza) = clasificador.clasificar("crea una nota de pan")
        assertEquals(NlpIntent.CREATE_NOTE, intencion)
        assert(confianza >= 0.9)
    }

    @Test
    fun `lee mis notas detecta READ_ALL_NOTES`() {
        val (intencion, _) = clasificador.clasificar("lee mis notas")
        assertEquals(NlpIntent.READ_ALL_NOTES, intencion)
    }

    @Test
    fun `ayuda detecta HELP`() {
        val (intencion, _) = clasificador.clasificar("que puedes hacer")
        assertEquals(NlpIntent.HELP, intencion)
    }

    @Test
    fun `repite detecta REPEAT`() {
        val (intencion, _) = clasificador.clasificar("repite")
        assertEquals(NlpIntent.REPEAT, intencion)
    }

    @Test
    fun `llama a Ana detecta CALL_CONTACT`() {
        val (intencion, _) = clasificador.clasificar("llama a Ana")
        assertEquals(NlpIntent.CALL_CONTACT, intencion)
    }

    @Test
    fun `llama al 600 detecta CALL_NUMBER`() {
        val (intencion, _) = clasificador.clasificar("llama al 600 123 456")
        assertEquals(NlpIntent.CALL_NUMBER, intencion)
    }

    @Test
    fun `abre ajustes detecta OPEN_SETTINGS`() {
        val (intencion, _) = clasificador.clasificar("abre los ajustes")
        assertEquals(NlpIntent.OPEN_SETTINGS, intencion)
    }

    @Test
    fun `busca gatos detecta SEARCH_GOOGLE`() {
        val (intencion, _) = clasificador.clasificar("busca gatos")
        assertEquals(NlpIntent.SEARCH_GOOGLE, intencion)
    }

    @Test
    fun `sube el volumen detecta SET_VOLUME`() {
        val (intencion, _) = clasificador.clasificar("sube el volumen")
        assertEquals(NlpIntent.SET_VOLUME, intencion)
    }

    @Test
    fun `activa bluetooth detecta SET_BLUETOOTH`() {
        val (intencion, _) = clasificador.clasificar("activa el bluetooth")
        assertEquals(NlpIntent.SET_BLUETOOTH, intencion)
    }

    @Test
    fun `enciende linterna detecta SET_FLASHLIGHT`() {
        val (intencion, _) = clasificador.clasificar("enciende la linterna")
        assertEquals(NlpIntent.SET_FLASHLIGHT, intencion)
    }

    @Test
    fun `donde estoy detecta GET_LOCATION`() {
        val (intencion, _) = clasificador.clasificar("donde estoy")
        assertEquals(NlpIntent.GET_LOCATION, intencion)
    }

    @Test
    fun `saca una foto detecta TAKE_PHOTO`() {
        val (intencion, _) = clasificador.clasificar("saca una foto")
        assertEquals(NlpIntent.TAKE_PHOTO, intencion)
    }

    @Test
    fun `activar monitoreo detecta START_MONITORING`() {
        val (intencion, _) = clasificador.clasificar("activar monitoreo")
        assertEquals(NlpIntent.START_MONITORING, intencion)
    }

    @Test
    fun `analiza mi pantalla detecta ANALYZE_SCREEN`() {
        val (intencion, _) = clasificador.clasificar("analiza mi pantalla")
        assertEquals(NlpIntent.ANALYZE_SCREEN, intencion)
    }

    @Test
    fun `traduce hola detecta TRANSLATE_TEXT`() {
        val (intencion, _) = clasificador.clasificar("traduce hola al ingles")
        assertEquals(NlpIntent.TRANSLATE_TEXT, intencion)
    }

    @Test
    fun `escanea qr detecta SCAN_QR`() {
        val (intencion, _) = clasificador.clasificar("escanea el codigo qr")
        assertEquals(NlpIntent.SCAN_QR, intencion)
    }

    @Test
    fun `texto sin sentido detecta UNKNOWN`() {
        val (intencion, confianza) = clasificador.clasificar("hola mundo como estas")
        assertEquals(NlpIntent.UNKNOWN, intencion)
        assertEquals(0.0, confianza, 0.001)
    }

    @Test
    fun `abre WhatsApp detecta OPEN_APP`() {
        val (intencion, _) = clasificador.clasificar("abre WhatsApp")
        assertEquals(NlpIntent.OPEN_APP, intencion)
    }

    @Test
    fun `recuerda que detecta SAVE_MEMORY`() {
        val (intencion, _) = clasificador.clasificar("recuerda que me gusta el cafe")
        assertEquals(NlpIntent.SAVE_MEMORY, intencion)
    }

    @Test
    fun `cancela alarma detecta CANCEL_ALARM`() {
        val (intencion, _) = clasificador.clasificar("cancela la alarma")
        assertEquals(NlpIntent.CANCEL_ALARM, intencion)
    }

    @Test
    fun `envia mensaje detecta SEND_SMS`() {
        val (intencion, _) = clasificador.clasificar("envia un mensaje a Ana")
        assertEquals(NlpIntent.SEND_SMS, intencion)
    }

    @Test
    fun `abre archivo detecta OPEN_FILE`() {
        val (intencion, _) = clasificador.clasificar("abre el archivo documento")
        assertEquals(NlpIntent.OPEN_FILE, intencion)
    }

    @Test
    fun `activa wifi detecta SET_WIFI`() {
        val (intencion, _) = clasificador.clasificar("activa el wifi")
        assertEquals(NlpIntent.SET_WIFI, intencion)
    }

    @Test
    fun `cuanto es 5 por 7 detecta CALCULATOR`() {
        val (intencion, _) = clasificador.clasificar("cuanto es 5 por 7")
        assertEquals(NlpIntent.CALCULATOR, intencion)
    }

    @Test
    fun `inicia cronometro detecta STOPWATCH_START`() {
        val (intencion, _) = clasificador.clasificar("inicia el cronometro")
        assertEquals(NlpIntent.STOPWATCH_START, intencion)
    }

    @Test
    fun `temporizador de 5 minutos detecta SET_TIMER`() {
        val (intencion, _) = clasificador.clasificar("temporizador de 5 minutos")
        assertEquals(NlpIntent.SET_TIMER, intencion)
    }

    @Test
    fun `lista contactos detecta LIST_CONTACTS`() {
        val (intencion, _) = clasificador.clasificar("lista mis contactos")
        assertEquals(NlpIntent.LIST_CONTACTS, intencion)
    }

    @Test
    fun `historial de llamadas detecta CALL_HISTORY`() {
        val (intencion, _) = clasificador.clasificar("historial de llamadas")
        assertEquals(NlpIntent.CALL_HISTORY, intencion)
    }

    @Test
    fun `que telefono tengo detecta DEVICE_INFO_MODEL`() {
        val (intencion, _) = clasificador.clasificar("que telefono tengo")
        assertEquals(NlpIntent.DEVICE_INFO_MODEL, intencion)
    }

    @Test
    fun `cuanta bateria queda detecta DEVICE_INFO_BATTERY`() {
        val (intencion, _) = clasificador.clasificar("cuanta bateria queda")
        assertEquals(NlpIntent.DEVICE_INFO_BATTERY, intencion)
    }
}
