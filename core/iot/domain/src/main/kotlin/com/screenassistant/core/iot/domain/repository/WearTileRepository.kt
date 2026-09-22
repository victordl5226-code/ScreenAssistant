package com.screenassistant.core.iot.domain.repository

import com.screenassistant.core.iot.domain.model.wearables.WearTileState
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gestión de Tiles de Wear OS.
 *
 * Los Tiles son superficies de acceso rápido que se muestran en el carrusel
 * de Wear OS. Este repositorio gestiona el estado y actualización de los
 * Tiles de ScreenAssistant.
 */
interface WearTileRepository {
    /**
     * IDs de los Tiles registrados por la app.
     */
    val registeredTileIds: List<String>

    /**
     * Obtiene el estado actual de un Tile.
     */
    fun getTileState(tileId: String): Flow<WearTileState>

    /**
     * Actualiza el estado de un Tile (llamado desde el servicio TileService).
     */
    suspend fun updateTileState(state: WearTileState): Boolean

    /**
     * Invalida un Tile para forzar refresco (llama a onTileRequest).
     */
    suspend fun invalidateTile(tileId: String): Boolean

    /**
     * Invalida todos los Tiles de la app.
     */
    suspend fun invalidateAllTiles(): Boolean

    /**
     * Registra un nuevo Tile dinámicamente.
     * Nota: En Wear OS, los Tiles se declaran en manifest, pero este método
     * permite gestionar estado de Tiles condicionales.
     */
    suspend fun registerTile(tileId: String, initialState: WearTileState): Boolean

    /**
     * Desregistra un Tile.
     */
    suspend fun unregisterTile(tileId: String): Boolean

    /**
     * Maneja acción de toque en un Tile.
     */
    suspend fun handleTileTap(tileId: String, action: String): Boolean

    /**
     * Obtiene recursos necesarios para renderizado (iconos, imágenes).
     */
    suspend fun getTileResources(tileId: String): Map<String, ByteArray>
}

/**
 * Contrato para el servicio de Tiles (implementación en módulo Wear).
 * Esta interfaz define qué debe implementar el TileService de Wear OS.
 */
interface WearTileServiceContract {
    /**
     * Invocado cuando el sistema solicita el contenido del Tile.
     */
    suspend fun onTileRequest(tileId: String, requestParams: Map<String, Any>): WearTileState

    /**
     * Invocado cuando el usuario toca una acción en el Tile.
     */
    suspend fun onTileAction(tileId: String, action: String, params: Map<String, Any>)

    /**
     * Invocado cuando el Tile entra/sale del modo ambiente.
     */
    suspend fun onAmbientModeChanged(tileId: String, isAmbient: Boolean)

    /**
     * Recursos disponibles para el Tile.
     */
    fun getResources(tileId: String): Map<String, ByteArray>
}