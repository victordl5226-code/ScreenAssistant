package com.screenassistant.service.system.bridge

import android.content.Context
import android.content.Intent
import com.screenassistant.core.data.util.PuenteConfig
import com.screenassistant.core.data.util.PuenteConfigStore
import com.screenassistant.core.domain.service.TextToSpeech
import io.mockk.EqMatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * TaskerResponseEmitterImpl (P3, ADR-014/T4; F2, ADR-015): broadcast SIEMPRE
 * (canal máquina) con el JSON de 6 claves + id plano; TTS solo si hablar; y
 * setPackage (privacidad F2) desde la config compartida. Patrón de MockK de
 * CallActionTest:64 — mockkConstructor(Intent) con prototipo constructedWith +
 * slot capture del Intent entregado a sendBroadcast.
 *
 * QA #3: el stub de `cargar()` devuelve la config MATERIALIZADA
 * (packageRespuesta = PACKAGE_RESPUESTA_DEFAULT por defecto — H1: el store ya
 * materializa antes; los casos null/blank son input DEFENSIVO DIRECTO al emitter,
 * no alcanzable en producción) y el stub de setPackage captura el valor real.
 */
class TaskerResponseEmitterTest {

    private lateinit var context: Context
    private lateinit var tts: TextToSpeech
    private lateinit var configStore: PuenteConfigStore
    private lateinit var emitter: TaskerResponseEmitter

    private val intentSlot = slot<Intent>()
    private val respuestaOk =
        """{"version":1,"id":"t-1","estado":"ok","resultado":"Éxito: Volumen subido.","error":null,"mensaje":null}"""

    /** Extras REALES escritos por producción en el Intent (registrados por el stub). */
    private val extrasEnviados = mutableMapOf<String, String>()

    /** package capturado de setPackage (null si no se llamó — F2). */
    private var packageAplicado: String? = null

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        tts = mockk()
        every { tts.speak(any()) } returns Unit
        configStore = mockk()
        every { configStore.cargar() } returns
            PuenteConfig(packageRespuesta = PuenteConfig.PACKAGE_RESPUESTA_DEFAULT)

        mockkConstructor(Intent::class)
        // Prototipo del constructor registrado ANTES de la producción (B2),
        // con matchers TIPADOS (any<String>) para resolver la sobrecarga putExtra
        // (patrón CallActionTest:64); el id null NO se pone (putExtra(null) en
        // Android real remueve la clave → misma semántica, verificada como 0 calls).
        // El answers registra los extras en el mapa → los getters del mock devuelven
        // el valor REAL que el flujo escribió (las aserciones leen del Intent enviado).
        every {
            constructedWith<Intent>(EqMatcher(TaskerBridgeContract.ACTION_RESPUESTA))
                .putExtra(any(), any<String>())
        } answers {
            extrasEnviados[firstArg()] = secondArg()
            mockk()
        }
        every {
            constructedWith<Intent>(EqMatcher(TaskerBridgeContract.ACTION_RESPUESTA))
                .getStringExtra(any())
        } answers { extrasEnviados[firstArg()] }
        every {
            constructedWith<Intent>(EqMatcher(TaskerBridgeContract.ACTION_RESPUESTA))
                .action
        } returns TaskerBridgeContract.ACTION_RESPUESTA
        // F2: captura real de setPackage (patrón QA #3 — slot de respuesta).
        every {
            constructedWith<Intent>(EqMatcher(TaskerBridgeContract.ACTION_RESPUESTA))
                .setPackage(any())
        } answers {
            packageAplicado = firstArg<String>()
            mockk()
        }

