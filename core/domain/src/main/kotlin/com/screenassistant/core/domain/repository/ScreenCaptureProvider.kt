package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.ImageData

/**
 * B3 (Lote 8) / D7 (Lote 9): captura de pantalla desacoplada del servicio de
 * accesibilidad.
 *
 * core:data no puede depender de service:system → el repositorio depende de ESTA
 * interfaz. El proveedor real es el PROPIO ScreenContextService (service:system),
 * que se AUTO-REGISTRA en ScreenContextRepositoryImpl.setScreenCaptureProvider(this)
 * en onServiceConnected y se desregistra en onUnbind/onDestroy (D7 — antes se
 * resolvía vía WeakReference estático + object en service:system/bridge).
 *
 * Contrato: devuelve null de forma fail-soft en cualquier fallo (API < 30,
 * servicio desconectado — sin proveedor registrado —, captura en curso,
 * excepción de la plataforma).
 */
interface ScreenCaptureProvider {
    suspend fun captureScreenshot(): ImageData?
}
