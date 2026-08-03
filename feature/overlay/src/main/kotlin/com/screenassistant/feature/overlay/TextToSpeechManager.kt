package com.screenassistant.feature.overlay

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.screenassistant.core.domain.service.TextToSpeech as TextToSpeechService
import java.util.Locale

/**
 * Gestor TTS — canal humano del overlay y del puente Tasker (ADR-014/T4).
 *
 * El motor `TextToSpeech` inicializa ASÍNCRONO (binder): entre la construcción y
 * `onInit` cualquier `speak()` se perdería en silencio. Para no dropear las
 * primeras respuestas (p. ej. el primer broadcast del puente justo tras arrancar
 * el proceso — instancia Singleton de AppModule), los textos que llegan con el
 * motor aún no inicializado se encolan en un BUFFER FIFO con límite [BUFFER_MAX]:
 * al superarse se descartan los MÁS VIEJOS (se oirán las respuestas más recientes,
 * no las obsoletas) y se drena en orden en `onInit` cuando el motor queda listo.
 * Si el motor falla (`onInit` con error), el buffer se limpia sin hablar.
 *
 * El contrato público de `speak` no cambia: best-effort con buffer.
 */
class TextToSpeechManager(
    context: Context,
    private val onStart: () -> Unit = {},
    private val onDone: () -> Unit = {}
) : TextToSpeech.OnInitListener, TextToSpeechService {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    /** Buffer FIFO de pendientes (solo se usa mientras el motor no está listo). */
    private val buffer: ArrayDeque<String> = ArrayDeque()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
                setupCallbacks()
                drenarBuffer()
            }
            // Si setLanguage falla el motor no puede hablar: los pendientes quedan
            // retenidos (el límite del buffer protege la memoria) — mismo resultado
            // audible que el drop silencioso previo, sin perder el caso feliz.
        } else {
            // Motor no disponible (binder/init fallido): sin habla posible, se
            // descartan los pendientes para no retenerlos indefinidamente.
            buffer.clear()
        }
    }

    private fun setupCallbacks() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { onStart() }
            override fun onDone(utteranceId: String?) { onDone() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
    }

    override fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_voice")
        } else {
            // Buffer FIFO con límite: si se supera, se descarta el más viejo.
            buffer.addLast(text)
            if (buffer.size > BUFFER_MAX) buffer.removeFirst()
        }
    }

    /** Drena los pendientes EN ORDEN (FIFO): con QUEUE_ADD cada texto suena tras el
     *  anterior (el motor recién inicializado tiene la cola vacía — no hay pisado). */
    private fun drenarBuffer() {
        while (buffer.isNotEmpty()) {
            tts?.speak(buffer.removeFirst(), TextToSpeech.QUEUE_ADD, null, "assistant_voice")
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }

    companion object {
        /** Límite del buffer de pendientes (máx. textos retenidos sin motor listo). */
        const val BUFFER_MAX = 5
    }
}
