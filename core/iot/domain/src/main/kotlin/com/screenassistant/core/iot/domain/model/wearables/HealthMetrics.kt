package com.screenassistant.core.iot.domain.model.wearables

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Métricas de salud agregadas desde Health Connect / wearables.
 *
 * Este modelo unifica los datos de salud provenientes de diferentes fuentes
 * (Health Connect en Android, HealthKit en iOS, wearables directos)
 * en una estructura común para la app.
 *
 * Todas las mediciones tienen timestamp para permitir series temporales.
 *
 * @property heartRate Frecuencia cardíaca actual (bpm)
 * @property heartRateVariability Variabilidad de la frecuencia cardíaca (ms)
 * @property restingHeartRate Frecuencia cardíaca en reposo (bpm)
 * @property steps Pasos totales del día
 * @property caloriesBurned Calorías quemadas activas (kcal)
 * @property distanceMeters Distancia recorrida (metros)
 * @property activeMinutes Minutos de actividad moderada/vigorosa
 * @property sleepHours Horas de sueño de la última noche
 * @property sleepStages Detalle de etapas de sueño
 * @property bloodOxygen Saturación de oxígeno en sangre (%)
 * @property stressLevel Nivel de estrés (0-100)
 * @property bodyTemperature Temperatura corporal (°C)
 * @property respiratoryRate Frecuencia respiratoria (rpm)
 * @property vo2Max VO2 máximo estimado (ml/kg/min)
 * @property weight Peso corporal (kg)
 * @property bodyFatPercentage Porcentaje de grasa corporal (%)
 * @property hydrationLevel Nivel de hidratación (ml o %)
 * @property menstrualCycle Fase del ciclo menstrual (si aplica)
 * @property lastSynced Última sincronización exitosa con la fuente
 * @property source Fuente de los datos (Health Connect, Galaxy Watch, etc.)
 */
@Serializable
data class HealthMetrics(
    val heartRate: HeartRateMetric? = null,
    val heartRateVariability: HRVMetric? = null,
    val restingHeartRate: RestingHeartRateMetric? = null,
    val steps: StepsMetric? = null,
    val caloriesBurned: CaloriesMetric? = null,
    val distanceMeters: DistanceMetric? = null,
    val activeMinutes: ActiveMinutesMetric? = null,
    val sleepHours: SleepHoursMetric? = null,
    val sleepStages: SleepStagesMetric? = null,
    val bloodOxygen: BloodOxygenMetric? = null,
    val stressLevel: StressMetric? = null,
    val bodyTemperature: TemperatureMetric? = null,
    val respiratoryRate: RespiratoryRateMetric? = null,
    val vo2Max: VO2MaxMetric? = null,
    val weight: WeightMetric? = null,
    val bodyFatPercentage: BodyFatMetric? = null,
    val hydrationLevel: HydrationMetric? = null,
    val menstrualCycle: MenstrualCycleMetric? = null,
    val lastSynced: Instant = Clock.System.now(),
    val source: DataSource = DataSource.UNKNOWN,
) {
    /**
     * Verifica si hay datos recientes (menos de 24h para métricas diarias, 1h para vitales).
     */
    fun hasRecentData(): Boolean {
        val now = Clock.System.now()
        return listOf(
            heartRate, heartRateVariability, bloodOxygen, stressLevel,
            bodyTemperature, respiratoryRate
        ).any { it?.isRecent(now, hours = 1) == true } ||
               listOf(
                   steps, caloriesBurned, distanceMeters, activeMinutes,
                   sleepHours, sleepStages
               ).any { it?.isRecent(now, hours = 24) == true }
    }

    /**
     * Obtiene un resumen textual de las métricas disponibles.
     */
    fun summary(): String {
        val parts = mutableListOf<String>()
        heartRate?.let { parts += "FC: ${it.value}bpm" }
        steps?.let { parts += "Pasos: ${it.value}" }
        sleepHours?.let { parts += "Sueño: ${String.format("%.1f", it.value)}h" }
        bloodOxygen?.let { parts += "SpO2: ${it.value}%" }
        stressLevel?.let { parts += "Estrés: ${it.value}/100" }
        return if (parts.isEmpty()) "Sin datos recientes" else parts.joinToString(" • ")
    }
}

/**
 * Fuente de los datos de salud.
 */
enum class DataSource {
    HEALTH_CONNECT,
    GOOGLE_FIT,
    SAMSUNG_HEALTH,
    GALAXY_WATCH,
    PIXEL_WATCH,
    FITBIT,
    GARMIN,
    APPLE_HEALTH,
    MANUAL_ENTRY,
    UNKNOWN,
}

