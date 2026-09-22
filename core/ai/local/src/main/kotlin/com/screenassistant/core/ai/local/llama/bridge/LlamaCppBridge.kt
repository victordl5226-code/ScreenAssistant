package com.screenassistant.core.ai.local.llama.bridge

import android.util.Log
import com.screenassistant.core.ai.local.llama.config.LlamaInitParams

/**
 * Bridge JNI para llama.cpp.
 *
 * Implementación concreta de [LlamaCppBridgeInterface].
 * Contiene las declaraciones `external fun` que conectan
 * con la implementación nativa en C++ (libllama_jni.so).
 *
 * La implementación nativa está en `src/main/cpp/` y se compila
 * via CMake o se incluye como .so pre-compilado en jniLibs/.
 */
class LlamaCppBridge : LlamaCppBridgeInterface {

    companion object {
        init {
            try {
                System.loadLibrary("llama_jni")
                Log.i("LlamaCppBridge", "Librería nativa llama_jni cargada con éxito")
            } catch (e: UnsatisfiedLinkError) {
                // Si el .so no está disponible, el bridge no funcionará
                // pero la app no crasheará — solo retornará errores
                Log.e("LlamaCppBridge", "No se pudo cargar libllama_jni.so: ${e.message}")
            } catch (e: Exception) {
                Log.e("LlamaCppBridge", "Error al cargar librería nativa: ${e.message}")
            }
        }
    }

    /**
     * Inicializa el contexto de llama.cpp.
     *
     * @param params Parámetros de inicialización
     * @return Handle del contexto (0 si falló)
     */
    override external fun nativeInit(params: LlamaInitParams): Long

    /**
     * Libera el contexto de llama.cpp.
     *
     * @param handle Handle del contexto
     */
    override external fun nativeDestroy(handle: Long)

    /**
     * Carga un modelo GGUF desde disco.
     *
     * @param handle Handle del contexto
     * @param modelPath Ruta al archivo .gguf
     * @param nThreads Número de hilos para inferencia
     * @return Handle del modelo (0 si falló)
     */
    override external fun nativeLoadModel(handle: Long, modelPath: String, nThreads: Int): Long

    /**
     * Libera el modelo de memoria.
     *
     * @param handle Handle del contexto
     * @param modelHandle Handle del modelo
     */
    override external fun nativeFreeModel(handle: Long, modelHandle: Long)

    /**
     * Genera texto de forma síncrona.
     *
     * @param handle Handle del contexto
     * @param modelHandle Handle del modelo
     * @param prompt Prompt de entrada
     * @param maxTokens Máximo de tokens a generar
     * @param temperature Temperatura de muestreo
     * @param topP Top-p sampling
     * @param topK Top-k sampling
     * @return Texto generado
     */
    override external fun nativeGenerate(
        handle: Long,
        modelHandle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
    ): String

    /**
     * Genera texto con streaming (token a token).
     *
     * @param handle Handle del contexto
     * @param modelHandle Handle del modelo
     * @param prompt Prompt de entrada
     * @param maxTokens Máximo de tokens a generar
     * @param temperature Temperatura de muestreo
     * @param topP Top-p sampling
     * @param topK Top-k sampling
     * @param onToken Callback por cada token generado
     * @param onDone Callback cuando termina la generación
     * @param onError Callback si hay error
     */
    override external fun nativeStreamGenerate(
        handle: Long,
        modelHandle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    )

    /**
     * Verifica si el contexto está listo para inferencia.
     *
     * @param handle Handle del contexto
     * @return true si está listo
     */
    override external fun nativeIsReady(handle: Long): Boolean

    /**
     * Cuenta tokens en un texto.
     *
     * @param handle Handle del contexto
     * @param text Texto a contar
     * @return Número de tokens
     */
    override external fun nativeTokenCount(handle: Long, text: String): Int
}
