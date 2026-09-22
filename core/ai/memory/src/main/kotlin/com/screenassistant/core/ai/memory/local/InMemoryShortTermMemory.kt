package com.screenassistant.core.ai.memory.local

import com.screenassistant.core.domain.repository.ai.MemoryTurn
import com.screenassistant.core.domain.repository.ai.ShortTermMemory
import java.util.ArrayDeque

/**
 * Implementación in-memory de memoria a corto plazo.
 *
 * Usa ArrayDeque como buffer circular de alta frecuencia.
 * NO persiste en Room (datos efímeros por diseño).
 *
 * @param maxCapacity Máximo de turnos en el buffer (default: 50)
 */
class InMemoryShortTermMemory(
    private val maxCapacity: Int = 50,
) : ShortTermMemory {

    private val buffer = ArrayDeque<MemoryTurn>(maxCapacity)

    override fun addTurn(turn: MemoryTurn) {
        if (buffer.size >= maxCapacity) {
            buffer.removeFirst()
        }
        buffer.addLast(turn)
    }

    override fun getContext(maxTurns: Int): List<MemoryTurn> {
        val start = maxOf(0, buffer.size - maxTurns)
        return buffer.toList().subList(start, buffer.size)
    }

    override fun getContextAsText(maxTurns: Int): String {
        val turns = getContext(maxTurns)
        if (turns.isEmpty()) return ""

        return buildString {
            appendLine("=== Contexto de conversación ===")
            for (turn in turns) {
                appendLine("Usuario: ${turn.userMessage}")
                appendLine("Asistente: ${turn.assistantResponse}")
            }
        }
    }

    override fun size(): Int = buffer.size

    override fun clear() {
        buffer.clear()
    }
}
