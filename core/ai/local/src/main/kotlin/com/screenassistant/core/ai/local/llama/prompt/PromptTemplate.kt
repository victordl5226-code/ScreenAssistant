package com.screenassistant.core.ai.local.llama.prompt

/**
 * Plantillas de prompt para diferentes modelos de chat.
 *
 * Cada modelo tiene un formato de chat específico. Esta clase
 * encapsula la lógica de formateo para cada tipo de modelo.
 */
object PromptTemplate {

    // TinyLlama tags
    private val TAG_SYS = "<" + "|system|>"
    private val TAG_USR = "<" + "|user|>"
    private val TAG_AST = "<" + "|assistant|>"

    // ChatML tags
    private val TAG_IM_START = "<" + "im_start>"
    private val TAG_IM_END = "<" + "im_end>"

    /**
     * Formato de chat para TinyLlama.
     *
     * Usa el formato con tags de sistema/usuario/asistente.
     */
    fun tinyLlamaChat(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.appendLine(TAG_SYS)
        sb.appendLine(systemPrompt)
        for ((user, assistant) in conversationHistory) {
            sb.appendLine(TAG_USR)
            sb.appendLine(user)
            sb.appendLine(TAG_AST)
            sb.appendLine(assistant)
        }
        sb.appendLine(TAG_USR)
        sb.appendLine(userMessage)
        sb.appendLine(TAG_AST)
        return sb.toString()
    }

    /**
     * Formato de chat ChatML (usado por muchos modelos).
     */
    fun chatML(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.appendLine("$TAG_IM_START system")
        sb.appendLine(systemPrompt)
        sb.appendLine(TAG_IM_END)
        for ((user, assistant) in conversationHistory) {
            sb.appendLine("$TAG_IM_START user")
            sb.appendLine(user)
            sb.appendLine(TAG_IM_END)
            sb.appendLine("$TAG_IM_START assistant")
            sb.appendLine(assistant)
            sb.appendLine(TAG_IM_END)
        }
        sb.appendLine("$TAG_IM_START user")
        sb.appendLine(userMessage)
        sb.appendLine(TAG_IM_END)
        sb.appendLine("$TAG_IM_START assistant")
        return sb.toString()
    }

    /**
     * Detecta el template adecuado según el modelo.
     *
     * @param modelId ID del modelo
     * @return Función de formateo
     */
    fun detectTemplate(modelId: String): (String, String, List<Pair<String, String>>) -> String {
        return when {
            modelId.contains("tinyllama", ignoreCase = true) -> ::tinyLlamaChat
            modelId.contains("chatml", ignoreCase = true) -> ::chatML
            else -> ::tinyLlamaChat
        }
    }
}
