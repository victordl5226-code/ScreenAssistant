package com.screenassistant.core.data.local.iot

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Entidad Room para escenas Matter.
 *
 * Almacena escenas creadas por el usuario para acceso offline.
 * Los dispositivos y estados se serializan como JSON.
 * Los timestamps se almacenan como Long (epoch millis).
 */
@Entity(
    tableName = "matter_scenes",
    indices = [
        Index(value = ["name"], name = "index_matter_scenes_name"),
    ]
)
@Serializable
data class MatterSceneEntity(
    @PrimaryKey val sceneId: String,
    val name: String,
    val icon: String,
    val devicesJson: String, // Serialized List<SceneDeviceState>
    val createdAt: Long, // epoch millis
    val updatedAt: Long, // epoch millis
)
