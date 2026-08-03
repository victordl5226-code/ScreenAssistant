package com.screenassistant.feature.overlay

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import io.mockk.OfTypeMatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TextToSpeechManager — buffer FIFO del canal humano (cierre QA/Supervisor, punto 1).
 *
 * El motor TTS inicializa asíncrono (binder): speak() antes de onInit NO debe dropearse
 * en silencio (p. ej. el primer broadcast del puente con la instancia Singleton de
 * AppModule). Se encola, onInit(SUCCESS) drena en orden, onInit con error limpia la cola
 * sin hablar, y el límite BUFFER_MAX descarta los más viejos.
 *
 * Patrón mockkConstructor del repo (precedente AlarmActionTest/CallActionTest): el
 * constructor TextToSpeech(Context, OnInitListener) se mockea con matchers explícitos
 * (constructedWith NO acepta any<T>() como parámetro — el repo usa EqMatcher/OfTypeMatcher)
 * y el manager ES el OnInitListener: el test llama a onInit directamente con SUCCESS/ERROR.
 */
class TextToSpeechManagerTest {

    private lateinit var context: Context
    private lateinit var manager: TextToSpeechManager

    /** Textos reales entregados al motor (registrados por el stub, no por el estado). */
    private val textosHablados = mutableListOf<String>()

    // Matchers del constructor TextToSpeech(Context, OnInitListener) mockeado: se
    // reutilizan inline en cada every/verify (constructedWith es de la scope MockK).
    private val ttsCtxMatcher = OfTypeMatcher<Context>(Context::class)
    private val ttsListenerMatcher = OfTypeMatcher<TextToSpeech.OnInitListener>(TextToSpeech.OnInitListener::class)

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mockkConstructor(TextToSpeech::class)
        // Motor mockeado (no-relaxed): setLanguage "exitoso" (LANG_AVAILABLE → el
        // manager queda inicializado en onInit(SUCCESS)) y el resto de métodos usados.
        // speak se stubbea con matchers TIPADOS para resolver la sobrecarga
        // speak(CharSequence, Int, Bundle, String) vs la deprecada con HashMap.
        every {
            constructedWith<TextToSpeech>(ttsCtxMatcher, ttsListenerMatcher).setLanguage(any())
        } returns TextToSpeech.LANG_AVAILABLE
        every {
            constructedWith<TextToSpeech>(ttsCtxMatcher, ttsListenerMatcher)
                .setOnUtteranceProgressListener(any())
        } returns TextToSpeech.SUCCESS
        every {
            constructedWith<TextToSpeech>(ttsCtxMatcher, ttsListenerMatcher)
                .speak(any<CharSequence>(), any<Int>(), any<Bundle>(), any<String>())
        } answers {
            textosHablados.add(firstArg<CharSequence>().toString())
            TextToSpeech.SUCCESS
        }
        every {
            constructedWith<TextToSpeech>(ttsCtxMatcher, ttsListenerMatcher).stop()
        } returns TextToSpeech.SUCCESS
        // shutdown() es void en el SDK (a diferencia de stop(), que es int).
        every {
            constructedWith<TextToSpeech>(ttsCtxMatcher, ttsListenerMatcher).shutdown()
        } returns Unit

        manager = TextToSpeechManager(context)
    }

    @After
    fun tearDown() {
        unmockkConstructor(TextToSpeech::class)
    }

    // ===== speak() antes de onInit: encola, no dropea =====

    @Test
    fun `speak antes de onInit encola sin hablar`() {
        manager.speak("hola")

        assertTrue("el texto debe quedar retenido (no hablado)", textosHablados.isEmpty())
        verify(exactly = 0) {
            constructedWith<TextToSpeech>(ttsCtxMatcher, ttsListenerMatcher)
                .speak(any<CharSequence>(), any<Int>(), any<Bundle>(), any<String>())
        }
    }

    // ===== onInit(SUCCESS): drena en orden FIFO =====

    @Test
    fun `onInit exitoso drena el buffer en orden FIFO`() {
        manager.speak("primero")
        manager.speak("segundo")

        manager.onInit(TextToSpeech.SUCCESS)

        assertEquals(listOf("primero", "segundo"), textosHablados)
    }

    // ===== onInit(error): limpia la cola sin hablar =====

    @Test
    fun `onInit con error limpia la cola sin hablar`() {
        manager.speak("viejo")

        manager.onInit(TextToSpeech.ERROR)
        manager.speak("nuevo")
        manager.onInit(TextToSpeech.SUCCESS)

        // "viejo" se descartó con el error; "nuevo" (encolado tras el error) sí se drena.
        assertEquals(listOf("nuevo"), textosHablados)
    }

    // ===== Límite de la cola: descarta los más viejos =====

    @Test
    fun `el limite de cola descarta los textos mas viejos`() {
        repeat(TextToSpeechManager.BUFFER_MAX + 2) { i -> manager.speak("texto-$i") }

        manager.onInit(TextToSpeech.SUCCESS)

        // Se conservan los 5 últimos ("texto-2".."texto-6"); los 2 más viejos se descartan.
        assertEquals(TextToSpeechManager.BUFFER_MAX, textosHablados.size)
        assertEquals("texto-2", textosHablados.first())
        assertEquals("texto-6", textosHablados.last())
    }

    // ===== Contrato previo intacto: motor listo → speak directo sin buffer =====

    @Test
    fun `speak con motor inicializado habla directo sin buffer`() {
        manager.onInit(TextToSpeech.SUCCESS)

        manager.speak("directo")

        assertEquals(listOf("directo"), textosHablados)
    }
}
