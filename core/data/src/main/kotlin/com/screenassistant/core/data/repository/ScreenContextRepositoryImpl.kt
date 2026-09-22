package com.screenassistant.core.data.repository

import com.screenassistant.core.domain.di.IoDispatcher
import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.model.ScreenMonitoringState
import com.screenassistant.core.domain.repository.ScreenCaptureProvider
import com.screenassistant.core.domain.repository.ScreenContextRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D7 (Lote 9): la captura se delega en el proveedor REGISTRADO dinámicamente —
 * el propio ScreenContextService se auto-registra en `onServiceConnected`
 * (mecanismo interno de core:data; el registro NO ensucia la interfaz
 * [ScreenContextRepository], P1-5). core:data no conoce service:system.
 *
 * Fail-soft (contrato B3): sin proveedor registrado (servicio desconectado,
 * proceso muerto sin onDestroy, captura previa a la conexión) → null.
 * Carrera de hilos: registro en main (onServiceConnected/onUnbind), lectura en
 * IO → `@Volatile` suficiente (mismo patrón que capturaEnCurso del servicio).
 * 
 * Lote 10 (Auto-recovery): si el proveedor se reconecta tras un Error,
 * se reinicia el monitoreo automáticamente con el último intervalo activo.
 */
@Singleton
class ScreenContextRepositoryImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ScreenContextRepository {

    @Volatile
    private var screenCaptureProvider: ScreenCaptureProvider? = null

    private val _screenText = MutableStateFlow("")
    override val screenText: StateFlow<String> = _screenText.asStateFlow()

    private val _activeAppPackage = MutableStateFlow<String?>(null)
    override val activeAppPackage: StateFlow<String?> = _activeAppPackage.asStateFlow()

    // === Monitoreo continuo ===
    private val _monitoringState = MutableStateFlow<ScreenMonitoringState>(ScreenMonitoringState.Idle)
    override val monitoringState: StateFlow<ScreenMonitoringState> = _monitoringState.asStateFlow()

    private var monitoringJob: Job? = null
    private val monitoringScope = CoroutineScope(ioDispatcher + SupervisorJob())

    // Lote 10: guarda el último intervalo activo y si estaba monitoreando para auto-recovery
    private var lastActiveIntervalMs: Long = 5000L
    private var wasMonitoringBeforeDisconnect = false

    companion object {
        const val TIMEOUT_MS = 15000L // 15 segundos sin cambios = Error
    }

    /** Registro dinámico del proveedor real (el servicio de accesibilidad). */
    fun setScreenCaptureProvider(provider: ScreenCaptureProvider?) {
        val wasNull = screenCaptureProvider == null
        screenCaptureProvider = provider

        // Lote 10 (Auto-recovery): si el proveedor se reconecta (null -> non-null)
        // y estábamos monitoreando antes de desconectar, reiniciar automáticamente
        if (wasNull && provider != null && wasMonitoringBeforeDisconnect) {
            startMonitoring(lastActiveIntervalMs)
            wasMonitoringBeforeDisconnect = false
        }
    }

    override suspend fun captureScreenshot(): ImageData? =
        screenCaptureProvider?.captureScreenshot()

    override fun updateScreenText(text: String) {
        _screenText.value = text
        // Si el monitoreo está activo, actualizar también el texto dentro del estado
        val current = _monitoringState.value
        if (current is ScreenMonitoringState.Active) {
            _monitoringState.update {
                (it as? ScreenMonitoringState.Active)?.copy(screenText = text) ?: it
            }
        }
    }

    override fun updateActiveApp(packageName: String?) {
        _activeAppPackage.value = packageName
    }

    override fun startMonitoring(intervalMs: Long) {
        // Cancelar job previo si existe
        monitoringJob?.cancel()

        val safeInterval = intervalMs.coerceAtLeast(1000L)
        // Guardar intervalo para auto-recovery
        lastActiveIntervalMs = safeInterval
        wasMonitoringBeforeDisconnect = true

        _monitoringState.value = ScreenMonitoringState.Active(
            intervalMs = safeInterval,
            screenText = _screenText.value
        )

        monitoringJob = monitoringScope.launch {
            var lastText = _screenText.value
            var lastUpdateTime = System.currentTimeMillis()

            while (true) {
                delay(safeInterval)

                // Detectar si el texto cambió (servicio activo)
                if (_screenText.value != lastText) {
                    lastText = _screenText.value
                    lastUpdateTime = System.currentTimeMillis()
                    _monitoringState.update {
                        (it as? ScreenMonitoringState.Active)?.copy(screenText = _screenText.value) ?: it
                    }
                }
                // Detectar timeout (servicio desconectado)
                else if (System.currentTimeMillis() - lastUpdateTime > TIMEOUT_MS) {
                    _monitoringState.value = ScreenMonitoringState.Error(
                        "Servicio de accesibilidad desconectado"
                    )
                    return@launch
                }
            }
        }
    }

    override fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        wasMonitoringBeforeDisconnect = false
        _monitoringState.value = ScreenMonitoringState.Idle
    }
}
