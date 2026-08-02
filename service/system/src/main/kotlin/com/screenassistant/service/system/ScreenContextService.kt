package com.screenassistant.service.system

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.screenassistant.core.data.repository.ScreenContextRepositoryImpl
import dagger.hilt.android.AndroidEntryPoint
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class ScreenContextService : AccessibilityService() {

    @Inject lateinit var screenContextRepository: ScreenContextRepositoryImpl

    companion object {
        @Volatile
        var lastScreenText: String = ""
            private set

        private var instanceRef = WeakReference<ScreenContextService>(null)
        val instanceContext: android.content.Context? get() = instanceRef.get()

        private var screenshotCallback: ((Bitmap?) -> Unit)? = null

        fun setScreenshotCallback(callback: ((Bitmap?) -> Unit)?) {
            screenshotCallback = callback
        }

        private val screenshotExecutor = Executors.newSingleThreadExecutor()

        fun takeScreenshot(callback: (Bitmap?) -> Unit) {
            screenshotCallback = callback
            val service = instanceRef.get()
            if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    service.takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        screenshotExecutor,
                        object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                                val bitmap = Bitmap.wrapHardwareBuffer(
                                    screenshot.hardwareBuffer,
                                    screenshot.colorSpace
                                )
                                // Convert to software bitmap and resize for AI processing (reduces payload size and serialization errors)
                                val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                val resizedBitmap = softwareBitmap?.let { scaleBitmap(it, 1024) }
                                screenshotCallback?.invoke(resizedBitmap)
                                screenshotCallback = null
                            }

                            override fun onFailure(errorCode: Int) {
                                screenshotCallback?.invoke(null)
                                screenshotCallback = null
                            }
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    screenshotCallback?.invoke(null)
                    screenshotCallback = null
                }
            } else {
                screenshotCallback?.invoke(null)
                screenshotCallback = null
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
    }

    private var lastEventTime = 0L
    private val THROTTLE_MS = 1000L // Solo procesar cada 1 segundo

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

    private fun extractText(node: AccessibilityNodeInfo, builder: StringBuilder) {
        node.text?.let {
            if (it.isNotEmpty()) {
                builder.append(it).append(" ")
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
