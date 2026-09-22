package com.screenassistant.core.ai.local.llama.bridge

import com.screenassistant.core.ai.local.llama.config.LlamaInitParams

/**
 * Interfaz del bridge JNI para llama.cpp.
 *
 * Permite testing con MockK sin depender del .so nativo.
 * La implementación concreta (LlamaCppBridge) contiene las
 * declaraciones `external fun` que conectan con C++.
 */
interface LlamaCppBridgeInterface {

    fun nativeInit(params: LlamaInitParams): Long
    fun nativeDestroy(handle: Long)
    fun nativeLoadModel(handle: Long, modelPath: String, nThreads: Int): Long
    fun nativeFreeModel(handle: Long, modelHandle: Long)
    fun nativeGenerate(
        handle: Long,
        modelHandle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
    ): String
    fun nativeStreamGenerate(
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
    fun nativeIsReady(handle: Long): Boolean
    fun nativeTokenCount(handle: Long, text: String): Int
}
