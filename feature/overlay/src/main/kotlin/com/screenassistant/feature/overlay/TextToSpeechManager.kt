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
 * Concurrencia (B6): `speak()` puede llegar desde el hilo IO del ViewModel o del
 * puente mientras `onInit` corre en el hilo binder del motor → el `ArrayDeque` y
 * `isInitialized` se protegen con `@Synchronized`/`@Volatile` (sin esto: corrupción
 * de índice o lectura stale que encolaría para siempre). La validación de esta
 * carrera es MANUAL en dispositivo (no reproducible en JVM unit): el guion es
 * arrancar el proceso con el puente enviando una respuesta inmediata.
 *
 * M19: idioma — se intenta es-ES PRIMERO (la asistente es bilingüe español) y, si el
 * motor no tiene datos de ese idioma (LANG_MISSING_DATA / LANG_NOT_SUPPORTED), se
 * cae al idioma del dispositivo (`Locale.getDefault()`). LIMITACIÓN documentada: si
 * AMBOS fallan, el motor no puede hablar — `isInitialized` queda false y los textos
 * se retienen en el buffer (mudez controlada con drenado si el idioma se instala
 * después; el límite del buffer protege la memoria). Sin drop silencioso.
 *
 * El contrato público de `speak` no cambia: best-effort con buffer.
 */
class TextToSpeechManager(
    context: Context,
    private val onStart: () -> Unit = {},
    private val onDone: () -> Unit = {}
) : TextToSpeech.OnInitListener, TextToSpeechService {

    private var tts: TextToSpeech? = TextToSpeech(context, this)

    /** B6: @Volatile — `speak()` (hilo IO) lee este flag mientras `onInit` (binder)
     *  lo escribe; sin el flag, una lectura stale encolaría para siempre. */
    @Volatile
    private var isInitialized = false

    /** Buffer FIFO de pendientes (solo se usa mientras el motor no está listo).
     *  Acceso serializado por @Synchronized en speak/onInit (B6). */
    private val buffer: ArrayDeque<String> = ArrayDeque()

    @Synchronized
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // M19: es-ES PRIMERO (la asistente es bilingüe español) con fallback al
            // idioma del dispositivo si el motor no tiene datos del español. Si AMBOS
            // fallan (LANG_MISSING_DATA en ambos) el motor no puede hablar: retención
            // en buffer (drenado si el idioma se instala después) — ver KDoc de clase.
            var result = tts?.setLanguage(Locale("es", "ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts?.setLanguage(Locale.getDefault())
            }
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
                setupCallbacks()
                drenarBuffer()
            }
            // Si ambos idiomas fallan el motor no puede hablar: los pendientes quedan
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

    @Synchronized
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
     *  anterior (el motor recién inicializado tiene la cola vacía — no hay pisado).
     *  @Synchronized por defensa (B6): solo se invoca desde onInit, pero el buffer
     *  compartido queda bajo el mismo monitor en cualquier punto futuro. */
    @Synchronized
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
