package com.screenassistant.core.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Interfaz para detección de hotword (palabra de activación).
 */
interface HotwordDetector {
    /**
     * Flujo de eventos de detección.
     */
    val detectionEvents: Flow<HotwordDetectionEvent>
    
    /**
     * Inicia la escucha del hotword.
     */
    fun startListening()
    
    /**
     * Detiene la escucha del hotword.
     */
    fun stopListening()
    
    /**
     * Indica si está escuchando activamente.
     */
    fun isListening(): Boolean
    
    /**
     * Libera recursos.
     */
    fun destroy()
}

/**
 * Eventos de detección de hotword.
 */
sealed class HotwordDetectionEvent {
    /** Hotword detectado */
    data class KeywordDetected(val keyword: String) : HotwordDetectionEvent()
    /** Error en la detección */
    data class Error(val message: String) : HotwordDetectionEvent()
    /** Escucha iniciada */
    data object ListeningStarted : HotwordDetectionEvent()
    /** Escucha detenida */
    data object ListeningStopped : HotwordDetectionEvent()
}
