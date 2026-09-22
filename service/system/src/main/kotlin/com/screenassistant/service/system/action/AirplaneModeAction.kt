package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AirplaneModeAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun setAirplaneMode(enabled: Boolean): String {
        val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return if (enabled) {
            "Abriendo ajustes del modo avión para activarlo."
        } else {
            "Abriendo ajustes del modo avión para desactivarlo."
        }
    }
}
