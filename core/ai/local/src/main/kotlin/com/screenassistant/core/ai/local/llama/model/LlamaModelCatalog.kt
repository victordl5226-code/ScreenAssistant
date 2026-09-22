package com.screenassistant.core.ai.local.llama.model

import com.screenassistant.core.domain.repository.ai.ModelInfo

/**
 * Catálogo de modelos GGUF disponibles para llama.cpp.
 *
 * Contiene los modelos predefinidos con sus URLs de descarga.
 * Usa ModelInfo existente de core/domain para compatibilidad.
 */
object LlamaModelCatalog {

    /** TinyLlama 1.1B Chat v1.0 — Q4_K_M (~637MB) */
    val TINY_LLAMA_Q4 = ModelInfo(
        id = "tinyllama-1.1b-chat-v1.0-q4_k_m",
        name = "TinyLlama 1.1B Chat",
        version = "1.0",
        sizeBytes = 668_000_000L, // ~637MB
        quantization = "Q4_K_M",
        description = "Modelo ligero para dispositivos móviles. Buen equilibrio calidad/velocidad.",
        downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
    )

    /** TinyLlama 1.1B Chat v1.0 — Q8_0 (~1.1GB) */
    val TINY_LLAMA_Q8 = ModelInfo(
        id = "tinyllama-1.1b-chat-v1.0-q8_0",
        name = "TinyLlama 1.1B Chat (Alta calidad)",
        version = "1.0",
        sizeBytes = 1_150_000_000L, // ~1.1GB
        quantization = "Q8_0",
        description = "Mayor calidad, requiere más memoria. Para tablets o emuladores.",
        downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q8_0.gguf",
    )

    /** Lista de todos los modelos disponibles */
    val ALL: List<ModelInfo> = listOf(TINY_LLAMA_Q4, TINY_LLAMA_Q8)

    /** Modelo por defecto (Q4 para móviles) */
    val DEFAULT: ModelInfo = TINY_LLAMA_Q4

    /**
     * Busca un modelo por su ID.
     *
     * @param modelId Identificador del modelo
     * @return ModelInfo o null si no existe
     */
    fun findById(modelId: String): ModelInfo? {
        return ALL.find { it.id == modelId }
    }
}
