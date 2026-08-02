package com.screenassistant.core.domain.service

interface SpeechToText {
    fun startListening()
    fun stopListening()
    fun destroy()
}
