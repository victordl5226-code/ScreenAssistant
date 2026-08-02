package com.screenassistant.core.data.remote

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.Tool
import javax.inject.Inject

class DefaultGenerativeModelFactory @Inject constructor() : GenerativeModelFactory {
    override fun create(apiKey: String, tools: List<Tool>, systemInstruction: Content?): GenerativeModel? {
        // Sin key configurada nunca se toca el SDK (ni construcción ni red).
        if (apiKey.isBlank()) return null
        return try {
            GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey,
                tools = tools,
                systemInstruction = systemInstruction
            )
        } catch (e: Exception) {
            null
        }
    }
}
