package com.screenassistant.feature.overlay

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.screenassistant.core.domain.service.TextToSpeech as TextToSpeechService
import java.util.Locale

class TextToSpeechManager(
    context: Context,
    private val onStart: () -> Unit = {},
    private val onDone: () -> Unit = {}
) : TextToSpeech.OnInitListener, TextToSpeechService {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
                setupCallbacks()
            }
        }
    }

    private fun setupCallbacks() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { onStart() }
            override fun onDone(utteranceId: String?) { onDone() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
    }

    override fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_voice")
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }
}
