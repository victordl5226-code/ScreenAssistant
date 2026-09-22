package com.screenassistant.feature.overlay

import android.content.Context
import android.util.Log
import com.screenassistant.core.domain.service.SpeechToText
import com.screenassistant.core.model.domain.ModelAsset
import com.screenassistant.core.model.domain.ModelAssetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException

/**
 * Implementación de [SpeechToText] usando la librería Vosk para reconocimiento
 * de voz 100% offline.
 *
 * El modelo se descarga bajo demanda desde GitHub Releases a través de
 * [ModelAssetRepository]. La carga es **lazy**: se realiza solo en la primera
 * llamada a [startListening], no en la construcción.
 *
 * Si la descarga o carga del modelo falla, el STT queda deshabilitado
 * y se notifica vía [onErrorCallback].
 *
 * Los tipos de Vosk ([org.vosk.Model], [org.vosk.Recognizer]) se crean
 * a través de funciones `internal open` extraídas para permitir mocking
 * en tests JVM (la librería nativa no está disponible en el classpath de tests).
 *
 * @param context Contexto de aplicación.
 * @param onResultCallback Callback con el texto reconocido definitivo.
 * @param onPartialResultCallback Callback con el texto parcial reconocido.
 * @param onErrorCallback Callback de errores.
 * @param modelRepository Repositorio de modelos descargables (inyectado).
 * @param dispatcher Dispatcher para corrutinas (inyectable para tests).
 */
open class VoskSpeechToTextManager(
    private val context: Context,
    private val onResultCallback: (String) -> Unit,
    private val onPartialResultCallback: (String) -> Unit = {},
    private val onErrorCallback: (String) -> Unit = {},
    private val modelRepository: ModelAssetRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SpeechToText, RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val initMutex = Mutex()
    private var isModelReady = false

    /**
     * Garantiza que el modelo Vosk esté cargado y listo para usar.
     *
     * - Si ya está cargado, retorna `true` inmediatamente.
     * - Si no está descargado, lo descarga a través de [modelRepository].
     * - Si la descarga o carga del modelo falla, retorna `false` (STT deshabilitado).
     *
     * THREAD-SAFE: el Mutex serializa concurrentes que llamen a [startListening] simultáneamente.
     */
    internal open suspend fun ensureModelLoaded(): Boolean {
        if (isModelReady && recognizer != null) return true

        initMutex.withLock {
            // Doble chequeo tras adquirir el mutex (otro hilo pudo haberlo cargado).
            if (isModelReady && recognizer != null) return true

            try {
                if (!modelRepository.isReady(ModelAsset.VOSK_STT)) {
                    Log.d(TAG, "Modelo Vosk no descargado, iniciando descarga...")
                    modelRepository.download(ModelAsset.VOSK_STT)
                }

                val path = modelRepository.getLocalPath(ModelAsset.VOSK_STT)
                if (path == null) {
                    Log.w(TAG, "Ruta del modelo Vosk no disponible tras descarga")
                    return false
                }

                val voskModel = createVoskModel(path)
                if (voskModel == null) {
                    Log.e(TAG, "No se pudo crear el modelo Vosk desde: $path")
                    return false
                }

                val newRecognizer = createRecognizer(voskModel)
                if (newRecognizer == null) {
                    Log.e(TAG, "No se pudo crear el reconocedor Vosk")
                    return false
                }

                model = voskModel
                recognizer = newRecognizer
                isModelReady = true
                Log.i(TAG, "Vosk STT listo — modelo cargado desde: $path")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando modelo Vosk, STT deshabilitado", e)
                isModelReady = false
                return false
            }
        }
    }

    /**
     * Crea un modelo Vosk desde la ruta local.
     * Extraído como `internal open` para permitir mocking en tests JVM
     * (la librería nativa de Vosk no está disponible en el classpath de tests).
     */
    internal open fun createVoskModel(path: String): Model? {
        return try {
            Model(path)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Vosk Model from: $path", e)
            null
        }
    }

    /**
     * Crea un Recognizer Vosk desde un modelo.
     * Extraído como `internal open` para permitir mocking en tests JVM.
     */
    internal open fun createRecognizer(model: Model): Recognizer? {
        return try {
            Recognizer(model, 16000.0f)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create Vosk Recognizer", e)
            null
        }
    }

    /**
     * Crea un SpeechService Vosk desde un Recognizer.
     * Extraído como `internal open` para permitir mocking en tests JVM.
     */
    internal open fun createSpeechService(recognizer: Recognizer): SpeechService? {
        return try {
            SpeechService(recognizer, 16000.0f)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SpeechService", e)
            null
        }
    }

    override fun startListening() {
        scope.launch {
            if (!ensureModelLoaded()) {
                Log.w(TAG, "Model not available, STT disabled")
                onErrorCallback("Reconocimiento de voz no disponible")
                return@launch
            }

            val currentRecognizer = recognizer
            if (currentRecognizer == null) {
                onErrorCallback("Reconocedor de voz no disponible")
                return@launch
            }

            try {
                if (speechService != null) {
                    speechService?.stop()
                    speechService = null
                }
                Log.d(TAG, "Preparing AudioRecord for Vosk...")
                speechService = createSpeechService(currentRecognizer)
                if (speechService == null) {
                    onErrorCallback("No se pudo crear el servicio de reconocimiento de voz")
                    return@launch
                }
                val success = speechService?.startListening(this@VoskSpeechToTextManager)
                Log.d(TAG, "Started listening: success=$success")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech service", e)
                onErrorCallback("No se pudo iniciar el servicio de voz: ${e.message}")
            }
        }
    }

    override fun stopListening() {
        speechService?.stop()
        speechService = null
        Log.d(TAG, "Stopped listening")
    }

    override fun destroy() {
        scope.cancel()

        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "SpeechService failed to shutdown", e)
        } finally {
            speechService = null
        }

        try {
            recognizer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Recognizer already closed or failed to close", e)
        } finally {
            recognizer = null
        }

        isModelReady = false
        Log.d(TAG, "Destroyed")
    }

    // --- RecognitionListener (Vosk) ---

    override fun onResult(hypothesis: String) {
        val text = parseHypothesis(hypothesis)
        if (text.isNotBlank()) {
            onResultCallback(text)
        }
    }

    override fun onPartialResult(hypothesis: String) {
        val text = parsePartialHypothesis(hypothesis)
        if (text.isNotEmpty()) {
            onPartialResultCallback(text)
        }
    }

    override fun onFinalResult(hypothesis: String) {
        val text = parseHypothesis(hypothesis)
        if (text.isNotBlank()) {
            Log.d(TAG, "Final result (onFinalResult): $text")
            onResultCallback(text)
        }
    }

    override fun onError(exception: Exception) {
        Log.e(TAG, "Vosk native error", exception)
        onErrorCallback("Error en el motor de voz offline")
    }

    override fun onTimeout() {
        Log.d(TAG, "Timeout")
    }

    private fun parseHypothesis(json: String): String {
        return try {
            JSONObject(json).optString("text", "")
        } catch (_: Exception) {
            ""
        }
    }

    private fun parsePartialHypothesis(json: String): String {
        return try {
            JSONObject(json).optString("partial", "")
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val TAG = "VoskSTT"
    }
}
