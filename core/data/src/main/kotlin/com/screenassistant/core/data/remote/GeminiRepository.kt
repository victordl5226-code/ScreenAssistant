package com.screenassistant.core.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.QuotaExceededException
import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.core.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class GeminiRepository @Inject constructor(
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

    // 1. Declaración de Funciones (Uso de List<Schema<*>> según SDK 0.9.0)
    private val openAlarmsTool = FunctionDeclaration(
        name = "open_alarms",
        description = "Abre la aplicación de reloj en la sección de alarmas.",
        parameters = emptyList(),
        requiredParameters = emptyList()
    )

    private val setAlarmTool = FunctionDeclaration(
        name = "set_alarm",
        description = "Configura una nueva alarma.",
        parameters = listOf(
            Schema.int("hour", "Hora (0-23)"),
            Schema.int("minute", "Minuto (0-59)"),
            Schema.str("label", "Nombre de la alarma")
        ),
        requiredParameters = listOf("hour", "minute")
    )

    private val searchGoogleTool = FunctionDeclaration(
        name = "search_google",
        description = "Busca algo en Google.",
        parameters = listOf(
            Schema.str("query", "Término de búsqueda")
        ),
        requiredParameters = listOf("query")
    )

    private val openYouTubeTool = FunctionDeclaration(
        name = "open_youtube",
        description = "Abre YouTube.",
        parameters = listOf(
            Schema.str("query", "Búsqueda en YouTube")
        ),
        requiredParameters = emptyList()
    )

    private val openWhatsAppTool = FunctionDeclaration(
        name = "open_whatsapp",
        description = "Abre WhatsApp.",
        parameters = emptyList(),
        requiredParameters = emptyList()
    )

    private val playMusicTool = FunctionDeclaration(
        name = "play_music",
        description = "Reproduce música.",
        parameters = listOf(
            Schema.str("query", "Canción o artista")
        ),
        requiredParameters = emptyList()
    )

    private val saveMemoryTool = FunctionDeclaration(
        name = "save_memory",
        description = "Guarda un dato importante sobre el usuario.",
        parameters = listOf(
            Schema.str("fact", "El dato a recordar")
        ),
        requiredParameters = listOf("fact")
    )

    private val openAppTool = FunctionDeclaration(
        name = "open_app",
        description = "Abre una aplicación instalada en el dispositivo. El valor puede ser el nombre visible de la app (p. ej. \"WhatsApp\") o su package name (p. ej. \"com.whatsapp\").",
        parameters = listOf(
            Schema.str("app_name", "Nombre visible o package name de la aplicación a abrir")
        ),
        requiredParameters = listOf("app_name")
    )

    // 2. Configuración del Modelo (Gemini 2.0 Flash - El punto de equilibrio entre SDK y Servidor)
    // La construcción LAZY queda delegada a GenerativeModelFactory: no se toca Gemini
    // ni la red hasta el primer mensaje real (ver buildModel()).
    private val tools: List<Tool> = listOf(Tool(listOf(
        openAlarmsTool, setAlarmTool, searchGoogleTool,
        openYouTubeTool, openWhatsAppTool, playMusicTool,
        saveMemoryTool, openAppTool
    )))

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

    // Synchronized único: el chat es compartido entre overlay (servicio) y
    // pantalla de chat (app), y ambos pueden enviar mensajes en paralelo.
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
                                val h = call.args["hour"]?.toIntOrNull() ?: 0
                                val m = call.args["minute"]?.toIntOrNull() ?: 0
                                val actionResult = systemAction.execute(
                                    SystemCommand.SetAlarm(h, m, call.args["label"])
                                )
                                when (actionResult) {
                                    is ActionResult.Success -> actionResult.message
                                    is ActionResult.Error -> actionResult.reason
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

        } catch (e: QuotaExceededException) {
            "He hablado mucho por ahora. Espera unos segundos y volvemos a charlar."
        } catch (e: Exception) {
            val msg = e.toString()
            if (msg.contains("serialization") || msg.contains("format") || msg.contains("Field")) {
                chat = null
                "He tenido un pequeño error de formato. Por favor, ¿podrías repetirme lo último?"
            } else {
                "He tenido un problema técnico momentáneo. Inténtalo de nuevo en un segundo."
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
                ?: kotlinx.coroutines.flow.flowOf("No pude inicializar el modelo de IA.")
        } catch (e: Exception) {
            kotlinx.coroutines.flow.flowOf("Error en streaming: ${e.message}")
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
