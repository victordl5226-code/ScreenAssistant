package com.screenassistant.core.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Interfaz abstracta para captura de audio.
 * Permite testing sin hardware real.
 */
interface AudioCapture {
    /**
     * Stream de chunks de audio (16-bit PCM, 16kHz mono).
     */
    fun audioStream(): Flow<AudioChunk>
    
    /**
     * Inicia la captura de audio.
     */
    fun startCapture()
    
    /**
     * Detiene la captura de audio.
     */
    fun stopCapture()
    
    /**
     * Indica si está capturando.
     */
    fun isCapturing(): Boolean
}

/**
 * Chunk de audio capturado.
 */
data class AudioChunk(
    val samples: ShortArray,
    val sampleRate: Int = 16000,
    val timestamp: Long = System.currentTimeMillis()
)
