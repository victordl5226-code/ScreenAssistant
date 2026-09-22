package com.screenassistant.service.system.action

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrightnessAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun setBrightness(level: Int): String {
        return when (level) {
            -1 -> adjustBrightness(10)
            -2 -> adjustBrightness(-10)
            else -> setBrightnessLevel(level)
        }
    }

    private fun setBrightnessLevel(level: Int): String {
        if (!Settings.System.canWrite(context)) {
            return "Error: Necesito permiso para modificar el brillo. Concedelo en ajustes."
        }
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            level.coerceIn(0, 255)
        )
        val percent = (level * 100 / 255).coerceIn(0, 100)
        return "Brillo ajustado al $percent%."
    }

    private fun adjustBrightness(delta: Int): String {
        if (!Settings.System.canWrite(context)) {
            return "Error: Necesito permiso para modificar el brillo. Concedelo en ajustes."
        }
        val current = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            128
        )
        val newLevel = (current + delta).coerceIn(0, 255)
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            newLevel
        )
        val percent = (newLevel * 100 / 255).coerceIn(0, 100)
        return if (delta > 0) {
            "Brillo subido al $percent%."
        } else {
            "Brillo bajado al $percent%."
        }
    }
}
