package com.screenassistant.service.system

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.screenassistant.core.data.repository.ScreenContextRepositoryImpl
import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.ScreenCaptureProvider
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * D7 (Lote 9): pago del residuo del service-locator (B3 pagó el 90% en Lote 8).
 * El servicio se AUTO-REGISTRA como [ScreenCaptureProvider] en
 * [ScreenContextRepositoryImpl] al conectarse y se desregistra al desconectarse
 * — se elimina el companion estático (WeakReference + acceso de instancia y el
 * texto estático MUERTO: la vía real del texto es `updateScreenText()` → StateFlow
 * del repo) y el object proveedor de service:system/bridge (ya no existe).
 *
 * P1-5: se inyecta la impl CONCRETA [ScreenContextRepositoryImpl] (el registro es
 * mecanismo interno de core:data — NO se ensucia la interfaz de dominio).
 *
 * Fail-soft intacto (contrato B3): sin servicio conectado → el repo devuelve null.
 */
@AndroidEntryPoint
class ScreenContextService : AccessibilityService(), ScreenCaptureProvider {

    @Inject lateinit var screenContextRepository: ScreenContextRepositoryImpl

    private var lastEventTime = 0L
    private val THROTTLE_MS = 1000L // Solo procesar cada 1 segundo

    // M8: tope de caracteres del texto de pantalla (evita builders sin límite).
    private val MAX_TEXT_LENGTH = 4000

    // B3: executor de instancia (el estático del companion desapareció); el callback
    // de TakeScreenshotCallback se ejecuta aquí (el wrap/resize/compress pesado NO
    // toca el main thread). Se cierra en onDestroy.
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    // M8 + EXTRA-1 (Lote 10): guard anti-reentrada. @Volatile: acceso cross-thread
    // REAL — escribe el hilo IO del VM (captureScreenshot vía suspendCancellableCoroutine
    // desde viewModelScope.launch(ioDispatcher)) y el executor de screenshots
    // (onSuccess/onFailure). Sin @Volatile, una segunda petición concurrente podía
    // leer un valor obsoleto → dos capturas simultáneas (viola el guard M8).
    // Mismo patrón que el KDoc de ScreenContextRepositoryImpl.
    @Volatile
    private var capturaEnCurso = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        registrarProveedor()
    }

    override fun onRebind(intent: android.content.Intent?) {
        super.onRebind(intent)
        // EXTRA-2 (Lote 10): el framework puede reconectar SIN re-lanzar
        // onServiceConnected (no garantizado por contrato entre versiones) → el
        // proveedor quedaría desregistrado y la captura muerta (fail-soft, sin
        // crash). Re-registrar es el mismo camino, coste ~0.
        registrarProveedor()
    }

    // D7 + EXTRA-2 (Lote 10): auto-registro compartido — el proveedor de captura
    // ES este servicio. Se invoca desde onServiceConnected y onRebind (misma lógica).
    private fun registrarProveedor() {
        if (::screenContextRepository.isInitialized) {
            screenContextRepository.setScreenCaptureProvider(this)
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        // D7: desregistro — el servicio deja de ser el proveedor de captura.
        if (::screenContextRepository.isInitialized) {
            screenContextRepository.setScreenCaptureProvider(null)
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::screenContextRepository.isInitialized) {
            screenContextRepository.setScreenCaptureProvider(null)
        }
        screenshotExecutor.shutdown()
    }

    /**
     * B3: captura de pantalla cancelable. Suspende hasta el callback de la
     * plataforma (o hasta que la corrutina se cancele); null fail-soft en:
     * API < 30, captura ya en curso, fallo de la plataforma o cancelación.
     */
    override suspend fun captureScreenshot(): ImageData? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        if (capturaEnCurso) {
            // M8: una captura en curso (p.ej. dos mensajes casi simultáneos) → no
            // encolar; la segunda petición recibe null (Gemini procesa sin imagen).
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        capturaEnCurso = true
        cont.invokeOnCancellation { capturaEnCurso = false }
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        capturaEnCurso = false
                        if (cont.isActive) {
                            cont.resume(convertir(screenshot))
                        } else {
                            // M8: la corrutina ya no espera → cerrar el buffer igualmente
                            // (close() SIEMPRE, nunca fugar el hardware buffer).
                            screenshot.hardwareBuffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        capturaEnCurso = false
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
        } catch (e: CancellationException) {
            capturaEnCurso = false
            throw e
        } catch (e: Exception) {
            capturaEnCurso = false
            if (cont.isActive) cont.resume(null)
        }
    }

    /**
     * M8: wrap del hardware buffer → bitmap software → resize 1024 → JPEG q80.
     * El hardwareBuffer se cierra SIEMPRE (finally); recycle() solo en API < 33
     * (desde API 33 el runtime gestiona la memoria nativa de Bitmap).
     */
    private fun convertir(screenshot: AccessibilityService.ScreenshotResult): ImageData? {
        val hardwareBuffer = screenshot.hardwareBuffer
        var bitmap: Bitmap? = null
        var softwareBitmap: Bitmap? = null
        var resized: Bitmap? = null
        return try {
            bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
            softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
            resized = softwareBitmap?.let { scaleBitmap(it, 1024) }
            val baos = ByteArrayOutputStream()
            resized?.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val data = baos.toByteArray()
            if (data.isEmpty() || resized == null) {
                null
            } else {
                ImageData(data, resized.width, resized.height)
            }
        } catch (e: Exception) {
            null
        } finally {
            hardwareBuffer.close()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                bitmap?.recycle()
                softwareBitmap?.recycle()
                resized?.recycle()
            }
        }
    }

    private fun scaleBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        val ratio = width.toFloat() / height.toFloat()

        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEventTime < THROTTLE_MS) return

        lastEventTime = currentTime

        val rootNode = rootInActiveWindow ?: return
        val textBuilder = StringBuilder()
        extractText(rootNode, textBuilder)
        val extractedText = textBuilder.toString()
        // Actualizar el repositorio (D7: sin texto estático — la vía real del
        // texto es el StateFlow del repo).
        if (::screenContextRepository.isInitialized) {
            screenContextRepository.updateScreenText(extractedText)
        }
        rootNode.recycle() // Importante reciclar nodos si no se usan más
    }

    // M8: el builder se acota a MAX_TEXT_LENGTH (antes crecía sin límite: un
    // AccessibilityNodeInfo gigante podía generar megabytes de texto).
    private fun extractText(node: AccessibilityNodeInfo, builder: StringBuilder) {
        if (builder.length >= MAX_TEXT_LENGTH) return
        node.text?.let {
            if (it.isNotEmpty()) {
                val remaining = MAX_TEXT_LENGTH - builder.length
                if (remaining > 0) builder.append(it, 0, minOf(it.length, remaining)).append(" ")
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractText(child, builder)
                child.recycle() // Reciclar hijos
            }
        }
    }

    override fun onInterrupt() {}
}
