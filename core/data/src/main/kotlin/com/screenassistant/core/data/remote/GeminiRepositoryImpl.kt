package com.screenassistant.core.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.screenassistant.core.data.remote.openrouter.*
import com.screenassistant.core.data.util.ApiKeyProvider
import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.*
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.domain.usecase.math.MathEvaluator
import com.screenassistant.core.domain.usecase.math.MathExpressionNormalizer
import com.screenassistant.core.domain.usecase.math.MathFormatter
import com.screenassistant.core.domain.util.PromptCatalog
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "GeminiRepository"
private const val FALLO_TECNICO_GENERICO = "He tenido un problema técnico momentáneo. Inténtalo de nuevo en un segundo, Señor."
private const val MAX_FUNCTION_CALL_ITERATIONS = 3

@Singleton
class GeminiRepositoryImpl @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider,
    private val systemAction: SystemAction,
    private val memoryRepository: MemoryRepository,
    private val apiClient: OpenRouterApiClient
) : GeminiRepository {

    private var googleModel: GenerativeModel? = null
    private var lastUsedGoogleKey: String? = null

    private fun currentApiKey(): String = apiKeyProvider.getApiKey()

    private fun isGoogleKey(key: String): Boolean = key.startsWith("AIza")

    private val conversationHistory: MutableList<OpenRouterMessage> = mutableListOf()
    private val MAX_HISTORY_SIZE = 20 // J.A.R.V.I.S. v3.7: Ventana deslizante para fluidez

    override fun setLanguage(language: AssistantLanguage) {
        synchronized(this) {
            conversationHistory.clear()
        }
    }

    override suspend fun sendMessage(message: String, image: ImageData?): String? {
        val apiKey = currentApiKey()
        if (apiKey.isBlank()) {
            // V1 DBG-veredicto: log de PRESENCIA booleana (nunca el valor de la clave).
            Log.w(TAG, "clave vacía: keyBlank=true isFallback=${apiKeyProvider.isUsingFallback} — respuesta de clave no configurada")
            return "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo."
        }

        return if (isGoogleKey(apiKey)) {
            sendToGoogleGemini(message, image, apiKey)
        } else {
            sendToOpenRouter(message, image, apiKey)
        }
    }

    private suspend fun sendToGoogleGemini(message: String, image: ImageData?, apiKey: String): String? {
        return try {
            val bitmap = image?.let { BitmapFactory.decodeByteArray(it.data, 0, it.data.size) }
            
            if (googleModel == null || lastUsedGoogleKey != apiKey) {
                googleModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey,
                    generationConfig = generationConfig {
                        temperature = 0.7f
                        topK = 40
                        topP = 0.95f
                    }
                )
                lastUsedGoogleKey = apiKey
            }
            
            val model = googleModel!!

            val memories = memoryRepository.getAllMemories().first()
            val userName = memoryRepository.getUserName(memories) ?: "Señor"
            val assistantName = memoryRepository.getAssistantName(memories) ?: "J.A.R.V.I.S."
            val currentMode = systemAction.assistantMode.value

            val prompt = PromptCatalog.getGooglePrompt(
                assistantName = assistantName,
                userName = userName,
                mode = currentMode,
                message = message,
                memories = memories.map { it.content }
            )

            val response = if (bitmap != null) {
                model.generateContent(content {
                    image(bitmap)
                    text(prompt)
                })
            } else {
                model.generateContent(prompt)
            }

            response.text
        } catch (e: Exception) {
            Log.e(TAG, "Fallo en motor Google, reintentando vía OpenRouter", e)
            sendToOpenRouter(message, image, apiKey)
        }
    }

    private suspend fun sendToOpenRouter(message: String, image: ImageData?, apiKey: String): String? {
        val base64Image = image?.let { toBase64(it) }

        return try {
            val memories = memoryRepository.getAllMemories().first()
            val userName = memoryRepository.getUserName(memories) ?: "Señor"
            val assistantName = memoryRepository.getAssistantName(memories) ?: "J.A.R.V.I.S."
            val currentMode = systemAction.assistantMode.value
            
            val dynamicInstruction = PromptCatalog.getSystemInstruction(assistantName, userName, currentMode)

            val isInit = message == "ACTION_INIT_CONVERSATION"
            if (isInit) synchronized(this) { conversationHistory.clear() }

            val actualMessage = if (isInit) "Hola, preséntate." else message
            val userMessageContent = buildUserMessageContent(actualMessage, base64Image)
            val userMessage = OpenRouterMessage(role = "user", content = userMessageContent)

            synchronized(this) { 
                conversationHistory.add(userMessage)
                // Mantener solo los últimos N mensajes para fluidez (Fase J.A.R.V.I.S. v3.7)
                while (conversationHistory.size > MAX_HISTORY_SIZE) {
                    conversationHistory.removeAt(0)
                }
            }

            val request = OpenRouterRequest(
                model = "google/gemini-flash-1.5-8b", 
                messages = listOf(OpenRouterMessage(role = "system", content = JsonPrimitive(dynamicInstruction))) + 
                          synchronized(this) { conversationHistory.toList() },
                tools = OpenRouterToolCatalog.tools
            )

            var response = try {
                apiClient.chatCompletion(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OpenRouterException.RateLimited) {
                Log.e(TAG, "Error en llamada inicial OpenRouter", e)
                return "He hablado mucho por ahora. Espera unos segundos y volvemos a charlar."
            } catch (e: OpenRouterException.ApiKeyInvalid) {
                Log.e(TAG, "Error en llamada inicial OpenRouter", e)
                return "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo."
            } catch (e: OpenRouterException.ParsingError) {
                Log.e(TAG, "Error en llamada inicial OpenRouter", e)
                synchronized(this) { conversationHistory.clear() }
                return "He tenido un pequeño error de formato. Por favor, ¿podrías repetirme lo último?"
            } catch (e: Exception) {
                Log.e(TAG, "Error en llamada inicial OpenRouter", e)
                return FALLO_TECNICO_GENERICO
            }

            // Procesar Tool Calls
            var iterations = 0
            while (hasToolCalls(response) && iterations < MAX_FUNCTION_CALL_ITERATIONS) {
                iterations++
                val assistantMessage = extractAssistantMessage(response) ?: break
                synchronized(this) { conversationHistory.add(assistantMessage) }

                val toolResults = executeToolCalls(assistantMessage.toolCalls ?: emptyList())
                if (toolResults.isEmpty()) break

                synchronized(this) {
                    for (result in toolResults) {
                        conversationHistory.add(
                            OpenRouterMessage(role = "tool", content = JsonPrimitive(result.content), toolCallId = result.toolCallId)
                        )
                    }
                }

                val nextRequest = OpenRouterRequest(
                    model = "google/gemini-flash-1.5-8b",
                    messages = listOf(OpenRouterMessage(role = "system", content = JsonPrimitive(dynamicInstruction))) + 
                              synchronized(this) { conversationHistory.toList() },
                    tools = OpenRouterToolCatalog.tools
                )
                response = try {
                    apiClient.chatCompletion(nextRequest)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OpenRouterException.RateLimited) {
                    Log.e(TAG, "Error en llamada de seguimiento OpenRouter", e)
                    return "He hablado mucho por ahora. Espera unos segundos y volvemos a charlar."
                } catch (e: OpenRouterException.ApiKeyInvalid) {
                    Log.e(TAG, "Error en llamada de seguimiento OpenRouter", e)
                    return "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo."
                } catch (e: OpenRouterException.ParsingError) {
                    Log.e(TAG, "Error en llamada de seguimiento OpenRouter", e)
                    synchronized(this) { conversationHistory.clear() }
                    return "He tenido un pequeño error de formato. Por favor, ¿podrías repetirme lo último?"
                } catch (e: Exception) {
                    Log.e(TAG, "Error en llamada de seguimiento OpenRouter", e)
                    return FALLO_TECNICO_GENERICO
                }
            }

            val finalText = extractTextFromResponse(response)
            if (finalText != null && !finalText.startsWith("{")) {
                synchronized(this) { conversationHistory.add(OpenRouterMessage(role = "assistant", content = JsonPrimitive(finalText))) }
                finalText
            } else {
                "He procesado su solicitud, $userName."
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenRouterException.RateLimited) {
            Log.e(TAG, "Fallo en motor OpenRouter", e)
            "He hablado mucho por ahora. Espera unos segundos y volvemos a charlar."
        } catch (e: OpenRouterException.ApiKeyInvalid) {
            Log.e(TAG, "Fallo en motor OpenRouter", e)
            "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo."
        } catch (e: OpenRouterException.ParsingError) {
            Log.e(TAG, "Fallo en motor OpenRouter", e)
            synchronized(this) { conversationHistory.clear() }
            "He tenido un pequeño error de formato. Por favor, ¿podrías repetirme lo último?"
        } catch (e: Exception) {
            Log.e(TAG, "Fallo en motor OpenRouter", e)
            FALLO_TECNICO_GENERICO
        }
    }

    override fun streamMessage(message: String): Flow<String> = flow {
        val response = sendMessage(message, null)
        if (response != null) emit(response)
    }

    private fun buildUserMessageContent(text: String, base64Image: String?): JsonElement {
        if (base64Image == null) return JsonPrimitive(text)
        return buildJsonArray {
            add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(text)) })
            add(buildJsonObject { put("type", JsonPrimitive("image_url")); put("image_url",
                buildJsonObject { put("url", JsonPrimitive("data:image/jpeg;base64,$base64Image")) }) })
        }
    }

    private fun hasToolCalls(response: OpenRouterResponse): Boolean {
        val message = response.choices.firstOrNull()?.message ?: return false
        return !message.toolCalls.isNullOrEmpty()
    }

    private fun extractAssistantMessage(response: OpenRouterResponse): OpenRouterMessage? {
        return response.choices.firstOrNull()?.message
    }

    private fun extractTextFromResponse(response: OpenRouterResponse): String? {
        val content = response.choices.firstOrNull()?.message?.content
        return when (content) {
            is JsonPrimitive -> content.contentOrNull
            else -> content?.toString()
        }
    }

    private suspend fun executeToolCalls(toolCalls: List<OpenRouterToolCall>): List<ToolCallResult> {
        return toolCalls.mapNotNull { call ->
            try {
                val functionName = call.function.name
                val args = parseArguments(call.function.arguments)
                val toolCallId = call.id ?: return@mapNotNull null
                val result = when (functionName) {
                    "open_alarms" -> {
                        when (val r = systemAction.execute(SystemCommand.OpenAlarms)) {
                            is ActionResult.Success -> r.message
                            is ActionResult.Error -> r.reason
                        }
                    }
                    "set_alarm" -> {
                        val h = args["hour"]?.toIntOrNull()
                        val m = args["minute"]?.toIntOrNull()
                        if (h == null || m == null || h !in 0..23 || m !in 0..59) "Error: Hora o minuto inválidos."
                        else {
                            when (val r = systemAction.execute(SystemCommand.SetAlarm(h, m, args["label"]))) {
                                is ActionResult.Success -> r.message
                                is ActionResult.Error -> r.reason
                            }
                        }
                    }
                    "save_memory" -> {
                        val fact = args["fact"] ?: ""
                        if (fact.isNotBlank()) memoryRepository.saveMemory(fact)
                        "Dato guardado."
                    }
                    "search_google" -> {
                        val query = args["query"] ?: ""
                        if (query.isNotBlank()) {
                            when (val r = systemAction.execute(SystemCommand.SearchGoogle(query))) {
                                is ActionResult.Success -> r.message
                                is ActionResult.Error -> r.reason
                            }
                        } else "Falta el término de búsqueda."
                    }
                    "open_app" -> {
                        val q = args["app_name"] ?: args["package_name"] ?: ""
                        when (val r = systemAction.execute(SystemCommand.OpenApp(q))) {
                            is ActionResult.Success -> r.message
                            is ActionResult.Error -> r.reason
                        }
                    }
                    "open_youtube" -> {
                        val q = args["query"]?.takeIf { it.isNotBlank() }
                        when (val r = systemAction.execute(SystemCommand.OpenYouTube(q))) {
                            is ActionResult.Success -> r.message
                            is ActionResult.Error -> r.reason
                        }
                    }
                    "open_whatsapp" -> {
                        when (val r = systemAction.execute(SystemCommand.OpenWhatsApp)) {
                            is ActionResult.Success -> r.message
                            is ActionResult.Error -> r.reason
                        }
                    }
                    "play_music" -> {
                        val q = args["query"]?.takeIf { it.isNotBlank() }
                        when (val r = systemAction.execute(SystemCommand.PlayMusic(q))) {
                            is ActionResult.Success -> r.message
                            is ActionResult.Error -> r.reason
                        }
                    }
                    // MATH (ADR-MATH §7, P5): el LLM estructura, el evaluador
                    // calcula. Sanitizar: tope 200 + allowlist del evaluador;
                    // null-safe como las ramas vecinas (nunca se propaga).
                    "calculate" -> {
                        // Longitud excedida → Error (nunca truncar: `take(200)`
                        // evaluaría una expresión DISTINTA con valor erróneo).
                        val raw = args["expression"] ?: ""
                        val can = try {
                            MathExpressionNormalizer.toCanonical(raw, raw) ?: raw
                        } catch (_: Exception) { raw }
                        val r = try {
                            MathEvaluator.evaluate(can)
                        } catch (_: Exception) {
                            MathResult.Error(MathResult.EXPRESION_INVALIDA)
                        }
                        when (r) {
                            is MathResult.Success ->
                                "El resultado es ${MathFormatter.format(r.value)}."
                            is MathResult.Error -> when (r.reason) {
                                MathResult.DIVISION_POR_CERO ->
                                    "Error: No se puede dividir por cero."
                                MathResult.NUMERO_FUERA_DE_RANGO ->
                                    "Error: Número fuera de rango."
                                else -> "Error: Expresión inválida."
                            }
                        }
                    }
                    else -> "Función ejecutada."
                }
                ToolCallResult(toolCallId = toolCallId, content = result)
            } catch (_: Exception) { null }
        }
    }

    private fun parseArguments(argumentsJson: String): Map<String, String?> {
        return try {
            val jsonObject = Json.parseToJsonElement(argumentsJson).jsonObject
            jsonObject.mapValues { (_, value) ->
                when (value) {
                    is JsonPrimitive -> value.contentOrNull
                    else -> value.toString()
                }
            }
        } catch (_: Exception) { emptyMap() }
    }

    private fun toBase64(imageData: ImageData): String? {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(imageData.data, 0, imageData.data.size) ?: return null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }
}
