package com.screenassistant.core.domain.repository.ai

/**
 * Gestor del ciclo de vida del modelo local.
 *
 * Implementado por MLC LLM en `core:ai:local`.
 * Maneja descarga, carga, descarga y verificación de espacio en disco.
 */
interface ModelManager {

    /**
     * Verifica si el modelo está descargado.
     */
    fun isModelDownloaded(): Boolean

    /**
     * Obtiene el tamaño del modelo en bytes.
     */
    fun getModelSizeBytes(): Long

    /**
     * Obtiene el espacio libre necesario para descargar el modelo.
     */
    fun getRequiredSpaceBytes(): Long

    /**
     * Descarga el modelo con progreso.
     *
     * @param progressCallback Callback con progreso (0.0 a 1.0)
     * @return Resultado de la descarga
     */
    suspend fun downloadModel(
        progressCallback: (Float) -> Unit = {}
    ): Result<Unit>

    /**
     * Carga el modelo en memoria.
     *
     * @return true si la carga fue exitosa
     */
    suspend fun loadModel(): Boolean

    /**
     * Descarga el modelo de memoria (libera RAM).
     */
    suspend fun unloadModel()

    /**
     * Elimina el archivo del modelo del disco.
     *
     * @return true si la eliminación fue exitosa
     */
    suspend fun deleteModel(): Boolean

    /**
     * Obtiene información del modelo disponible.
     */
    fun getModelInfo(): ModelInfo?

    /**
     * Lista de modelos disponibles para descargar.
     */
    val availableModels: List<ModelInfo>
}

/**
 * Información de un modelo.
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val quantization: String,
    val description: String,
    val downloadUrl: String,
)
