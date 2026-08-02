package com.screenassistant.feature.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
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
        params.x += dx
        params.y += dy
        updateLayout()
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
