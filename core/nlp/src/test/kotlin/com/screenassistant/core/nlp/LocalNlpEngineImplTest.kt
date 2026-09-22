package com.screenassistant.core.nlp.engine

import com.screenassistant.core.domain.nlp.model.NlpIntent
import com.screenassistant.core.nlp.classifier.RuleBasedIntentClassifier
import com.screenassistant.core.nlp.extractor.SpanishEntityExtractor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para LocalNlpEngineImpl.
 */
class LocalNlpEngineImplTest {

    private lateinit var motor: LocalNlpEngineImpl

    @Before
    fun setUp() {
        motor = LocalNlpEngineImpl(
            RuleBasedIntentClassifier(),
            SpanishEntityExtractor()
        )
    }

    @Test
    fun `busca gatos retorna SEARCH_GOOGLE con confianza alta`() {
        val resultado = motor.procesar("busca gatos")
        assertEquals(NlpIntent.SEARCH_GOOGLE, resultado.intent)
        assertTrue(resultado.confianza >= 0.9)
        assertTrue(resultado.esReconocido)
    }

    @Test
    fun `hola mundo retorna UNKNOWN`() {
        val resultado = motor.procesar("hola mundo como estas")
        assertEquals(NlpIntent.UNKNOWN, resultado.intent)
        assertEquals(0.0, resultado.confianza, 0.001)
        assertFalse(resultado.esReconocido)
    }

    @Test
    fun `llama a Ana por favor retorna CALL_CONTACT con entidad`() {
        val resultado = motor.procesar("llama a Ana por favor")
        assertEquals(NlpIntent.CALL_CONTACT, resultado.intent)
        assertTrue(resultado.esReconocido)
        assertTrue(resultado.entidades.any { it is com.screenassistant.core.domain.nlp.model.NlpEntity.Contacto })
    }

    @Test
    fun `pon alarma 7 30 retorna SET_ALARM con entidad Hora`() {
        val resultado = motor.procesar("pon una alarma a las 7:30")
        assertEquals(NlpIntent.SET_ALARM, resultado.intent)
        assertTrue(resultado.esReconocido)
        assertTrue(resultado.entidades.any { it is com.screenassistant.core.domain.nlp.model.NlpEntity.Hora })
    }

    @Test
    fun `texto vacio retorna UNKNOWN`() {
        val resultado = motor.procesar("")
        assertEquals(NlpIntent.UNKNOWN, resultado.intent)
        assertFalse(resultado.esReconocido)
    }

    @Test
    fun `activa bluetooth retorna SET_BLUETOOTH con OnOff`() {
        val resultado = motor.procesar("activa el bluetooth")
        assertEquals(NlpIntent.SET_BLUETOOTH, resultado.intent)
        assertTrue(resultado.esReconocido)
        assertTrue(resultado.entidades.any {
            it is com.screenassistant.core.domain.nlp.model.NlpEntity.OnOff && it.activado
        })
    }

    @Test
    fun `abre WhatsApp retorna OPEN_APP`() {
        val resultado = motor.procesar("abre WhatsApp")
        assertEquals(NlpIntent.OPEN_APP, resultado.intent)
        assertTrue(resultado.esReconocido)
    }

    @Test
    fun `temporizador de 5 minutos retorna SET_TIMER con Duracion`() {
        val resultado = motor.procesar("temporizador de 5 minutos")
        assertEquals(NlpIntent.SET_TIMER, resultado.intent)
        assertTrue(resultado.esReconocido)
        assertTrue(resultado.entidades.any {
            it is com.screenassistant.core.domain.nlp.model.NlpEntity.Duracion && it.minutos == 5
        })
    }

    @Test
    fun `cuanto es 5 por 7 retorna CALCULATOR`() {
        val resultado = motor.procesar("cuanto es 5 por 7")
        assertEquals(NlpIntent.CALCULATOR, resultado.intent)
        assertTrue(resultado.esReconocido)
    }

    @Test
    fun `lee el texto de la pantalla retorna OCR_SCAN`() {
        val resultado = motor.procesar("lee el texto de la pantalla")
        assertEquals(NlpIntent.OCR_SCAN, resultado.intent)
        assertTrue(resultado.esReconocido)
    }

    @Test
    fun `preserva texto original`() {
        val texto = "Busca Gatos En Madrid"
        val resultado = motor.procesar(texto)
        assertEquals(texto, resultado.textoOriginal)
    }

    @Test
    fun `rendimiento 1000 iteraciones menor a 5 segundos`() {
        val inicio = System.currentTimeMillis()
        repeat(1000) {
            motor.procesar("busca gatos en madrid")
        }
        val duracion = System.currentTimeMillis() - inicio
        assertTrue("1000 iteraciones tomaron ${duracion}ms, límite 5000ms", duracion < 5000)
    }
}
