package com.screenassistant.core.ai.local.llama.config

/**
 * Parámetros de inicialización de llama.cpp.
 *
 * Controla el comportamiento del motor de inferencia local.
 * Valores por defecto optimizados para dispositivos móviles.
 *
 * @property nCtx Tamaño del contexto (tokens). Mayor = más memoria, más contexto.
 * @property nBatch Tamaño del batch para procesamiento paralelo.
 * @property nThreads Número de hilos CPU. recomendado: 2-8 según dispositivo.
 * @property useMmap Usa memory-mapped I/O para cargar el modelo (más eficiente).
 * @property useMlock Bloca el modelo en memoria (previene swap, usa más RAM).
 * @property verbose Logging detallado del motor (para debugging).
 */
data class LlamaInitParams(
    val nCtx: Int = 2048,
    val nBatch: Int = 512,
    val nThreads: Int = 4,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false,
    val verbose: Boolean = false,
) {
    companion object {
        /** Parámetros para TinyLlama 1.1B en dispositivos móviles */
        val TINY_LLAMA_MOBILE = LlamaInitParams(
            nCtx = 2048,
            nBatch = 512,
            nThreads = 4,
            useMmap = true,
            useMlock = false,
            verbose = false,
        )

        /** Parámetros para dispositivos con más RAM (tablets, emuladores) */
        val TINY_LLAMA_TABLET = LlamaInitParams(
            nCtx = 4096,
            nBatch = 1024,
            nThreads = 6,
            useMmap = true,
            useMlock = false,
            verbose = false,
        )
    }
}
