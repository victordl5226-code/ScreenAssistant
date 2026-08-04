package com.screenassistant.core.data.repository

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.ScreenCaptureProvider
import com.screenassistant.core.domain.repository.ScreenContextRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 */
@Singleton
class ScreenContextRepositoryImpl @Inject constructor() : ScreenContextRepository {

    @Volatile
    private var screenCaptureProvider: ScreenCaptureProvider? = null

    private val _screenText = MutableStateFlow("")
    override val screenText: StateFlow<String> = _screenText.asStateFlow()

    /** Registro dinámico del proveedor real (el servicio de accesibilidad). */
    fun setScreenCaptureProvider(provider: ScreenCaptureProvider?) {
        screenCaptureProvider = provider
    }

    override suspend fun captureScreenshot(): ImageData? =
        screenCaptureProvider?.captureScreenshot()

    override fun updateScreenText(text: String) {
        _screenText.value = text
    }
}
