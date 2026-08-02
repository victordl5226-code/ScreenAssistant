package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapsAction @Inject constructor(
    private val context: Context
) {
    fun navigateTo(destination: String): String {
        return try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + Uri.encode(destination))
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Éxito: Abriendo Maps hacia $destination."
        } catch (e: Exception) {
            "Error: No se pudo abrir Maps."
        }
    }
}
