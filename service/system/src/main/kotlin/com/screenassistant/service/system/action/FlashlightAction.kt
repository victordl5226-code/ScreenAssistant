package com.screenassistant.service.system.action

import android.content.Context
import android.hardware.camera2.CameraManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlashlightAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    @Volatile
    private var isOn = false

    fun setFlashlight(enabled: Boolean): String {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return "Error: No se encontró una cámara con flash."

            if (enabled) {
                cameraManager.setTorchMode(cameraId, true)
                isOn = true
                "Linterna encendida."
            } else {
                cameraManager.setTorchMode(cameraId, false)
                isOn = false
                "Linterna apagada."
            }
        } catch (e: SecurityException) {
            "Error: No tengo permiso para usar la cámara."
        } catch (e: Exception) {
            "Error: No pudo controlarse la linterna."
        }
    }
}
