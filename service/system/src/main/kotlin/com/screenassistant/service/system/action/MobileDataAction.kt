package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MobileDataAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun setMobileData(enabled: Boolean): String {
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return if (enabled) {
            "Abriendo ajustes de datos móviles para activarlos."
        } else {
            "Abriendo ajustes de datos móviles para desactivarlos."
        }
    }
}
