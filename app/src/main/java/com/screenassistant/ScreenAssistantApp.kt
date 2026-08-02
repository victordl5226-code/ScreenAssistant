package com.screenassistant

import android.app.Application
import com.screenassistant.service.system.action.AlarmAction
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class ScreenAssistantApp : Application() {

    // Sin wrapper: AlarmAction se inyecta directamente (punto 9 QA).
    @Inject lateinit var alarmAction: AlarmAction

    override fun onCreate() {
        super.onCreate()
        // O4-P2: tras un reinicio del proceso, AlarmManager pierde las alarmas;
        // se reprograman las futuras y se purgan las pasadas desde el store Room.
        // El guard interno de restoreActiveAlarms (canScheduleExactAlarms) evita
        // alarmas fantasma; runCatching cubre cualquier fallo no previsto.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { alarmAction.restoreActiveAlarms() }
        }
    }
}
