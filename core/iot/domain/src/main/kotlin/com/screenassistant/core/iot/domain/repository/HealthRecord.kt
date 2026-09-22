package com.screenassistant.core.iot.domain.repository

import kotlinx.serialization.Serializable

/**
 * Registro de salud genérico para escritura.
 */
sealed class HealthRecord {
    @Serializable
    data class Steps(
        val count: Long,
        val startTime: Long,
        val endTime: Long,
        val dataSource: String,
    ) : HealthRecord()

    @Serializable
    data class HeartRate(
        val bpm: Int,
        val time: Long,
        val dataSource: String,
    ) : HealthRecord()

    @Serializable
    data class Weight(
        val kg: Double,
        val time: Long,
        val dataSource: String,
    ) : HealthRecord()

    @Serializable
    data class SleepSession(
        val startTime: Long,
        val endTime: Long,
        val stages: List<SleepStageRecord>,
        val dataSource: String,
    ) : HealthRecord()

    @Serializable
    data class BloodOxygen(
        val percentage: Int,
        val time: Long,
        val dataSource: String,
    ) : HealthRecord()

    @Serializable
    data class ExerciseSession(
        val exerciseType: Int,
        val startTime: Long,
        val endTime: Long,
        val calories: Double?,
        val distanceMeters: Double?,
        val dataSource: String,
    ) : HealthRecord()

    /** Etapa de sueño para SleepSession */
    @Serializable
    data class SleepStageRecord(
        val stage: Int, // 1=awake, 2=light, 3=deep, 4=rem
        val startTime: Long,
        val endTime: Long,
    )
}