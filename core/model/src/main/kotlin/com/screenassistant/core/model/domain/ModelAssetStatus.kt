package com.screenassistant.core.model.domain

/**
 * Estado de un [ModelAsset] en su ciclo de vida de descarga.
 */
sealed class ModelAssetStatus {
    /** El modelo no ha sido descargado. */
    data object NotDownloaded : ModelAssetStatus()

    /** El modelo se está descargando. [progress] va de 0f a 1f. */
    data class Downloading(val progress: Float) : ModelAssetStatus()

    /** El archivo se verificó (SHA-256) y está siendo descomprimido/preparado. */
    data object Verifying : ModelAssetStatus()

    /** El modelo está listo para usar en [localPath]. */
    data class Ready(val localPath: String) : ModelAssetStatus()

    /** La descarga o verificación falló con [error]. */
    data class Failed(val error: Throwable) : ModelAssetStatus()
}
