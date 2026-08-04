package com.screenassistant.service.system.bridge

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.ScreenCaptureProvider
import com.screenassistant.service.system.ScreenContextService

/**
 * B3 (Lote 8): implementación de [ScreenCaptureProvider] que delega en la
 * instancia viva del servicio de accesibilidad.
 *
 * Vive en service:system porque ScreenContextService está aquí (core:data no
 * puede depender de este módulo). Fail-soft: servicio desconectado → null.
 */
object ServiceScreenCaptureProvider : ScreenCaptureProvider {
    override suspend fun captureScreenshot(): ImageData? =
        ScreenContextService.instancia()?.captureScreenshot()
}