        emitter = TaskerResponseEmitterImpl(context, tts, configStore)
    }

    @After
    fun tearDown() {
        unmockkConstructor(Intent::class)
    }

    // ===== Broadcast de vuelta (canal máquina): SIEMPRE =====

    @Test
    fun `emitir envia el broadcast con la accion de respuesta y el json`() {
        emitter.emitir(respuestaOk, hablar = true)

        verify { context.sendBroadcast(capture(intentSlot)) }
        // Lectura REAL del Intent enviado (el slot no es decorativo, punto 5):
        val enviado = intentSlot.captured
        assertEquals(TaskerBridgeContract.ACTION_RESPUESTA, enviado.action)
        assertEquals(respuestaOk, enviado.getStringExtra(TaskerBridgeContract.EXTRA_RESPUESTA))
        assertEquals("t-1", enviado.getStringExtra(TaskerBridgeContract.EXTRA_RESPUESTA_ID))
    }

    @Test
    fun `el broadcast lleva el id plano extraido del json`() {
        emitter.emitir(respuestaOk, hablar = false)

        verify { context.sendBroadcast(capture(intentSlot)) }
        assertEquals("t-1", intentSlot.captured.getStringExtra(TaskerBridgeContract.EXTRA_RESPUESTA_ID))
    }

    @Test
    fun `json invalido emite el broadcast sin id y sin hablar`() {
        emitter.emitir("no-json", hablar = true)

        verify { context.sendBroadcast(capture(intentSlot)) }
        // El JSON viaja íntegro (sin parseo) y el id plano se omite (no existe).
        assertEquals("no-json", intentSlot.captured.getStringExtra(TaskerBridgeContract.EXTRA_RESPUESTA))
        assertNull(intentSlot.captured.getStringExtra(TaskerBridgeContract.EXTRA_RESPUESTA_ID))
        verify(exactly = 0) { tts.speak(any()) }
    }

    // ===== TTS (canal humano): solo si hablar y hay texto =====

    @Test
    fun `hablar true habla el texto de la respuesta`() {
        emitter.emitir(respuestaOk, hablar = true)

        verify(exactly = 1) { tts.speak("Éxito: Volumen subido.") }
    }

    @Test
    fun `hablar false no habla pero si emite el broadcast`() {
        emitter.emitir(respuestaOk, hablar = false)

        verify(exactly = 0) { tts.speak(any()) }
        verify { context.sendBroadcast(any()) }
    }

    @Test
    fun `resultado null no habla pese a hablar true`() {
        val jsonSinResultado =
            """{"version":1,"id":"t-1","estado":"ok","resultado":null,"error":null,"mensaje":null}"""
        emitter.emitir(jsonSinResultado, hablar = true)

        verify(exactly = 0) { tts.speak(any()) }
        verify { context.sendBroadcast(any()) }
    }

    // ===== F2: setPackage desde la config compartida (ADR-015, QA #3) =====

    @Test
    fun `packageRespuesta configurado aplica setPackage al intent`() {
        every { configStore.cargar() } returns PuenteConfig(packageRespuesta = "com.otro.paquete")

        emitter.emitir(respuestaOk, hablar = false)

        assertEquals("com.otro.paquete", packageAplicado)
        verify { context.sendBroadcast(any()) }
    }

    @Test
    fun `packageRespuesta null no aplica setPackage`() {
        // Input DEFENSIVO DIRECTO (H1): en producción el store materializa antes;
        // el null solo llega si un test/config lo inyecta.
        every { configStore.cargar() } returns PuenteConfig(packageRespuesta = null)

        emitter.emitir(respuestaOk, hablar = false)

        assertNull("setPackage no debe llamarse con null", packageAplicado)
    }

    @Test
    fun `packageRespuesta blank no aplica setPackage`() {
        // Mismo input defensivo: blank → sin setPackage (canal global solo por
        // inyección directa; el modo global NO es alcanzable desde la UI, H1).
        every { configStore.cargar() } returns PuenteConfig(packageRespuesta = "   ")

        emitter.emitir(respuestaOk, hablar = false)

        assertNull("setPackage no debe llamarse con blank", packageAplicado)
    }
}
