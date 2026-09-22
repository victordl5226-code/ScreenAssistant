package com.screenassistant.feature.overlay.hotword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.screenassistant.core.domain.service.HotwordDetectionEvent
import com.screenassistant.core.domain.service.HotwordDetector
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service en background que escucha el hotword "Hey JARVIS".
 *
 * Cuando detecta el hotword, notifica al overlay para activar el STT.
 */
@AndroidEntryPoint
class HotwordListeningService : LifecycleService() {

    @Inject lateinit var hotwordDetector: HotwordDetector
    @Inject lateinit var hotwordPreferences: HotwordPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startHotwordListening()
    }

    override fun onDestroy() {
        stopHotwordListening()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startHotwordListening() {
        serviceScope.launch {
            hotwordDetector.detectionEvents.collectLatest { event ->
                when (event) {
                    is HotwordDetectionEvent.KeywordDetected -> {
                        Log.i(TAG, "Hotword detected: ${event.keyword}")
                        sendBroadcast(Intent(ACTION_HOTWORD_DETECTED))
                    }
                    is HotwordDetectionEvent.Error -> {
                        Log.e(TAG, "Hotword error: ${event.message}")
                    }
                    is HotwordDetectionEvent.ListeningStarted -> {
                        isListening = true
                        Log.d(TAG, "Hotword listening started")
                    }
                    is HotwordDetectionEvent.ListeningStopped -> {
                        isListening = false
                        Log.d(TAG, "Hotword listening stopped")
                    }
                }
            }
        }
        hotwordDetector.startListening()
    }

    private fun stopHotwordListening() {
        hotwordDetector.stopListening()
        isListening = false
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hotword Listening",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Escuchando hotword Hey JARVIS"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS está escuchando")
            .setContentText("Di 'Hey JARVIS' para activar")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "HotwordService"
        private const val CHANNEL_ID = "hotword_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_HOTWORD_DETECTED = "com.screenassistant.HOTWORD_DETECTED"

        fun start(context: Context) {
            val intent = Intent(context, HotwordListeningService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HotwordListeningService::class.java))
        }
    }
}
