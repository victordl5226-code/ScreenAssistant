package com.screenassistant.core.domain.repository.ai

import com.screenassistant.core.domain.model.proactive.ConnectivityInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Monitor de conectividad de red.
 *
 * Implementado en `core:data` usando Android ConnectivityManager.
 * Reutiliza [ConnectivityInfo] existente del dominio.
 */
interface ConnectivityMonitor {

    /**
     * Estado actual de conectividad.
     */
    val connectivityInfo: StateFlow<ConnectivityInfo>

    /**
     * Observa cambios de conectividad.
     */
    fun observeConnectivity(): Flow<ConnectivityInfo>

    /**
     * Verifica si hay conexión a internet.
     */
    suspend fun isConnected(): Boolean
}
