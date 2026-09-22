package com.screenassistant.feature.overlay.hotword

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioRecord.STATE_INITIALIZED
import android.media.MediaRecorder
import android.util.Log
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import com.screenassistant.core.domain.service.HotwordDetectionEvent
import com.screenassistant.core.domain.service.HotwordDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [HotwordDetector] basada en Porcupine (Picovoice).
 *
 * Utiliza el keyword built-in JARVIS para detectar la palabra de activación
 * de forma offline y eficiente en batería.
 *
 * Requiere un AccessKey válido de Picovoice (free tier disponible en
 * https://console.picovoice.ai/).
 */
@Singleton
class PorcupineHotwordDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : HotwordDetector {

    private val _detectionEvents = MutableSharedFlow<HotwordDetectionEvent>(extraBufferCapacity = 64)
    override val detectionEvents = _detectionEvents.asSharedFlow()

    private var porcupine: Porcupine? = null
    private var audioRecord: AudioRecord? = null
    private var listeningJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var _isListening = false

    override fun startListening() {
        if (_isListening) return

        try {
            porcupine = Porcupine.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .build(context)

            val sampleRate = porcupine!!.sampleRate
            val frameLength = porcupine!!.frameLength

            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                _detectionEvents.tryEmit(
                    HotwordDetectionEvent.Error(message = "AudioRecord initialization failed")
                )
                releaseResources()
                return
            }

            audioRecord?.startRecording()
            _isListening = true
            _detectionEvents.tryEmit(HotwordDetectionEvent.ListeningStarted)

            listeningJob = scope.launch {
                val frame = ShortArray(frameLength)
                try {
                    while (isActive && _isListening) {
                        val read = audioRecord?.read(frame, 0, frame.size) ?: -1
                        if (read > 0) {
                            val keywordIndex = porcupine?.process(frame) ?: -1
                            if (keywordIndex >= 0) {
                                _detectionEvents.emit(
                                    HotwordDetectionEvent.KeywordDetected(keyword = "JARVIS")
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in listening loop", e)
                    _detectionEvents.emit(
                        HotwordDetectionEvent.Error(message = e.message ?: "Unknown error in listening loop")
                    )
                }
            }
        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine init failed", e)
            _detectionEvents.tryEmit(
                HotwordDetectionEvent.Error(message = "Failed to initialize Porcupine: ${e.message}")
            )
            releaseResources()
        }
    }

    override fun stopListening() {
        _isListening = false
        listeningJob?.cancel()
        listeningJob = null
        releaseResources()
        _detectionEvents.tryEmit(HotwordDetectionEvent.ListeningStopped)
    }

    override fun isListening(): Boolean = _isListening

    override fun destroy() {
        stopListening()
        scope.cancel()
    }

    private fun releaseResources() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null

        try {
            porcupine?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Porcupine", e)
        }
        porcupine = null
    }

    companion object {
        private const val TAG = "PorcupineHotword"

        // TODO: Move to BuildConfig or secure storage. Free tier key from picovoice.ai.
        private const val ACCESS_KEY = ""
    }
}
