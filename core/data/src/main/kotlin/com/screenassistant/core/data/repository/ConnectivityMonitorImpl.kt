package com.screenassistant.core.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.screenassistant.core.domain.model.proactive.ConnectivityInfo
import com.screenassistant.core.domain.repository.ai.ConnectivityMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [ConnectivityMonitor] usando Android ConnectivityManager.
 *
 * Reutiliza la misma lógica que [ContextAggregatorRepositoryImpl.getConnectivityInfo]
 * para mantener una sola fuente de verdad de conectividad.
 */
@Singleton
class ConnectivityMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConnectivityMonitor {

    private val _connectivityInfo = MutableStateFlow(ConnectivityInfo.from(wifi = false, mobile = false))

    override val connectivityInfo: StateFlow<ConnectivityInfo> = _connectivityInfo.asStateFlow()

    init {
        // Actualizar estado inicial
        _connectivityInfo.value = getConnectivityInfo()
    }

    override fun observeConnectivity(): Flow<ConnectivityInfo> = flow {
        // Emitir estado actual
        emit(getConnectivityInfo())
        // En una implementación real, se registraría un BroadcastReceiver
        // para escuchar cambios de conectividad. Por ahora, emite bajo demanda.
    }

    override suspend fun isConnected(): Boolean {
        return getConnectivityInfo().isConnected
    }

    /**
     * Obtiene la información de conectividad actual.
     * Misma lógica que ContextAggregatorRepositoryImpl.getConnectivityInfo().
     */
    private fun getConnectivityInfo(): ConnectivityInfo {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager

            if (connectivityManager == null) {
                return ConnectivityInfo.from(wifi = false, mobile = false)
            }

            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

            val wifi = capabilities?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } ?: false

            val mobile = capabilities?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            } ?: false

            ConnectivityInfo.from(wifi = wifi, mobile = mobile)
        } catch (e: Exception) {
            ConnectivityInfo.from(wifi = false, mobile = false)
        }
    }
}
