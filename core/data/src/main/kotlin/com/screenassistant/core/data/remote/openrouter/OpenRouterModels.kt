package com.screenassistant.core.data.remote.openrouter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Request ──────────────────────────────────────────────────────────────

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val tools: List<OpenRouterToolDefinition>? = null,
    val stream: Boolean = false
)

// ── Messages ─────────────────────────────────────────────────────────────

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: JsonElement? = null,
    @SerialName("tool_calls")
    val toolCalls: List<OpenRouterToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

// ── Tool definitions ─────────────────────────────────────────────────────

@Serializable
data class OpenRouterToolDefinition(
    val type: String = "function",
    val function: OpenRouterFunction
)

@Serializable
data class OpenRouterFunction(
    val name: String,
    val description: String,
    val parameters: OpenRouterFunctionParameters
)

@Serializable
data class OpenRouterFunctionParameters(
    val type: String = "object",
    val properties: Map<String, OpenRouterParameterSchema>,
    val required: List<String> = emptyList()
)

@Serializable
data class OpenRouterParameterSchema(
    val type: String,
    val description: String? = null
)

// ── Response ─────────────────────────────────────────────────────────────

@Serializable
data class OpenRouterResponse(
    val id: String? = null,
    val choices: List<OpenRouterChoice> = emptyList(),
    val error: OpenRouterError? = null
)

@Serializable
data class OpenRouterChoice(
    val message: OpenRouterMessage? = null,
    val delta: OpenRouterDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class OpenRouterDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class OpenRouterError(
    val message: String = "",
    val type: String = ""
)

// ── Tool calls (del LLM) ────────────────────────────────────────────────

@Serializable
data class OpenRouterToolCall(
    val id: String? = null,
    val type: String = "function",
    val function: OpenRouterFunctionCall
)

@Serializable
data class OpenRouterFunctionCall(
    val name: String,
    val arguments: String
)

// ── Tool call result (para enviar al LLM) ────────────────────────────────

data class ToolCallResult(
    val toolCallId: String,
    val content: String
)

// ── Exceptions ───────────────────────────────────────────────────────────

sealed class OpenRouterException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    class ApiKeyInvalid(message: String) : OpenRouterException(message)
    class RateLimited(message: String) : OpenRouterException(message)
    class ServerError(message: String, code: Int, cause: Throwable? = null) :
        OpenRouterException("HTTP $code: $message", cause)
    class ParsingError(message: String, cause: Throwable? = null) :
        OpenRouterException(message, cause)
    class NetworkError(message: String, cause: Throwable? = null) :
        OpenRouterException(message, cause)
}
