package com.screenassistant.core.ai.local.llama

import com.screenassistant.core.ai.local.llama.bridge.LlamaCppBridgeInterface
import com.screenassistant.core.ai.local.llama.config.LlamaCppConfig
import com.screenassistant.core.domain.repository.ai.AiProvider
import com.screenassistant.core.domain.repository.ai.AiResponse
import com.screenassistant.core.domain.repository.ai.LocalInferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Motor de inferencia local basado en llama.cpp.
 *
 * Implementa LocalInferenceEngine de core/domain.
 * Usa LlamaCppBridge para la comunicación JNI con la librería nativa.
 *
 * Características:
 * - Inference síncrona y asíncrona
 * - Streaming token a token via callbackFlow
 * - Manejo de OOM automático (libera recursos y retorna error recoverable)
 * - Thread-safe con volatile handles
 *
 * @param bridge Bridge JNI para llama.cpp
 * @param config Configuración del motor
 */
class LlamaCppEngine(
    private val bridge: LlamaCppBridgeInterface,
    private val config: LlamaCppConfig,
) : LocalInferenceEngine {

    @Volatile private var contextHandle: Long = 0L
    @Volatile private var modelHandle: Long = 0L
    @Volatile private var ready = false

    /**
     * Inicializa el motor con un modelo específico.
     * Incluye fallback automático de n_ctx si hay OOM (dispositivos con poca RAM).
     *
     * @param modelPath Ruta al archivo .gguf
     * @return true si la inicialización fue exitosa
     */
    override suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (ready) return@withContext true

            // Secuencia de fallback n_ctx para dispositivos con poca RAM
            val fallbackCtxSizes = listOf(config.contextSize, 1024, 512)

            for (ctxSize in fallbackCtxSizes) {
                val params = config.toInitParams().copy(nCtx = ctxSize)
                contextHandle = bridge.nativeInit(params)
                if (contextHandle == 0L) {
                    android.util.Log.w(TAG, "nativeInit falló con nCtx=$ctxSize, probando fallback...")
                    continue
                }

                modelHandle = bridge.nativeLoadModel(contextHandle, modelPath, config.numThreads)
                if (modelHandle == 0L) {
                    android.util.Log.w(TAG, "nativeLoadModel falló con nCtx=$ctxSize (posible OOM), probando fallback...")
                    bridge.nativeDestroy(contextHandle)
                    contextHandle = 0L
                    continue
                }

                ready = bridge.nativeIsReady(contextHandle)
                if (!ready) {
                    android.util.Log.w(TAG, "Contexto no ready con nCtx=$ctxSize, probando fallback...")
                    release()
                    continue
                }

                android.util.Log.i(TAG, "Motor llama.cpp inicializado: $modelPath (nCtx=$ctxSize)")
                return@withContext true
            }

            android.util.Log.e(TAG, "Todos los fallbacks de nCtx fallaron")
            false
        } catch (e: OutOfMemoryError) {
            android.util.Log.e(TAG, "OOM al inicializar motor", e)
            release()
            false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error inesperado al inicializar", e)
            release()
            false
        }
    }

    /**
     * Ejecuta inferencia con el modelo cargado.
     *
     * @param prompt Prompt de entrada
     * @param maxTokens Máximo de tokens a generar
     * @param temperature Temperatura de muestreo
     * @return Texto generado o error
     */
    override suspend fun inference(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
    ): AiResponse = withContext(Dispatchers.IO) {
        if (!ready) {
            return@withContext AiResponse.Error(
                reason = "Modelo no cargado",
                provider = AiProvider.LOCAL,
                recoverable = true,
            )
        }

        try {
            val start = System.currentTimeMillis()
            
            val result = bridge.nativeGenerate(
                handle = contextHandle,
                modelHandle = modelHandle,
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = config.topP,
                topK = config.topK,
            )
            val latency = System.currentTimeMillis() - start

            AiResponse.Success(
                text = result,
                provider = AiProvider.LOCAL,
                latencyMs = latency,
            )
        } catch (e: OutOfMemoryError) {
            android.util.Log.e(TAG, "OOM durante inferencia", e)
            release()
            AiResponse.Error(
                reason = "Memoria insuficiente: ${e.message}",
                provider = AiProvider.LOCAL,
                recoverable = true,
            )
        } catch (e: Exception) {
            AiResponse.Error(
                reason = "Error en inferencia: ${e.message}",
                provider = AiProvider.LOCAL,
                recoverable = true,
            )
        }
    }

    /**
     * Streaming de inferencia — genera tokens uno a uno.
     *
     * @param prompt Prompt de entrada
     * @param maxTokens Máximo de tokens a generar
     * @param temperature Temperatura de muestreo
     * @return Flow de tokens generados
     */
    override fun streamInference(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
    ): Flow<String> = callbackFlow {
        if (!ready) {
            trySend("")
            close()
            return@callbackFlow
        }

        try {
            bridge.nativeStreamGenerate(
                handle = contextHandle,
                modelHandle = modelHandle,
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = config.topP,
                topK = config.topK,
                onToken = { token ->
                    trySend(token)
                },
                onDone = {
                    close()
                },
                onError = { error ->
                    trySend("")
                    close(Exception(error))
                },
            )
        } catch (e: OutOfMemoryError) {
            android.util.Log.e(TAG, "OOM durante streaming", e)
            release()
            close(e)
        } catch (e: Exception) {
            close(e)
        }

        awaitClose { /* Cleanup si es necesario */ }
    }.flowOn(Dispatchers.IO)

    /**
     * Verifica si el motor está listo para inferencia.
     */
    override fun isReady(): Boolean = ready

    /**
     * Libera recursos del motor.
     */
    override suspend fun release() {
        withContext(Dispatchers.IO) {
            try {
                if (modelHandle != 0L) {
                    bridge.nativeFreeModel(contextHandle, modelHandle)
                    modelHandle = 0L
                }
                if (contextHandle != 0L) {
                    bridge.nativeDestroy(contextHandle)
                    contextHandle = 0L
                }
                ready = false
                android.util.Log.i(TAG, "Recursos del motor liberados")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error al liberar recursos", e)
                ready = false
            }
        }
    }

    companion object {
        private const val TAG = "LlamaCppEngine"
    }
}
