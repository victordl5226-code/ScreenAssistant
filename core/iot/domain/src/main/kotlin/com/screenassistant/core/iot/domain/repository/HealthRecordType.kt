package com.screenassistant.core.iot.domain.repository

import kotlinx.serialization.Serializable

/**
 * Tipos de registros de salud soportados.
 * Mapean a los tipos de Health Connect (androidx.health.connect.client.records).
 */
enum class HealthRecordType {
    // Actividad
    STEPS,
    ACTIVE_CALORIES_BURNED,
    BASAL_METABOLIC_RATE,
    DISTANCE,
    EXERCISE_SESSION,
    HEART_RATE,
    HEART_RATE_VARIABILITY,
    RESTING_HEART_RATE,
    VO2_MAX,
    SPEED,
    POWER,
    CADENCE,

    // Cuerpo
    WEIGHT,
    BODY_FAT,
    HEIGHT,
    LEAN_BODY_MASS,
    BODY_WATER_MASS,
    BONE_MASS,
    MUSCLE_MASS,
    WAIST_CIRCUMFERENCE,

    // Sueño
    SLEEP_SESSION,
    SLEEP_STAGE,

    // Signos vitales
    BLOOD_OXYGEN,
    BLOOD_PRESSURE,
    BODY_TEMPERATURE,
    RESPIRATORY_RATE,
    SKIN_TEMPERATURE,

    // Ciclo menstrual
    MENSTRUATION_FLOW,
    OVULATION_TEST,
    CERVICAL_MUCUS,
    BASAL_BODY_TEMPERATURE,

    // Nutrición
    NUTRITION,
    HYDRATION,

    // Médicos
    MEDICATION,
    ALLERGY,
    CONDITION,
    IMMUNIZATION,
    LAB_RESULT,
    PROCEDURE,
    VITAL_SIGNS; // Semicolon required before companion object

    companion object {
        /** Todos los tipos de actividad/fitness */
        val activityTypes = setOf(
            STEPS, ACTIVE_CALORIES_BURNED, BASAL_METABOLIC_RATE, DISTANCE,
            EXERCISE_SESSION, HEART_RATE, HEART_RATE_VARIABILITY, RESTING_HEART_RATE,
            VO2_MAX, SPEED, POWER, CADENCE
        )

        /** Todos los tipos de cuerpo/medidas */
        val bodyTypes = setOf(
            WEIGHT, BODY_FAT, HEIGHT, LEAN_BODY_MASS, BODY_WATER_MASS,
            BONE_MASS, MUSCLE_MASS, WAIST_CIRCUMFERENCE
        )

        /** Todos los tipos de sueño */
        val sleepTypes = setOf(SLEEP_SESSION, SLEEP_STAGE)

        /** Todos los signos vitales */
        val vitalsTypes = setOf(
            BLOOD_OXYGEN, BLOOD_PRESSURE, BODY_TEMPERATURE, RESPIRATORY_RATE, SKIN_TEMPERATURE
        )

        /** Tipos mínimos para funcionalidad básica de la app */
        val essentialTypes = setOf(
            STEPS, HEART_RATE, SLEEP_SESSION, BLOOD_OXYGEN, WEIGHT
        )

        /** Todos los tipos */
        val all: Set<HealthRecordType>
            get() = activityTypes + bodyTypes + sleepTypes + vitalsTypes +
                setOf(
                    MENSTRUATION_FLOW, OVULATION_TEST, CERVICAL_MUCUS, BASAL_BODY_TEMPERATURE,
                    NUTRITION, HYDRATION,
                    MEDICATION, ALLERGY, CONDITION, IMMUNIZATION, LAB_RESULT, PROCEDURE, VITAL_SIGNS
                )
    }
}