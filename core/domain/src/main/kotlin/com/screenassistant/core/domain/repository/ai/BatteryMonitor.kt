package com.screenassistant.core.domain.repository.ai

import com.screenassistant.core.domain.model.proactive.BatteryInfo
import kotlinx.coroutines.flow.Flow

/**
 * Monitor del estado de la batería y carga.
 */
interface BatteryMonitor {
    /**
     * Observa cambios en el estado de la batería.
     */
    fun observeBattery(): Flow<BatteryInfo>

    /**
     * Obtiene el estado actual de la batería.
     */
    suspend fun getBatteryInfo(): BatteryInfo
}
