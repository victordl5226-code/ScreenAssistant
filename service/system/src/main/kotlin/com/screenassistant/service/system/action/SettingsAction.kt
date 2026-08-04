package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.provider.Settings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsAction @Inject constructor(
    private val context: Context
) {
    fun openSettings(): String {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Éxito: Abriendo ajustes del sistema."
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e  // B5: la cancelación nunca se traga
        } catch (e: Exception) {
            "Error: No se pudieron abrir los ajustes."
        }
    }
}
