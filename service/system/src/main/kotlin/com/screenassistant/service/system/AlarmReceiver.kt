package com.screenassistant.service.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.screenassistant.service.system.action.AlarmAction
import com.screenassistant.service.system.bridge.TaskerBridgeContract
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Recibe el broadcast ALARM_FIRED (mismo action y package que el PendingIntent
 * de la fábrica única de AlarmAction — QA #1). onAlarmFired es suspend (Room),
 * así que se usa goAsync + coroutine en Dispatchers.IO (QA #4) para mantener el
 * proceso vivo hasta terminar.
 *
 * Timeout de seguridad (~10s): si Room se cuelga, el broadcast no debe alargar
 * el ciclo de vida del proceso indefinidamente (ANR); finish() se llama SIEMPRE
 * en finally. M22 (Lote 10): TIMEOUT_MS ÚNICA en TaskerBridgeContract (misma
 * semántica de ventana goAsync que TaskerMessageHandlerImpl).
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmAction: AlarmAction

    override fun onReceive(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra(AlarmAction.EXTRA_REQUEST_CODE, -1)
        if (requestCode < 0) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(TaskerBridgeContract.TIMEOUT_MS) {
                    alarmAction.onAlarmFired(requestCode)
                }
            } finally {
                result.finish()
            }
        }
    }
}
