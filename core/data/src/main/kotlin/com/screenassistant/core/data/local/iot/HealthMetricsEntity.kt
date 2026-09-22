package com.screenassistant.core.data.local.iot

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Entidad Room para métricas de salud agregadas.
 *
 * Almacena snapshots periódicos de métricas de salud para histórico
 * y análisis de tendencias. Los datos complejos se serializan como JSON.
 * Los timestamps se almacenan como Long (epoch millis).
 * La conversión a/from domain se hace en el repository (core:iot:data).
 */
@Entity(
    tableName = "health_metrics",
    indices = [
        Index(value = ["timestamp"], name = "index_health_metrics_timestamp"),
        Index(value = ["source"], name = "index_health_metrics_source"),
    ]
)
@Serializable
data class HealthMetricsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, // epoch millis
    val metricsJson: String, // Serialized HealthMetrics
    val source: String, // DataSource.name
    val syncedAt: Long = System.currentTimeMillis(), // epoch millis
)