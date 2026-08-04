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
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@AndroidEntryPoint
class ScreenContextService : AccessibilityService(), ScreenCaptureProvider {

    @Inject lateinit var screenContextRepository: ScreenContextRepositoryImpl

    companion object {
        @Volatile
        var lastScreenText: String = ""
            private set

        private var instanceRef = WeakReference<ScreenContextService>(null)

        /** Instancia viva del servicio (null si no está conectado). B3: única vía de
         *  acceso del proveedor de captura (los estáticos takeScreenshot/callback se
         *  eliminaron en el Lote 8: 0 call sites y estado global frágil). */
        fun instancia(): ScreenContextService? = instanceRef.get()
    }

    private var lastEventTime = 0L
    private val THROTTLE_MS = 1000L // Solo procesar cada 1 segundo

    // M8: tope de caracteres del texto de pantalla (evita builders sin límite).
    private val MAX_TEXT_LENGTH = 4000

    // B3: executor de instancia (el estático del companion desapareció); el callback
    // de TakeScreenshotCallback se ejecuta aquí (el wrap/resize/compress pesado NO
    // toca el main thread). Se cierra en onDestroy.
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    // M8: guard anti-reentrada de la captura (fail-soft con null si hay una en curso).
    private var capturaEnCurso = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instanceRef.clear()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceRef.clear()
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
        lastScreenText = extractedText
        // Actualizar el repositorio
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
