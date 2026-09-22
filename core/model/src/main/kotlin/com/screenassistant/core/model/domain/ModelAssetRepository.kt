package com.screenassistant.core.model.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato del repositorio de modelos descargables.
 *
 * Permite observar el estado de cada modelo, descargarlos bajo demanda,
 * verificar si están listos y eliminarlos para liberar espacio.
 */
interface ModelAssetRepository {

    /** Flujo reactivo del estado de un modelo. */
    fun observeStatus(model: ModelAsset): StateFlow<ModelAssetStatus>

    /** Verifica si un modelo está descargado y listo para usar. */
    suspend fun isReady(model: ModelAsset): Boolean

    /** Descarga el modelo si no está ya descargado. Actualiza [observeStatus]. */
    suspend fun download(model: ModelAsset)

    /** Retorna la ruta local del modelo listo, o null si no está disponible. */
    suspend fun getLocalPath(model: ModelAsset): String?

    /** Elimina el modelo del almacenamiento local. */
    suspend fun delete(model: ModelAsset): Result<Unit>

    /** Lista todos los modelos disponibles para descargar. */
    fun availableModels(): List<ModelAsset>

    /** Tamaño en bytes reportado por el manifiesto del modelo. */
    suspend fun getModelSizeBytes(model: ModelAsset): Long

    /** Espacio estimado necesario (archivo × 1.2 para descompresión). */
    suspend fun getRequiredSpaceBytes(model: ModelAsset): Long
}
