package com.screenassistant.core.domain.model

/**
 * Información del modelo local MLC con estado de ciclo de vida.
 */
data class MlcModelInfo(
    val id: String,
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val quantization: String,
    val description: String,
    val downloadUrl: String,
    val localPath: String? = null,
    val state: ModelState = ModelState.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null,
) {
    companion object {
        val DEFAULT = MlcModelInfo(
            id = "phi-3-mini-3.8b-q4",
            name = "Phi-3 Mini 3.8B Q4",
            version = "3.8B",
            sizeBytes = 2_300_000_000L,
            quantization = "Q4_K_M",
            description = "Phi-3 Mini 3.8B, cuantización Q4_K_M. Rápido en dispositivos con 6GB+ RAM.",
            downloadUrl = "https://huggingface.co/mlc-ai/Phi-3-mini-4k-instruct-q4f16_1-MLC/resolve/main/Phi-3-mini-4k-instruct-q4f16_1-MLC.zip",
        )
    }
}

enum class ModelState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    LOADING,
    READY,
    ERROR,
}
