package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.proactive.AggregatedContext
import com.screenassistant.core.domain.model.proactive.BatteryInfo
import com.screenassistant.core.domain.model.proactive.CalendarEvent
import com.screenassistant.core.domain.model.proactive.ConnectivityInfo

/**
 * Repositorio de acceso a la información contextual agregada del dispositivo.
 *
 * Proporciona una vista unificada de todas las señales de contexto (batería,
 * calendario, conectividad) necesarias para evaluar las reglas proactivas.
 * Esta interfaz actúa como puente entre las fuentes de datos del sistema
 * (APIs de Android) y el dominio puro.
 */
interface ContextAggregatorRepository {

    /**
     * Obtiene el contexto agregado completo del momento actual.
     * Consolida batería, eventos, conectividad, pantalla y ubicación
     * en una única estructura [AggregatedContext].
     *
     * @return [AggregatedContext] con todas las señales de contexto disponibles
     */
    suspend fun getAggregatedContext(): AggregatedContext

    /**
     * Obtiene el estado actual de la batería del dispositivo.
     *
     * @return [BatteryInfo] con el nivel y estado de carga
     */
    suspend fun getBatteryInfo(): BatteryInfo

    /**
     * Obtiene los próximos eventos del calendario dentro de la ventana de tiempo indicada.
     *
     * @param withinMinutes Ventana de búsqueda en minutos hacia el futuro (por defecto 60)
     * @return Lista de [CalendarEvent]s próximos, ordenados por fecha de inicio
     */
    suspend fun getUpcomingEvents(withinMinutes: Int = 60): List<CalendarEvent>

    /**
     * Obtiene el estado actual de conectividad de red del dispositivo.
     *
     * @return [ConnectivityInfo] con los estados de WiFi y datos móviles
     */
    suspend fun getConnectivityInfo(): ConnectivityInfo
}
