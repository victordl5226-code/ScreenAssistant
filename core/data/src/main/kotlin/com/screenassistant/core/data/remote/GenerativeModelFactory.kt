package com.screenassistant.core.data.remote

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.Tool

/** Separa la construcción del modelo Gemini de su uso. */
interface GenerativeModelFactory {
    fun create(apiKey: String, tools: List<Tool>, systemInstruction: Content?): GenerativeModel?
}
