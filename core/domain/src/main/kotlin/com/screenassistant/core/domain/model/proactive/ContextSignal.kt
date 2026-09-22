package com.screenassistant.core.domain.model.proactive

import com.screenassistant.core.domain.model.TemporalContext
import java.time.DayOfWeek

/**
 * Evento de calendario básico representado como datos puros del dominio.
 * Se usa tanto en [ContextSignal.Calendar] como en [AggregatedContext].
 *
 * @property title Título del evento
 * @property startMillis Timestamp de inicio en milisegundos desde epoch
 * @property endMillis Timestamp de fin en milisegundos desde epoch
 * @property location Ubicación opcional del evento
 */
data class CalendarEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String? = null
)

/**
 * Señales crudas de contexto que el dispositivo puede emitir.
 * Cada variante encapsula un tipo distinto de señal de contexto.
 *
 * El sistema proactivo consume estas señales, las agrega en [AggregatedContext]
 * y evalúa las [ProactiveRule] definidas por el usuario.
 */
sealed class ContextSignal {

    /**
     * Señal temporal: hora del día, día de la semana, período, etc.
     *
     * @property context Snapshot del contexto temporal calculado
     */
    data class Temporal(val context: TemporalContext) : ContextSignal()

    /**
     * Señal de batería del dispositivo.
     *
     * @property level Nivel de batería (0-100)
     * @property isCharging true si el dispositivo está cargando
     * @throws IllegalArgumentException si level no está en el rango 0-100
     */
    data class Battery(
        val level: Int,
        val isCharging: Boolean
    ) : ContextSignal() {
        init {
            require(level in 0..100) { "El nivel de batería debe estar entre 0 y 100, recibido: $level" }
        }
    }

    /**
     * Señal de calendario con los próximos eventos.
     *
     * @property events Lista de eventos de calendario detectados
     */
    data class Calendar(val events: List<CalendarEvent>) : ContextSignal()

    /**
     * Señal de pantalla: contenido visible en el momento.
     *
     * @property packageName Nombre del paquete de la app en primer plano (null si no disponible)
     * @property text Texto extraído de la pantalla (OCR o accesibilidad)
     */
    data class Screen(
        val packageName: String?,
        val text: String
    ) : ContextSignal()

    /**
     * Señal de conectividad de red.
     *
     * @property wifi true si hay conexión WiFi activa
     * @property mobile true si hay conexión de datos móviles activa
     */
    data class Connectivity(
        val wifi: Boolean,
        val mobile: Boolean
    ) : ContextSignal()
}
