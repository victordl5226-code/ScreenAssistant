package com.screenassistant.core.domain.repository.ai

/**
 * Memoria a corto plazo — buffer in-memory de la conversación actual.
 *
 * NO usa Room (datos efímeros, alta frecuencia de acceso).
 * Implementado con ArrayDeque en `core:ai:memory`.
 */
interface ShortTermMemory {

    /**
     * Agrega un turno a la memoria corto plazo.
     */
    fun addTurn(turn: MemoryTurn)

    /**
     * Obtiene el contexto de la conversación actual.
     *
     * @param maxTurns Máximo de turnos a incluir
     * @return Lista de turnos ordenados cronológicamente
     */
    fun getContext(maxTurns: Int = 10): List<MemoryTurn>

    /**
     * Obtiene el contexto formateado como texto para el LLM.
     *
     * @param maxTurns Máximo de turnos
     * @return Texto formateado para incluir en el prompt
     */
    fun getContextAsText(maxTurns: Int = 10): String

    /**
     * Número de turnos actuales.
     */
    fun size(): Int

    /**
     * Limpia toda la memoria de corto plazo.
     */
    fun clear()
}

/**
 * Turno de conversación para memoria (par mensaje-respuesta).
 *
 * NOTA: Esta clase es diferente a `ConversationTurn` de `model.personality`.
 * La de personality tiene `screenContext`, `topic`, `sentiment`.
 * Esta es más ligera para el buffer de corto plazo del LLM.
 */
data class MemoryTurn(
    val userMessage: String,
    val assistantResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<String> = emptyList(),
)
