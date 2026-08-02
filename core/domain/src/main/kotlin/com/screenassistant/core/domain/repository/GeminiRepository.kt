package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.model.ImageData
import kotlinx.coroutines.flow.Flow

interface GeminiRepository {
    suspend fun sendMessage(message: String, image: ImageData? = null): String?
    fun streamMessage(message: String): Flow<String>

    /** Cambia el idioma de la instrucción del sistema y resetea el chat (sin red). */
    fun setLanguage(language: AssistantLanguage)
}
