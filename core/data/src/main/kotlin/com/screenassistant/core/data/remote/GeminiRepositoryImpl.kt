package com.screenassistant.core.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.QuotaExceededException
import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.core.domain.repository.MemoryRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

private const val TAG = "GeminiRepository"

// M3 (Lote 11): fallo técnico genérico — fuente ÚNICA del repo. sendMessage y
// streamMessage comparten byte a byte el mismo texto (cero deriva entre caminos);
// el detalle del error va a logcat (Log.w), nunca al usuario (ADR-009). Constante
// top-level private: GeminiRepositoryImpl no tiene companion (fija la ambigüedad
// del diseño Lote 11 §2.1).
private const val FALLO_TECNICO_GENERICO =
    "He tenido un problema técnico momentáneo. Inténtalo de nuevo en un segundo."

// M24 (Lote 10): renombrado a *Impl — un nombre = un concepto (precedente
// ScreenContextRepositoryImpl). La interfaz vive en core:domain/repository.
class GeminiRepositoryImpl @Inject constructor(
    private val apiKeyProvider: com.screenassistant.core.data.util.ApiKeyProvider,
    private val systemAction: SystemAction,
    private val memoryRepository: MemoryRepository,
    private val modelFactory: GenerativeModelFactory
) : com.screenassistant.core.domain.repository.GeminiRepository {

    // Lee la key de forma defensiva: nunca debe tumbar el arranque de la app.
    // Se lee UNA VEZ por mensaje (contrato B4) y se propaga por parámetro a
    // buildModel/currentChat para que el bucle de function calls no la relea.
    private fun currentApiKey(): String = try {
        apiKeyProvider.getApiKey()
    } catch (e: Exception) {
        ""
    }

    // 1. Declaración de Funciones (D3, Lote 9): extraídas a GeminiFunctionCatalog
    // (core:data/remote) — fuente única de las 8 FunctionDeclaration + mapeo
    // nombre→wire del puente (guardián CorrespondenciaGeminiWireTest). El `when`
    // de EJECUCIÓN (abajo) se alimenta de los mismos nombres (inglés, vocabulario
    // del LLM por diseño).
    private val tools: List<Tool> = listOf(Tool(GeminiFunctionCatalog.declaraciones))

    private var systemInstruction: Content? = buildInstruction(com.screenassistant.core.domain.model.AssistantLanguage.SPANISH)

    // Única diferencia por idioma: la línea del idioma del prompt (el resto se mantiene).
    private fun buildInstruction(language: com.screenassistant.core.domain.model.AssistantLanguage): Content? = content {
        text("Eres una asistente digital amable y observadora. " +
             "Tu apariencia: joven, coleta alta negra, chaqueta de trekking blanca/gris. " +
             "Eres servicial y te encanta comentar lo que el usuario hace en pantalla. " +
             "Respuestas concisas y cálidas. " +
             if (language == com.screenassistant.core.domain.model.AssistantLanguage.ENGLISH) {
                 "User language (English by default)."
             } else {
                 "Idioma del usuario (español por defecto)."
             } + "\n\n" +
             "IDENTIDAD Y PERSONALIZACIÓN:\n" +
             "- Si no conoces tu nombre ni el del usuario, preséntate y pregunta amablemente ambos al inicio.\n" +
             "- Una vez que tengas los nombres, guárdalos como: 'Nombre del usuario: [nombre]' y 'Nombre de la asistente: [nombre]'.\n\n" +
             "PRIVACIDAD ESTRICTA:\n" +
             "- NO compartas datos del usuario con servicios externos.\n\n" +
             "MEMORIA Y APRENDIZAJE PROACTIVO:\n" +
             "- Usa 'save_memory' SILENCIOSAMENTE para guardar datos relevantes y hábitos detectados en pantalla.\n\n" +
             "CAPACIDADES:\n" +
             "Puedes controlar el teléfono: poner alarmas, abrir YouTube, WhatsApp, Música, Google y abrir aplicaciones.")
    }

    override fun setLanguage(language: com.screenassistant.core.domain.model.AssistantLanguage) {
        // Mismo monitor que currentChat: el chat es compartido overlay/pantalla.
        // lastApiKey SE MANTIENE: la key no cambió; chat=null fuerza el rebuild.
        synchronized(this) {
            systemInstruction = buildInstruction(language)
            chat = null
        }
    }

    private fun buildModel(apiKey: String): GenerativeModel? {
        if (apiKey.isBlank()) return null
        return modelFactory.create(apiKey, tools, systemInstruction)
    }

    private var chat: com.google.ai.client.generativeai.Chat? = null

    // Última key usada por el chat. Si cambia, el chat se reconstruye:
    // cada key tiene su propio hilo de conversación en el SDK.
    private var lastApiKey: String? = null

    // Synchronized único: la sesión de Gemini es compartida entre el overlay
    // (servicio) y el resto de consumidores; el SDK no es thread-safe para
    // conversaciones concurrentes (la pantalla de chat se eliminó en Lote 9-D2).
    private fun currentChat(apiKey: String): com.google.ai.client.generativeai.Chat? {
        synchronized(this) {
            if (apiKey != lastApiKey) {
                chat = null
                lastApiKey = apiKey
            }
            if (chat == null) {
                chat = try {
                    buildModel(apiKey)?.startChat()
                } catch (e: Exception) {
                    null
                }
            }
            return chat
        }
    }

    override suspend fun sendMessage(message: String, image: ImageData?): String? {
        // Una sola lectura de la key por mensaje (contrato B4): se propaga por
        // parámetro a buildModel/currentChat, el bucle NO vuelve a leerla.
        val apiKey = currentApiKey()

        // Sin API key configurada: mensaje claro en lugar de excepción.
        if (apiKey.isBlank()) {
            return "Aún no tengo mi clave de IA configurada. Revisa la configuración de la API key e inténtalo de nuevo."
        }

        // Convertir ImageData a Bitmap si es necesario
        val bitmap = image?.let { toBitmap(it) }

        return try {
            val memories = memoryRepository.getAllMemories().first()
            val userName = memoryRepository.getUserName(memories)
            val assistantName = memoryRepository.getAssistantName(memories)

            val isInit = message == "ACTION_INIT_CONVERSATION"
            if (isInit) chat = null

            val actualMessage = if (isInit) {
                if (userName == null || assistantName == null) {
                    "Hola. Preséntate brevemente y pide nombres para ambos."
                } else {
                    "Hola de nuevo, $userName. Soy $assistantName."
                }
            } else message

            val memoryContext = if (memories.isNotEmpty() && !isInit) {
                "\n\n[MEMORIA RECIENTE]: " + memories.take(5).joinToString(", ") { it.content }
            } else ""

            val inputContent = content {
                bitmap?.let { image(it) }
                text(actualMessage + memoryContext)
            }

            var response = currentChat(apiKey)?.sendMessage(inputContent)
                ?: return "No pude inicializar el modelo de IA. Inténtalo de nuevo en un momento."

            var iterations = 0
            while (response.functionCalls.isNotEmpty() && iterations < 3) {
                iterations++
                val functionResponses = response.functionCalls.mapNotNull { call ->
                    try {
                        val result = when (call.name) {
                            "open_alarms" -> {
                                val actionResult = systemAction.execute(SystemCommand.OpenAlarms)
                                when (actionResult) {
                                    is ActionResult.Success -> actionResult.message
                                    is ActionResult.Error -> actionResult.reason
                                }
                            }
                            "set_alarm" -> {
                                // B2: args inválidos (no numéricos o fuera de rango) →
                                // error claro SIN ejecutar la acción. Antes el fallback a 0
                                // programaba una alarma a las 0:00 silenciosamente.
                                val h = call.args["hour"]?.toIntOrNull()
                                val m = call.args["minute"]?.toIntOrNull()
                                if (h == null || m == null || h !in 0..23 || m !in 0..59) {
                                    "Error: Hora o minuto inválidos."
                                } else {
                                    val actionResult = systemAction.execute(
                                        SystemCommand.SetAlarm(h, m, call.args["label"])
                                    )
                                    when (actionResult) {
                                        is ActionResult.Success -> actionResult.message
                                        is ActionResult.Error -> actionResult.reason
                                    }
                                }
                            }
                            "search_google" -> {
                                val actionResult = systemAction.execute(
                                    SystemCommand.SearchGoogle(call.args["query"] ?: "")
                                )
                                when (actionResult) {
                                    is ActionResult.Success -> actionResult.message
                                    is ActionResult.Error -> actionResult.reason
                                }
                            }
                            "open_youtube" -> {
                                val actionResult = systemAction.execute(
                                    SystemCommand.OpenYouTube(call.args["query"])
                                )
                                when (actionResult) {
                                    is ActionResult.Success -> actionResult.message
                                    is ActionResult.Error -> actionResult.reason
                                }
                            }
                            "open_app" -> {
                                val query = call.args["app_name"] ?: call.args["package_name"] ?: ""
                                val actionResult = systemAction.execute(SystemCommand.OpenApp(query))
                                when (actionResult) {
                                    is ActionResult.Success -> actionResult.message
                                    is ActionResult.Error -> actionResult.reason
                                }
                            }
                            "open_whatsapp" -> {
                                val actionResult = systemAction.execute(SystemCommand.OpenWhatsApp)
                                when (actionResult) {
                                    is ActionResult.Success -> actionResult.message
                                    is ActionResult.Error -> actionResult.reason
                                }
                            }
                            "play_music" -> {
                                val actionResult = systemAction.execute(
                                    SystemCommand.PlayMusic(call.args["query"])
                                )
                                when (actionResult) {
                                    is ActionResult.Success -> actionResult.message
                                    is ActionResult.Error -> actionResult.reason
                                }
                            }
                            "save_memory" -> {
                                val fact = call.args["fact"] ?: ""
                                if (fact.isNotBlank()) memoryRepository.saveMemory(fact)
                                "Dato guardado."
                            }
                            else -> "No reconocido."
                        }

                        val resultJson = org.json.JSONObject().apply { put("result", result) }
                        FunctionResponsePart(call.name, resultJson)
                    } catch (_: Exception) {
                        null
                    }
                }

                if (functionResponses.isEmpty()) break

                response = currentChat(apiKey)?.sendMessage(
                    content(role = "function") {
                        functionResponses.forEach { part(it) }
                    }
                ) ?: break
            }

            response.text ?: "Entendido."

        } catch (e: CancellationException) {
            // B5 (Lote 11, decisión A): la cancelación del scope NUNCA se traga —
            // el catch genérico la convertiría en mensaje y completaría el job.
            throw e
        } catch (e: QuotaExceededException) {
            "He hablado mucho por ahora. Espera unos segundos y volvemos a charlar."
        } catch (e: Exception) {
            val msg = e.toString()
            if (msg.contains("serialization") || msg.contains("format") || msg.contains("Field")) {
                chat = null
                "He tenido un pequeño error de formato. Por favor, ¿podrías repetirme lo último?"
            } else {
                FALLO_TECNICO_GENERICO
            }
        }
    }

    override fun streamMessage(message: String): Flow<String> {
        // Una sola lectura de la key por mensaje (contrato B4).
        val apiKey = currentApiKey()

        // Sin API key configurada: flujo de fallback sin tocar la factory ni la red.
        if (apiKey.isBlank()) {
            return kotlinx.coroutines.flow.flowOf("No pude inicializar el modelo de IA.")
        }
        return try {
            currentChat(apiKey)?.sendMessageStream(message)?.mapNotNull { it.text }
                // M3: el try/catch exterior solo cubre la CONSTRUCCIÓN del flujo; un
                // IOException de red durante la RECOLECCIÓN se captura aquí (emit en
                // lugar de propagar). El resto (incluida CancellationException) se
                // RE-LANZA — contrato del repo.
                ?.catch { e: Throwable ->
                    if (e is java.io.IOException) {
                        Log.w(TAG, "Error en streaming", e)
                        emit(FALLO_TECNICO_GENERICO)
                    } else {
                        throw e
                    }
                }
                ?: kotlinx.coroutines.flow.flowOf("No pude inicializar el modelo de IA.")
        } catch (e: CancellationException) {
            // B5 (Lote 11, decisión A): mismo contrato que sendMessage — la
            // cancelación se propaga, nunca se convierte en flujo de error.
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error en streaming", e)
            flowOf(FALLO_TECNICO_GENERICO)
        }
    }

    /**
     * Convierte ImageData (dominio puro) a android.graphics.Bitmap para la API de Gemini.
     */
    private fun toBitmap(imageData: ImageData): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(imageData.data, 0, imageData.data.size)
        } catch (e: Exception) {
            null
        }
    }
}
