package com.screenassistant.core.data.remote

import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.Schema

/**
 * Catálogo del canal LLM (D3, Lote 9): las 8 FunctionDeclaration que Gemini
 * expone como tools. El canal WIRE vive en `AccionRegistry` (core:domain, JVM
 * puro) — vocabularios INDEPENDIENTES por diseño: el LLM emite nombres en inglés
 * (vocabulario del modelo, probado en producción) y el wire es nuestro vocabulario
 * español para Tasker (ADR-013 H1). NO unificar (veto D3): generar los tools desde
 * el registro acoplaría domain al SDK de Gemini, y la cobertura es 8 vs 22.
 *
 * Guardián de paridad: `CorrespondenciaGeminiWireTest` (core:data) exige que TODA
 * función de este catálogo tenga su wire en [AccionRegistry] vía [wirePorNombre] —
 * añadir una 9ª función sin wire → rojo (decisión obligada: wire nuevo o exclusión
 * documentada). Un wire sin función Gemini → verde (el LLM no cubre los 22).
 */
object GeminiFunctionCatalog {

    // Declaración de Funciones (Uso de List<Schema<*>> según SDK 0.9.0).
    // Extraídas de GeminiRepository (41-118) sin cambios de runtime.
    val declaraciones: List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = "open_alarms",
            description = "Abre la aplicación de reloj en la sección de alarmas.",
            parameters = emptyList(),
            requiredParameters = emptyList()
        ),
        FunctionDeclaration(
            name = "set_alarm",
            description = "Configura una nueva alarma.",
            parameters = listOf(
                Schema.int("hour", "Hora (0-23)"),
                Schema.int("minute", "Minuto (0-59)"),
                Schema.str("label", "Nombre de la alarma")
            ),
            requiredParameters = listOf("hour", "minute")
        ),
        FunctionDeclaration(
            name = "search_google",
            description = "Busca algo en Google.",
            parameters = listOf(
                Schema.str("query", "Término de búsqueda")
            ),
            requiredParameters = listOf("query")
        ),
        FunctionDeclaration(
            name = "open_youtube",
            description = "Abre YouTube.",
            parameters = listOf(
                Schema.str("query", "Búsqueda en YouTube")
            ),
            requiredParameters = emptyList()
        ),
        FunctionDeclaration(
            name = "open_whatsapp",
            description = "Abre WhatsApp.",
            parameters = emptyList(),
            requiredParameters = emptyList()
        ),
        FunctionDeclaration(
            name = "play_music",
            description = "Reproduce música.",
            parameters = listOf(
                Schema.str("query", "Canción o artista")
            ),
            requiredParameters = emptyList()
        ),
        FunctionDeclaration(
            name = "save_memory",
            description = "Guarda un dato importante sobre el usuario.",
            parameters = listOf(
                Schema.str("fact", "El dato a recordar")
            ),
            requiredParameters = listOf("fact")
        ),
        FunctionDeclaration(
            name = "open_app",
            description = "Abre una aplicación instalada en el dispositivo. El valor puede ser el nombre visible de la app (p. ej. \"WhatsApp\") o su package name (p. ej. \"com.whatsapp\").",
            parameters = listOf(
                Schema.str("app_name", "Nombre visible o package name de la aplicación a abrir")
            ),
            requiredParameters = listOf("app_name")
        )
    )

    /** Los 8 nombres LLM (fuente única para el guardián de paridad). */
    val nombres: Set<String> get() = declaraciones.map { it.name }.toSet()

    /**
     * P1-3: mapeo nombre LLM → wire del puente Tasker (AccionRegistry). Exigencia
     * de QA: el test de paridad NO puede duplicar esta tabla a mano — la consulta
     * directamente.
     *
     * P1-4 (save_memory): decisión declarada — la ejecución runtime de save_memory
     * va DIRECTA a `MemoryRepository.saveMemory` (no genera SystemCommand); el
     * mapeo aquí es SEMÁNTICO: el wire `recordar_dato` de AccionRegistry existe y
     * cubre la misma intención (persistir un dato del usuario).
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
