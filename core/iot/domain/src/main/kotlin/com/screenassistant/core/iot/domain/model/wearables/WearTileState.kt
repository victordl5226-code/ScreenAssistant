package com.screenassistant.core.iot.domain.model.wearables

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Estado de un Tile (mosaico) de Wear OS.
 *
 * Los Tiles son superficies de acceso rápido en Wear OS que muestran
 * información de un vistazo y permiten acciones rápidas.
 * Este modelo define el estado que renderiza un Tile de ScreenAssistant.
 *
 * @property tileId Identificador único del tile
 * @property lastUpdated Última actualización del contenido
 * @property primaryContent Contenido principal (métrica destacada)
 * @property secondaryContent Contenido secundario (lista de métricas)
 * @property actions Acciones disponibles (tap, long press, etc.)
 * @property theme Tema visual (claro/oscuro/sistema)
 * @property locale Locale para formateo
 * @property isInAmbientMode Si el reloj está en modo ambiente (always-on)
 */
@Serializable
data class WearTileState(
    val tileId: String,
    val lastUpdated: Instant = Clock.System.now(),
    val primaryContent: TileContent,
    val secondaryContent: List<TileContent> = emptyList(),
    val actions: List<TileAction> = emptyList(),
    val theme: TileTheme = TileTheme.SYSTEM,
    val locale: String = "es_ES",
    val isInAmbientMode: Boolean = false,
) {
    /**
     * Crea un estado por defecto para un tile de salud.
     */
    companion object {
        fun healthDefault(tileId: String): WearTileState =
            WearTileState(
                tileId = tileId,
                primaryContent = TileContent.HealthSummary(
                    heartRate = null,
                    steps = null,
                    sleepHours = null,
                ),
                secondaryContent = listOf(
                    TileContent.MetricTile(
                        label = "FC",
                        value = "--",
                        unit = "bpm",
                        icon = "favorite",
                    ),
                    TileContent.MetricTile(
                        label = "Pasos",
                        value = "--",
                        unit = "",
                        icon = "directions_walk",
                    ),
                    TileContent.MetricTile(
                        label = "Sueño",
                        value = "--",
                        unit = "h",
                        icon = "bedtime",
                    ),
                ),
                actions = listOf(
                    TileAction.OpenApp("screenassistant.HEALTH_DETAILS"),
                    TileAction.Refresh,
                ),
            )

        /**
         * Crea un estado por defecto para un tile de control de hogar.
         */
        fun homeControlDefault(tileId: String): WearTileState =
            WearTileState(
                tileId = tileId,
                primaryContent = TileContent.SceneTile(
                    sceneName = "Escenas",
                    activeScene = null,
                    deviceCount = 0,
                ),
                secondaryContent = emptyList(),
                actions = listOf(
                    TileAction.OpenApp("screenassistant.SCENES"),
                    TileAction.Custom("screenassistant.TOGGLE_LIGHTS", "Luces", "lightbulb"),
                ),
            )

        /**
         * Crea un estado por defecto para un tile de asistente de voz.
         */
        fun voiceAssistantDefault(tileId: String): WearTileState =
            WearTileState(
                tileId = tileId,
                primaryContent = TileContent.VoiceTile(
                    status = VoiceTileStatus.IDLE,
                    lastTranscript = null,
                ),
                secondaryContent = emptyList(),
                actions = listOf(
                    TileAction.VoiceCommand,
                    TileAction.OpenApp("screenassistant.MAIN"),
                ),
            )
    }
}

/**
 * Contenido renderizable en un Tile.
 */
@Serializable
sealed class TileContent {
    /** Resumen de salud con 3 métricas principales */
    @Serializable
    data class HealthSummary(
        val heartRate: Int?,
        val steps: Long?,
        val sleepHours: Double?,
    ) : TileContent()

    /** Métrica individual con etiqueta, valor, unidad e icono */
    @Serializable
    data class MetricTile(
        val label: String,
        val value: String,
        val unit: String,
        val icon: String,
        val trend: TrendDirection = TrendDirection.STABLE,
    ) : TileContent()

    /** Tile para mostrar/activar una escena */
    @Serializable
    data class SceneTile(
        val sceneName: String,
        val activeScene: String?,
        val deviceCount: Int,
    ) : TileContent()

    /** Tile para control de voz */
    @Serializable
    data class VoiceTile(
        val status: VoiceTileStatus,
        val lastTranscript: String?,
    ) : TileContent()

    /** Tile para temporizador/cronómetro */
    @Serializable
    data class TimerTile(
        val label: String,
        val remainingSeconds: Long,
        val isRunning: Boolean,
        val isPaused: Boolean,
    ) : TileContent()

    /** Tile genérico con texto e icono */
    @Serializable
    data class TextTile(
        val title: String,
        val subtitle: String?,
        val icon: String?,
    ) : TileContent()

    /** Progreso circular (anillo) */
    @Serializable
    data class CircularProgress(
        val progress: Float, // 0.0 - 1.0
        val label: String,
        val value: String,
        val icon: String,
    ) : TileContent()
}

/**
 * Dirección de tendencia para métricas.
 */
enum class TrendDirection {
    UP,
    DOWN,
    STABLE,
}

/**
 * Estado del tile de voz.
 */
enum class VoiceTileStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR,
}

/**
 * Tema visual del Tile.
 */
enum class TileTheme {
    LIGHT,
    DARK,
    SYSTEM,
}

/**
 * Acciones disponibles en un Tile.
 */
@Serializable
sealed class TileAction {
    /** Abrir la app principal o una actividad específica */
    @Serializable
    data class OpenApp(
        val action: String, // Intent action
        val extras: Map<String, String> = emptyMap(),
    ) : TileAction()

    /** Comando de voz */
    @Serializable
    object VoiceCommand : TileAction()

    /** Refrescar datos del tile */
    @Serializable
    object Refresh : TileAction()

    /** Acción personalizada con intent */
    @Serializable
    data class Custom(
        val action: String,
        val label: String,
        val icon: String,
    ) : TileAction()

    /** Toggle de dispositivo/escena */
    @Serializable
    data class Toggle(
        val entityId: String,
        val label: String,
        val iconOn: String,
        val iconOff: String,
    ) : TileAction()

    /** Iniciar/detener temporizador */
    @Serializable
    data class TimerControl(
        val timerId: String,
        val action: TimerAction,
    ) : TileAction()
}

/**
 * Acciones de control de temporizador.
 */
enum class TimerAction {
    START,
    PAUSE,
    RESUME,
    STOP,
    RESET,
}