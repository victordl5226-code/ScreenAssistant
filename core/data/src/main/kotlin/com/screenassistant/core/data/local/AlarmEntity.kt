package com.screenassistant.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Alarma programada in-app con AlarmManager (O4-P2).
 * PK = hour*60+minute (0..1439): una alarma por franja horaria.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val requestCode: Int,
    val hour: Int,
    val minute: Int,
    val label: String,
    val triggerAtMillis: Long,
    val createdAt: Long
)
