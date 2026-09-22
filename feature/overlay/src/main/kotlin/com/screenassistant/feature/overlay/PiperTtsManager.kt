package com.screenassistant.feature.overlay

import android.content.Context
import android.util.Log
import com.screenassistant.core.domain.service.TextToSpeech
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

/**
 * Gestor de TTS Local usando Piper (ONNX Runtime).
 *
 * El modelo se descarga bajo demanda desde GitHub Releases a través de
 * [ModelAssetRepository]. La carga del modelo ONNX es **lazy**: se realiza
 * solo en la primera llamada a [speak], no en la construcción.
 *
 * Si la descarga o carga del modelo falla, se usa automáticamente
 * [TextToSpeechManager] del sistema como fallback — J.A.R.V.I.S. nunca
 * se queda en silencio.
 *
 * Los tipos de ONNX Runtime ([ai.onnxruntime.OrtEnvironment],
 * [ai.onnxruntime.OrtSession]) se manejan como [Any?] internamente para
 * evitar que la librería nativa se cargue en tests JVM unitarios.
 * El cast se realiza solo en la ruta de inferencia (ver [createOrtSession]).
 *
 * @param context Contexto de aplicación.
 * @param onStart Callback invocado cuando el motor de voz comienza a hablar.
 * @param onDone Callback invocado cuando el motor de voz termina de hablar.
 * @param modelRepository Repositorio de modelos descargables (inyectado).
 */
open class PiperTtsManager(
    private val context: Context,
    private val onStart: () -> Unit = {},
    private val onDone: () -> Unit = {},
    private val modelRepository: ModelAssetRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : TextToSpeech {

    private val systemTts = TextToSpeechManager(context, onStart, onDone)

    /** Sesión ONNX cargada (Any? para evitar carga de la librería nativa en tests). */
    private var ortSession: Any? = null
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    /** Mutex para serializar la inicialización del modelo (evita descargas duplicadas). */
    private val initMutex = Mutex()
    private var isPiperReady = false

    /**
     * Garantiza que el modelo Piper esté cargado y listo para usar.
     *
     * - Si ya está cargado, retorna `true` inmediatamente.
     * - Si no está descargado, lo descarga a través de [modelRepository].
     * - Si la descarga o carga del ONNX falla, retorna `false` (fallback al sistema).
     *
     * THREAD-SAFE: el Mutex serializa concurrentes que llamen a `speak()` simultáneamente.
     */
    private suspend fun ensureModelLoaded(): Boolean {
        if (isPiperReady && ortSession != null) return true

        initMutex.withLock {
            // Doble chequeo tras adquirir el mutex (otro hilo pudo haberlo cargado).
            if (isPiperReady && ortSession != null) return true

            try {
                if (!modelRepository.isReady(ModelAsset.PIPER_TTS)) {
                    Log.d(TAG, "Modelo Piper no descargado, iniciando descarga...")
                    modelRepository.download(ModelAsset.PIPER_TTS)
                }

                val path = modelRepository.getLocalPath(ModelAsset.PIPER_TTS)
                if (path == null) {
                    Log.w(TAG, "Ruta del modelo Piper no disponible tras descarga")
                    return false
                }

                val session = createOrtSession(path)
                if (session == null) {
                    Log.e(TAG, "No se pudo crear la sesión ONNX desde: $path")
                    return false
                }
                ortSession = session
                isPiperReady = true
                Log.i(TAG, "Piper TTS listo (ONNX) — modelo cargado desde: $path")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando modelo Piper, fallback al sistema", e)
                isPiperReady = false
                return false
            }
        }
    }

    override fun speak(text: String) {
        if (text.isBlank()) return

        scope.launch {
            if (ensureModelLoaded()) {
                Log.d(TAG, "Piper habla (ONNX): $text")
                // TODO(Porcion 3): inferencia ONNX con ortSession para generar audio.
                // Por ahora, usamos el sistema como canal de audio incluso con Piper listo.
                systemTts.speak(text)
            } else {
                Log.d(TAG, "[SISTEMA] JARVIS habla (fallback): $text")
                systemTts.speak(text)
            }
        }
    }

    override fun stop() {
        systemTts.stop()
    }

    override fun destroy() {
        scope.cancel()

        // Guard contra doble cierre de sesión (Fix IllegalStateException)
        try {
            closeOrtSession()
        } catch (e: Exception) {
            Log.w(TAG, "OrtSession already closed or failed to close", e)
        } finally {
            ortSession = null
        }

        isPiperReady = false
        systemTts.destroy()
    }

    /**
     * Cierra la sesión ONNX de forma segura.
     * Usa reflexión para evitar dependencia directa de la clase OrtSession
     * en el classpath (la librería nativa no está disponible en JVM tests).
     */
    private fun closeOrtSession() {
        val session = ortSession ?: return
        try {
            // session.close() vía reflexión — evita importar OrtSession
            val closeMethod = session.javaClass.getMethod("close")
            closeMethod.invoke(session)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close ONNX session via reflection", e)
        }
    }

    /**
     * Crea una sesión ONNX Runtime desde la ruta del modelo.
     * Extraído como `internal open` para permitir mocking en tests JVM
     * (la librería nativa de ORT no está disponible en el classpath de tests).
     *
     * Retorna [Any?] para no exponer tipos de ORT en la interfaz pública.
     */
    internal open fun createOrtSession(path: String): Any? {
        // Importación dinámica para evitar que la clase se cargue en tests
        val ortEnvClass = Class.forName("ai.onnxruntime.OrtEnvironment")
        val getEnvMethod = ortEnvClass.getMethod("getEnvironment")
        val env = getEnvMethod.invoke(null)

        val ortSessionClass = Class.forName("ai.onnxruntime.OrtSession")
        val createSessionMethod = ortEnvClass.getMethod("createSession", String::class.java)
        return createSessionMethod.invoke(env, path)
    }

    companion object {
        private const val TAG = "PiperTTS"
    }
}
