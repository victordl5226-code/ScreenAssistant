package com.screenassistant.core.data.local.iot

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Entidad Room para dispositivos Matter.
 *
 * Almacena dispositivos descubiertos para acceso offline y sincronización.
 * Los atributos complejos se serializan como JSON.
 * Los timestamps se almacenan como Long (epoch millis).
 * La conversión a/from domain se hace en el repository (core:iot:data).
 */
@Entity(
    tableName = "matter_devices",
    indices = [
        Index(value = ["room"], name = "index_matter_devices_room"),
        Index(value = ["type"], name = "index_matter_devices_type"),
        Index(value = ["isOnline"], name = "index_matter_devices_online"),
    ]
)
@Serializable
data class MatterDeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val type: String, // MatterDeviceType.name
    val room: String,
    val isOnline: Boolean,
    val attributesJson: String, // Serialized Map<String, MatterAttribute>
    val lastSeen: Long, // epoch millis
    val firmwareVersion: String,
    val manufacturer: String,
    val model: String,
    val serialNumber: String,
    val updatedAt: Long = System.currentTimeMillis(), // epoch millis
)