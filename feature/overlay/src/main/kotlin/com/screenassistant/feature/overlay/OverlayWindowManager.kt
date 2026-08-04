package com.screenassistant.feature.overlay

import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.WindowManager

class OverlayWindowManager(private val windowManager: WindowManager) {

    private var params: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT

        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        format = PixelFormat.TRANSLUCENT
        dimAmount = 0.0f
        alpha = 1.0f
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 300
    }

    private var currentView: View? = null

    fun addView(view: View) {
        currentView = view
        windowManager.addView(view, params)
    }

    fun updatePosition(dx: Int, dy: Int) {
        val view = currentView ?: return
        val bounds = screenBounds()
        // Tamaño real del view (tras addView puede no estar medido: medida con
        // MeasureSpec.UNSPECIFIED para conocer el WRAP_CONTENT).
        val viewW = view.width.takeIf { it > 0 } ?: view.measuredWidth
        val viewH = view.height.takeIf { it > 0 } ?: view.measuredHeight
        // M17: clamp del drag — el personaje NUNCA sale de la pantalla visible;
        // queda al menos parcialmente visible (coerceAtLeast(0) cubre el caso
        // teórico view más ancho que la pantalla).
        params.x = (params.x + dx).coerceIn(0, (bounds.width() - viewW).coerceAtLeast(0))
        params.y = (params.y + dy).coerceIn(0, (bounds.height() - viewH).coerceAtLeast(0))
        updateLayout()
    }

    /** Bounds de la ventana real (currentWindowMetrics, API 30+) con fallback a
     *  los displayMetrics del sistema para API < 30 (excluye bars, aceptable en
     *  el fallback: los límites siguen siendo conservadores). */
    private fun screenBounds(): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val metrics = Resources.getSystem().displayMetrics
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }

    fun setFocusable(focusable: Boolean) {
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        updateLayout()
    }

    private fun updateLayout() {
        currentView?.let {
            try {
                windowManager.updateViewLayout(it, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
