package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.ImageData

/**
 * B3 (Lote 8): captura de pantalla desacoplada del servicio de accesibilidad.
 *
 * core:data no puede depender de service:system → el repositorio depende de ESTA
 * interfaz y la implementación (ServiceScreenCaptureProvider, que consulta la
 * instancia viva de ScreenContextService) se provee desde el grafo de la app.
 *
 * Contrato: devuelve null de forma fail-soft en cualquier fallo (API < 30,
 * servicio desconectado, captura en curso, excepción de la plataforma).
 */
interface ScreenCaptureProvider {
    suspend fun captureScreenshot(): ImageData?
}
