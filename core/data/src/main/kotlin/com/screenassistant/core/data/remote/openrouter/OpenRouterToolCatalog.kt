package com.screenassistant.core.data.remote.openrouter

/**
 * Catálogo del canal LLM (formato OpenAI-compatible): las 9 tools que
 * OpenRouter expone como function calling. Mapeo nombre LLM → wire del
 * puente Tasker (AccionRegistry) en [wirePorNombre], SALVO [sinWire].
 *
 * Guardián de paridad: CorrespondenciaGeminiWireTest debe mantenerse verde
 * tras este cambio.
 *
 * MATH (ADR-MATH §7, P5): `calculate` es cálculo puro, NO acción de
 * dispositivo → SIN wire Tasker (precedente: el cálculo no necesita puente;
 * el evaluador local exacto produce el valor, el LLM solo estructura).
 * Exclusión documentada aquí y en el guardián — NO crear wire `calcular`.
 */
object OpenRouterToolCatalog {

    val tools: List<OpenRouterToolDefinition> = listOf(
        OpenRouterToolDefinition(
            function = OpenRouterFunction(
                name = "open_alarms",
                description = "Abre la aplicación de reloj en la sección de alarmas.",
                parameters = OpenRouterFunctionParameters(
                    properties = emptyMap(),
                    required = emptyList()
                )
            )
        ),
        OpenRouterToolDefinition(
            function = OpenRouterFunction(
                name = "set_alarm",
                description = "Configura una nueva alarma.",
                parameters = OpenRouterFunctionParameters(
                    properties = mapOf(
                        "hour" to OpenRouterParameterSchema(
                            type = "integer",
                            description = "Hora (0-23)"
                        ),
                        "minute" to OpenRouterParameterSchema(
                            type = "integer",
                            description = "Minuto (0-59)"
                        ),
                        "label" to OpenRouterParameterSchema(
                            type = "string",
                            description = "Nombre de la alarma"
                        )
                    ),
                    required = listOf("hour", "minute")
                )
            )
        ),
        OpenRouterToolDefinition(
            function = OpenRouterFunction(
                name = "search_google",
                description = "Busca algo en Google.",
                parameters = OpenRouterFunctionParameters(
                    properties = mapOf(
                        "query" to OpenRouterParameterSchema(
                            type = "string",
                            description = "Término de búsqueda"
                        )
                    ),
                    required = listOf("query")
                )
            )
        ),
        OpenRouterToolDefinition(
            function = OpenRouterFunction(
                name = "open_youtube",
                description = "Abre YouTube.",
                parameters = OpenRouterFunctionParameters(
                    properties = mapOf(
                        "query" to OpenRouterParameterSchema(
                            type = "string",
                            description = "Búsqueda en YouTube"
                        )
                    ),
                    required = emptyList()
                )
            )
        ),
        OpenRouterToolDefinition(
            function = OpenRouterFunction(
                name = "open_whatsapp",
                description = "Abre WhatsApp.",
                parameters = OpenRouterFunctionParameters(
                    properties = emptyMap(),
                    required = emptyList()
                )
            )
        ),
        OpenRouterToolDefinition(
            function = OpenRouterFunction(
                name = "play_music",
                description = "Reproduce música.",
                parameters = OpenRouterFunctionParameters(
                    properties = mapOf(
                        "query" to OpenRouterParameterSchema(
                            type = "string",
                            description = "Canción o artista"
                        )
                    ),
                    required = emptyList()
                )
            )
        ),
        OpenRouterToolDefinition(
            function = OpenRouterFunction(
                name = "save_memory",
                description = "Guarda un dato importante sobre el usuario.",
                parameters = OpenRouterFunctionParameters(
                    properties = mapOf(
                        "fact" to OpenRouterParameterSchema(
                            type = "string",
                            description = "El dato a recordar"
                        )
                    ),
                    required = listOf("fact")
                )
            )
        ),
        OpenRouterToolDefinition(
            function = OpenRouterFunction(
                name = "open_app",
                description = "Abre una aplicación instalada en el dispositivo. El valor puede ser el nombre visible de la app (p. ej. \"WhatsApp\") o su package name (p. ej. \"com.whatsapp\").",
                parameters = OpenRouterFunctionParameters(
                    properties = mapOf(
                        "app_name" to OpenRouterParameterSchema(
                            type = "string",
                            description = "Nombre visible o package name de la aplicación a abrir"
                        )
                    ),
                    required = listOf("app_name")
                )
            )
        ),
        // MATH (ADR-MATH §7, P5): 9ª tool — el LLM estructura, MathEvaluator calcula.
        OpenRouterToolDefinition(
            function = OpenRouterFunction(
                name = "calculate",
                description = "Resuelve una expresión aritmética en español libre. Devuelve la expresión canónica; el cálculo lo hace el evaluador local exacto, no el LLM.",
                parameters = OpenRouterFunctionParameters(
                    properties = mapOf(
                        "expression" to OpenRouterParameterSchema(
                            type = "string",
                            description = "Expresión aritmética canónica o en español (p. ej. '15% de 200', 'raiz de 81', '(2+3)*4'). Solo aritmética."
                        )
                    ),
                    required = listOf("expression")
                )
            )
        )
    )

    /** Los 9 nombres LLM (fuente única para el guardián de paridad). */
    val nombres: Set<String> get() = tools.map { it.function.name }.toSet()

    /**
     * Funciones LLM SIN wire Tasker (exclusión documentada, ADR-MATH §7):
     * `calculate` es cálculo puro — no hay acción de dispositivo que puentear.
     */
    val sinWire: Set<String> = setOf("calculate")

    /**
     * Mapeo nombre LLM → wire del puente Tasker (AccionRegistry).
     * save_memory: el wire `recordar_dato` existe y cubre la misma intención
     * (persistir un dato del usuario), aunque save_memory va directa a
     * MemoryRepository.saveMemory en runtime.
     */
    val wirePorNombre: Map<String, String> = mapOf(
        "open_alarms" to "abrir_alarmas",
        "set_alarm" to "poner_alarma",
        "search_google" to "buscar_google",
        "open_youtube" to "abrir_youtube",
        "open_whatsapp" to "abrir_whatsapp",
        "play_music" to "reproducir_musica",
        "save_memory" to "recordar_dato",
        "open_app" to "abrir_app"
    )
}
