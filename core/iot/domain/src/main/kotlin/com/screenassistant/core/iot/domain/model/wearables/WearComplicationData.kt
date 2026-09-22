package com.screenassistant.core.iot.domain.model.wearables

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Datos para una complicación de Wear OS.
 *
 * Las complicaciones son pequeños elementos visuales en las esferas de reloj
 * que muestran información de un vistazo. ScreenAssistant puede proveer
 * datos para complicaciones de tipo:
 * - SHORT_TEXT: Texto corto (ej: "72 bpm")
 * - LONG_TEXT: Texto largo (ej: "FC: 72 bpm • 8,542 pasos")
 * - RANGED_VALUE: Valor en rango (ej: anillo de progreso de pasos)
 * - GOAL_PROGRESS: Progreso hacia meta (ej: anillo de actividad)
 * - WEIGHT_ELEMENT: Elemento ponderado (para múltiples complicaciones)
 * - ICON_ONLY: Solo icono
 * - SMALL_IMAGE / LARGE_IMAGE: Imagen
 *
 * @property complicationId Identificador único de la complicación
 * @property type Tipo de complicación
 * @property data Datos específicos según el tipo
 * @property lastUpdated Última actualización
 * @property validity Período de validez de los datos
 * @property tapAction Acción al tocar la complicación
 */
@Serializable
data class WearComplicationData(
    val complicationId: String,
    val type: ComplicationType,
    val data: ComplicationData,
    val lastUpdated: Instant = Clock.System.now(),
    val validity: ComplicationValidity = ComplicationValidity.INDEFINITE,
    val tapAction: ComplicationTapAction? = null,
) {
    /**
     * Crea datos para complicación de frecuencia cardíaca (SHORT_TEXT).
     */
    companion object {
        fun heartRate(heartRate: Int?, complicationId: String = "heart_rate"): WearComplicationData {
            val text = heartRate?.let { "$it bpm" } ?: "-- bpm"
            return WearComplicationData(
                complicationId = complicationId,
                type = ComplicationType.SHORT_TEXT,
                data = ComplicationData.ShortText(text),
                tapAction = ComplicationTapAction.OpenApp("screenassistant.HEALTH_DETAILS"),
            )
        }

        /**
         * Crea datos para complicación de pasos (RANGED_VALUE).
         */
        fun steps(steps: Long?, goal: Long = 10000, complicationId: String = "steps"): WearComplicationData {
            val current = steps ?: 0L
            val progress = if (goal > 0) (current.toDouble() / goal).coerceIn(0.0, 1.0) else 0.0
            return WearComplicationData(
                complicationId = complicationId,
                type = ComplicationType.RANGED_VALUE,
                data = ComplicationData.RangedValue(
                    value = current.toFloat(),
                    min = 0f,
                    max = goal.toFloat(),
                    shortText = "${(progress * 100).toInt()}%",
                ),
                tapAction = ComplicationTapAction.OpenApp("screenassistant.HEALTH_DETAILS"),
            )
        }

        /**
         * Crea datos para complicación de actividad (GOAL_PROGRESS).
         */
        fun activityProgress(
            activeMinutes: Int?,
            goalMinutes: Int = 30,
            complicationId: String = "activity",
        ): WearComplicationData {
            val current = activeMinutes ?: 0
            val progress = if (goalMinutes > 0) (current.toDouble() / goalMinutes).coerceIn(0.0, 1.0) else 0.0
            return WearComplicationData(
                complicationId = complicationId,
                type = ComplicationType.GOAL_PROGRESS,
                data = ComplicationData.GoalProgress(
                    progress = progress.toFloat(),
                    shortText = "${current}min",
                ),
                tapAction = ComplicationTapAction.OpenApp("screenassistant.HEALTH_DETAILS"),
            )
        }

        /**
         * Crea datos para complicación de escena activa (SHORT_TEXT).
         */
        fun activeScene(sceneName: String?, complicationId: String = "scene"): WearComplicationData {
            val text = sceneName ?: "Sin escena"
            return WearComplicationData(
                complicationId = complicationId,
                type = ComplicationType.SHORT_TEXT,
                data = ComplicationData.ShortText(text),
                tapAction = ComplicationTapAction.OpenApp("screenassistant.SCENES"),
            )
        }

        /**
         * Crea datos para complicación de asistente de voz (ICON_ONLY o SHORT_TEXT).
         */
        fun voiceAssistant(status: VoiceStatus, complicationId: String = "voice"): WearComplicationData {
            return when (status) {
                VoiceStatus.IDLE -> WearComplicationData(
                    complicationId = complicationId,
                    type = ComplicationType.ICON_ONLY,
                    data = ComplicationData.IconOnly("mic"),
                    tapAction = ComplicationTapAction.VoiceCommand,
                )
                VoiceStatus.LISTENING -> WearComplicationData(
                    complicationId = complicationId,
                    type = ComplicationType.SHORT_TEXT,
                    data = ComplicationData.ShortText("Escuchando..."),
                    tapAction = ComplicationTapAction.VoiceCommand,
                )
                VoiceStatus.PROCESSING -> WearComplicationData(
                    complicationId = complicationId,
                    type = ComplicationType.SHORT_TEXT,
                    data = ComplicationData.ShortText("Procesando..."),
                )
                VoiceStatus.SPEAKING -> WearComplicationData(
                    complicationId = complicationId,
                    type = ComplicationType.ICON_ONLY,
                    data = ComplicationData.IconOnly("volume_up"),
                )
                VoiceStatus.ERROR -> WearComplicationData(
                    complicationId = complicationId,
                    type = ComplicationType.SHORT_TEXT,
                    data = ComplicationData.ShortText("Error"),
                    tapAction = ComplicationTapAction.OpenApp("screenassistant.MAIN"),
                )
            }
        }

        /**
         * Crea datos para complicación de próximo evento/alarma (LONG_TEXT).
         */
        fun nextEvent(title: String?, time: String?, complicationId: String = "next_event"): WearComplicationData {
            val text = when {
                title != null && time != null -> "$title\n$time"
                title != null -> title
                time != null -> time
                else -> "Sin eventos"
            }
            return WearComplicationData(
                complicationId = complicationId,
                type = ComplicationType.LONG_TEXT,
                data = ComplicationData.LongText(text),
                tapAction = ComplicationTapAction.OpenApp("screenassistant.CALENDAR"),
            )
        }
    }
}

