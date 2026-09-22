package com.screenassistant.core.data.remote.openrouter

import android.util.Log
import com.screenassistant.core.data.util.ApiKeyProvider
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Implementación por defecto de [OpenRouterApiClient] con OkHttp directo.
 *
 * C1: baseUrl y model son parámetros del constructor (NO constants).
 * C5: sin okhttp-sse — parseo manual de SSE via BufferedReader.
 * C3: mapeo de errores HTTP a [OpenRouterException].
 */
@Singleton
class DefaultOpenRouterApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val apiKeyProvider: ApiKeyProvider,
    @Named("openrouter_base_url") private val baseUrl: String,
    @Named("openrouter_model") private val model: String
) : OpenRouterApiClient {

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private companion object {
        private const val TAG = "OpenRouterClient"
    }

    /**
     * Construye el header Authorization Bearer a partir de la API key almacenada.
     * Si la key está vacía, retorna null (el request irá sin auth y recibirá 401).
     */
    private fun buildAuthHeader(): String? {
        val key = apiKeyProvider.getApiKey()
        if (key.isBlank()) {
            Log.w(TAG, "buildAuthHeader: API key is blank/empty")
            return null
        }
        val masked = if (key.length > 8) "${key.take(4)}...${key.takeLast(4)}" else "***"
        Log.d(TAG, "buildAuthHeader: key present (${key.length} chars) preview=$masked")
        return "Bearer $key"
    }

    override suspend fun chatCompletion(request: OpenRouterRequest): OpenRouterResponse {
        val requestWithModel = request.copy(model = model)
        val requestBody = json.encodeToString(OpenRouterRequest.serializer(), requestWithModel)

        val requestBuilder = Request.Builder()
            .url("${baseUrl}chat/completions")
            .post(requestBody.toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://github.com/screenassistant")
            .header("X-Title", "ScreenAssistant")

        // Agregar Authorization header si la key está configurada
        buildAuthHeader()?.let { requestBuilder.header("Authorization", it) }

        val httpRequest = requestBuilder.build()

        Log.d(TAG, "chatCompletion: sending request to ${httpRequest.url}, model=$model, body length=${requestBody.length}")

        return try {
            val httpResponse = okHttpClient.newCall(httpRequest).execute()
            val body = httpResponse.body?.string() ?: ""

            Log.d(TAG, "chatCompletion: HTTP ${httpResponse.code} (body length=${body.length})")

            when (httpResponse.code) {
                200 -> {
                    json.decodeFromString(OpenRouterResponse.serializer(), body)
                }
                401 -> {
                    Log.w(TAG, "chatCompletion: 401 Unauthorized body=$body")
                    throw OpenRouterException.ApiKeyInvalid(
                        "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo."
                    )
                }
                429 -> {
                    Log.w(TAG, "chatCompletion: 429 RateLimited body=$body")
                    throw OpenRouterException.RateLimited(
                        "He hablado mucho por ahora. Espera unos segundos y volvemos a charlar."
                    )
                }
                500, 502, 503 -> {
                    Log.w(TAG, "chatCompletion: ${httpResponse.code} ServerError body=$body")
                    throw OpenRouterException.ServerError(
                        "He tenido un problema técnico momentáneo. Inténtalo de nuevo en un segundo.",
                        httpResponse.code
                    )
                }
                else -> {
                    Log.w(TAG, "chatCompletion: ${httpResponse.code} UnknownError body=$body")
                    throw OpenRouterException.ServerError(
                        "Error HTTP ${httpResponse.code}: $body",
                        httpResponse.code
                    )
                }
            }
        } catch (e: OpenRouterException) {
            throw e
        } catch (e: IOException) {
            throw OpenRouterException.NetworkError(
                "Error de red: ${e.message}",
                e
            )
        } catch (e: CancellationException) {
            throw e  // B7: la cancelación nunca se traga
        } catch (e: Exception) {
            throw OpenRouterException.ParsingError(
                "Error de formato: ${e.message}",
                e
            )
        }
    }

    override fun chatCompletionStream(request: OpenRouterRequest): Flow<OpenRouterResponse> = flow {
        val requestWithModel = request.copy(model = model, stream = true)
        val requestBody = json.encodeToString(OpenRouterRequest.serializer(), requestWithModel)

        val requestBuilder = Request.Builder()
            .url("${baseUrl}chat/completions")
            .post(requestBody.toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://github.com/screenassistant")
            .header("X-Title", "ScreenAssistant")

        // Agregar Authorization header si la key está configurada
        buildAuthHeader()?.let { requestBuilder.header("Authorization", it) }

        val httpRequest = requestBuilder.build()

        val httpResponse = okHttpClient.newCall(httpRequest).execute()

        when (httpResponse.code) {
            200 -> {
                val reader = httpResponse.body?.byteStream()?.bufferedReader()
                    ?: throw OpenRouterException.ParsingError("Respuesta vacía del servidor")

                reader.use { bufferedReader ->
                    parseSSEStream(bufferedReader)
                }
            }
            401 -> throw OpenRouterException.ApiKeyInvalid(
                "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo."
            )
            429 -> throw OpenRouterException.RateLimited(
                "He hablado mucho por ahora. Espera unos segundos y volvemos a charlar."
            )
            500, 502, 503 -> throw OpenRouterException.ServerError(
                "He tenido un problema técnico momentáneo. Inténtalo de nuevo en un segundo.",
                httpResponse.code
            )
            else -> throw OpenRouterException.ServerError(
                "Error HTTP ${httpResponse.code}",
                httpResponse.code
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Parsea líneas SSE del servidor dentro del scope del flow builder.
     * Formato:
     * ```
     * data: {"id":"...","choices":[{"delta":{"content":"hola"}}]}
     * data: [DONE]
     * ```
     *
     * Cada línea "data:" con JSON válido se emite como [OpenRouterResponse].
     * La línea "data: [DONE]" indica fin del stream.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<OpenRouterResponse>.parseSSEStream(
        reader: BufferedReader
    ) {
        while (true) {
            val line = reader.readLine() ?: break

            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                try {
                    val chunk = json.parseToJsonElement(data).jsonObject
                    val response = parseChunk(chunk)
                    if (response != null) {
                        emit(response)
                    }
                } catch (e: CancellationException) {
                    throw e  // B7: la cancelación nunca se traga
                } catch (_: Exception) {
                    // Línea malformada: la ignoramos (el stream puede tener
                    // comentarios o líneas vacías intercaladas).
                }
            }
        }
    }

    /**
     * Parsea un chunk JSON del SSE en [OpenRouterResponse].
     * Extrae id, choices (message/delta), y finish_reason.
     */
    private fun parseChunk(chunk: JsonObject): OpenRouterResponse? {
        val id = chunk["id"]?.jsonPrimitive?.contentOrNull
        val choicesArray = chunk["choices"]?.toString()?.let {
            try {
                json.decodeFromString<List<JsonObject>>(it)
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()

        if (choicesArray.isEmpty()) return null

        val choices = choicesArray.mapNotNull { choiceObj ->
            val messageObj = choiceObj["message"]?.jsonObject
            val deltaObj = choiceObj["delta"]?.jsonObject
            val finishReason = choiceObj["finish_reason"]?.jsonPrimitive?.contentOrNull

            val message = messageObj?.let { parseMessage(it) }
            val delta = deltaObj?.let { parseDelta(it) }

            OpenRouterChoice(
                message = message,
                delta = delta,
                finishReason = finishReason
            )
        }

        return OpenRouterResponse(id = id, choices = choices)
    }

    private fun parseMessage(obj: JsonObject): OpenRouterMessage? {
        val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: return null
        val content = obj["content"]
        val toolCalls = obj["tool_calls"]?.toString()?.let {
            try {
                json.decodeFromString<List<OpenRouterToolCall>>(it)
            } catch (_: Exception) {
                null
            }
        }
        val toolCallId = obj["tool_call_id"]?.jsonPrimitive?.contentOrNull

        return OpenRouterMessage(
            role = role,
            content = content,
            toolCalls = toolCalls,
            toolCallId = toolCallId
        )
    }

    private fun parseDelta(obj: JsonObject): OpenRouterDelta {
        val role = obj["role"]?.jsonPrimitive?.contentOrNull
        val content = obj["content"]?.jsonPrimitive?.contentOrNull
        return OpenRouterDelta(role = role, content = content)
    }
}
