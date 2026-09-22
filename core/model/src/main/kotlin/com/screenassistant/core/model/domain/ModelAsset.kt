package com.screenassistant.core.model.domain

/**
 * Catálogo de modelos descargables que el asistente puede usar.
 *
 * Cada modelo se descarga bajo demanda desde GitHub Releases y se almacena
 * localmente en [com.screenassistant.core.model.data.ModelFileStore].
 */
enum class ModelAsset(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long
) {
    PIPER_TTS(
        id = "piper_tts",
        displayName = "Piper TTS (Voz)",
        fileName = "voice.onnx",
        downloadUrl = "https://github.com/victordl5226-code/ScreenAssistant/releases/download/v1.0/voice.onnx",
        sha256 = "6658B03B1A6C316EE4C265A9896ABC1393353C2D9E1BCA7D66C2C442E222A917",
        sizeBytes = 60_270_000L
    ),
    VOSK_STT(
        id = "vosk_stt",
        displayName = "Vosk STT (Reconocimiento)",
        fileName = "vosk-model-es.tar.gz",
        downloadUrl = "https://github.com/victordl5226-code/ScreenAssistant/releases/download/v1.0/vosk-model-es.tar.gz",
        sha256 = "032106C840E1D16F954A806BE47BCC555E64592879255ECF109DFBB2817A171C",
        sizeBytes = 37_960_000L
    ),
    MINILM_EMBEDDINGS(
        id = "minilm_embeddings",
        displayName = "MiniLM (Embeddings)",
        fileName = "all-minilm-l6-v2.tar.gz",
        downloadUrl = "https://github.com/victordl5226-code/ScreenAssistant/releases/download/v1.0/all-minilm-l6-v2.tar.gz",
        sha256 = "55976038C6ED2D3DCE5D52BE50EAB9BA8D32095549544D382A5C77AC84886D2F",
        sizeBytes = 16_840_000L
    )
}