/**
 * Tipos de complicación soportados.
 */
enum class ComplicationType {
    SHORT_TEXT,
    LONG_TEXT,
    RANGED_VALUE,
    GOAL_PROGRESS,
    WEIGHT_ELEMENT,
    ICON_ONLY,
    SMALL_IMAGE,
    LARGE_IMAGE,
}

/**
 * Datos específicos según el tipo de complicación.
 */
@Serializable
sealed class ComplicationData {
    @Serializable
    data class ShortText(val text: String) : ComplicationData()

    @Serializable
    data class LongText(val text: String) : ComplicationData()

    @Serializable
    data class RangedValue(
        val value: Float,
        val min: Float,
        val max: Float,
        val shortText: String? = null,
    ) : ComplicationData()

    @Serializable
    data class GoalProgress(
        val progress: Float, // 0.0 - 1.0
        val shortText: String? = null,
    ) : ComplicationData()

    @Serializable
    data class WeightElement(
        val elements: List<WeightElement>,
    ) : ComplicationData()

    @Serializable
    data class IconOnly(val icon: String) : ComplicationData()

    @Serializable
    data class SmallImage(val imageData: ByteArray, val contentDescription: String?) : ComplicationData()

    @Serializable
    data class LargeImage(val imageData: ByteArray, val contentDescription: String?) : ComplicationData()
}

/**
 * Elemento ponderado para WEIGHT_ELEMENT.
 */
@Serializable
data class WeightElement(
    val weight: Float,
    val complicationData: ComplicationData,
)

/**
 * Período de validez de los datos de complicación.
 */
@Serializable
sealed class ComplicationValidity {
    /** Válido indefinidamente hasta próxima actualización */
    object INDEFINITE : ComplicationValidity()

    /** Válido por un tiempo específico (segundos) */
    @Serializable
    data class TimeBounded(val ttlSeconds: Long) : ComplicationValidity()

    /** Válido hasta un timestamp específico */
    @Serializable
    data class Until(val timestamp: Instant) : ComplicationValidity()
}

/**
 * Acción al tocar la complicación.
 */
@Serializable
sealed class ComplicationTapAction {
    /** Abrir app con action específica */
    @Serializable
    data class OpenApp(val action: String, val extras: Map<String, String> = emptyMap()) : ComplicationTapAction()

    /** Lanzar comando de voz */
    object VoiceCommand : ComplicationTapAction()

    /** Toggle de entidad */
    @Serializable
    data class ToggleEntity(val entityId: String) : ComplicationTapAction()

    /** Sin acción */
    object NONE : ComplicationTapAction()
}

/**
 * Estado del asistente de voz para complicaciones.
 */
enum class VoiceStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR,
}