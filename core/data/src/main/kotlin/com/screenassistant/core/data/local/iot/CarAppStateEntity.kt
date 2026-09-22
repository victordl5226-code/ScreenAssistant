package com.screenassistant.core.data.local.iot

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Entidad Room para estado de la app en el coche.
 *
 * Persiste el último estado conocido para restauración rápida
 * y análisis de patrones de uso automotriz.
 * Los timestamps se almacenan como Long (epoch millis).
 * La conversión a/from domain se hace en el repository (core:iot:data).
 */
@Entity(
    tableName = "car_app_states",
    indices = [
        Index(value = ["timestamp"], name = "index_car_app_states_timestamp"),
        Index(value = ["connectionType"], name = "index_car_app_states_connection"),
    ]
)
@Serializable
data class CarAppStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, // epoch millis
    val stateJson: String, // Serialized CarAppState
    val connectionType: String, // ConnectionType.name
    val carModel: String?,
    val isDriving: Boolean,
    val speedKmh: Double,
)