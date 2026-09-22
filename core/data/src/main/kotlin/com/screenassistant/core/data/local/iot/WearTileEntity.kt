package com.screenassistant.core.data.local.iot

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Entidad Room para estado de Tiles de Wear OS.
 *
 * Persiste el último estado conocido de cada Tile para restauración
 * rápida al reiniciar la app o sincronizar con Wear OS.
 * Los timestamps se almacenan como Long (epoch millis).
 * La conversión a/from domain se hace en el repository (core:iot:data).
 */
@Entity(
    tableName = "wear_tiles",
    indices = [
        Index(value = ["tileId"], name = "index_wear_tiles_tileId", unique = true),
        Index(value = ["lastUpdated"], name = "index_wear_tiles_updated"),
    ]
)
@Serializable
data class WearTileEntity(
    @PrimaryKey val tileId: String,
    val stateJson: String, // Serialized WearTileState
    val lastUpdated: Long = System.currentTimeMillis(), // epoch millis
)