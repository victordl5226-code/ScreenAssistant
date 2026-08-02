package com.screenassistant.core.domain.service

interface TextToSpeech {
    fun speak(text: String)
    fun stop()
    fun destroy()
}