/**
 * Interfaz común para métricas de salud con timestamp.
 */
interface HealthMetric {
    val timestamp: Instant
    fun isRecent(now: Instant, hours: Long): Boolean {
        val diff = now.epochSeconds - timestamp.epochSeconds
        return diff <= hours * 3600
    }
}

/** Frecuencia cardíaca instantánea (bpm) */
@Serializable
data class HeartRateMetric(
    override val timestamp: Instant,
    val value: Int,
    val unit: String = "bpm",
) : HealthMetric

/** Variabilidad de frecuencia cardíaca (ms) */
@Serializable
data class HRVMetric(
    override val timestamp: Instant,
    val value: Double,
    val unit: String = "ms",
) : HealthMetric

/** Frecuencia cardíaca en reposo (bpm) */
@Serializable
data class RestingHeartRateMetric(
    override val timestamp: Instant,
    val value: Int,
    val unit: String = "bpm",
) : HealthMetric

/** Pasos diarios */
@Serializable
data class StepsMetric(
    override val timestamp: Instant,
    val value: Long,
    val unit: String = "steps",
) : HealthMetric

/** Calorías quemadas activas (kcal) */
@Serializable
data class CaloriesMetric(
    override val timestamp: Instant,
    val value: Double,
    val unit: String = "kcal",
) : HealthMetric

/** Distancia recorrida (metros) */
@Serializable
data class DistanceMetric(
    override val timestamp: Instant,
    val value: Double,
    val unit: String = "meters",
) : HealthMetric

/** Minutos de actividad */
@Serializable
data class ActiveMinutesMetric(
    override val timestamp: Instant,
    val value: Int,
    val unit: String = "minutes",
) : HealthMetric

/** Horas de sueño */
@Serializable
data class SleepHoursMetric(
    override val timestamp: Instant,
    val value: Double,
    val unit: String = "hours",
) : HealthMetric

/** Etapas de sueño detalladas */
@Serializable
data class SleepStagesMetric(
    override val timestamp: Instant,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val unit: String = "minutes",
) : HealthMetric {
    val totalMinutes: Int
        get() = deepMinutes + lightMinutes + remMinutes + awakeMinutes

    val efficiencyPercent: Double
        get() = if (totalMinutes > 0) (deepMinutes + lightMinutes + remMinutes) * 100.0 / totalMinutes else 0.0
}

/** Saturación de oxígeno (%) */
@Serializable
data class BloodOxygenMetric(
    override val timestamp: Instant,
    val value: Int,
    val unit: String = "%",
) : HealthMetric

/** Nivel de estrés (0-100) */
@Serializable
data class StressMetric(
    override val timestamp: Instant,
    val value: Int,
    val unit: String = "score",
) : HealthMetric

/** Temperatura corporal (°C) */
@Serializable
data class TemperatureMetric(
    override val timestamp: Instant,
    val value: Double,
    val unit: String = "°C",
) : HealthMetric

/** Frecuencia respiratoria (rpm) */
@Serializable
data class RespiratoryRateMetric(
    override val timestamp: Instant,
    val value: Double,
    val unit: String = "rpm",
) : HealthMetric

/** VO2 máximo (ml/kg/min) */
@Serializable
data class VO2MaxMetric(
    override val timestamp: Instant,
    val value: Double,
    val unit: String = "ml/kg/min",
) : HealthMetric

/** Peso corporal (kg) */
@Serializable
data class WeightMetric(
    override val timestamp: Instant,
    val value: Double,
    val unit: String = "kg",
) : HealthMetric

/** Grasa corporal (%) */
@Serializable
data class BodyFatMetric(
    override val timestamp: Instant,
    val value: Double,
    val unit: String = "%",
) : HealthMetric

/** Hidratación (ml) */
@Serializable
data class HydrationMetric(
    override val timestamp: Instant,
    val value: Double,
    val unit: String = "ml",
) : HealthMetric

/** Fase del ciclo menstrual */
@Serializable
data class MenstrualCycleMetric(
    override val timestamp: Instant,
    val phase: MenstrualPhase,
    val dayOfCycle: Int,
    val unit: String = "day",
) : HealthMetric

/** Fases del ciclo menstrual */
enum class MenstrualPhase {
    MENSTRUAL,
    FOLLICULAR,
    OVULATION,
    LUTEAL,
    UNKNOWN,
}