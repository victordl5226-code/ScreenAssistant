package com.screenassistant.core.ai.memory

import com.screenassistant.core.ai.memory.local.InMemoryShortTermMemory
import com.screenassistant.core.domain.repository.ai.MemoryTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InMemoryShortTermMemoryTest {

    private lateinit var memory: InMemoryShortTermMemory

    @Before
    fun setup() {
        memory = InMemoryShortTermMemory(maxCapacity = 5)
    }

    @Test
    fun `initial size is zero`() {
        assertEquals(0, memory.size())
    }

    @Test
    fun `addTurn increases size`() {
        memory.addTurn(MemoryTurn("hola", "¡Hola!"))
        assertEquals(1, memory.size())
    }

    @Test
    fun `getContext returns empty when no turns`() {
        assertTrue(memory.getContext().isEmpty())
    }

    @Test
    fun `getContext returns recent turns`() {
        memory.addTurn(MemoryTurn("msg1", "resp1"))
        memory.addTurn(MemoryTurn("msg2", "resp2"))
        val context = memory.getContext(maxTurns = 10)
        assertEquals(2, context.size)
        assertEquals("msg1", context[0].userMessage)
        assertEquals("msg2", context[1].userMessage)
    }

    @Test
    fun `getContext respects maxTurns limit`() {
        memory.addTurn(MemoryTurn("msg1", "resp1"))
        memory.addTurn(MemoryTurn("msg2", "resp2"))
        memory.addTurn(MemoryTurn("msg3", "resp3"))
        val context = memory.getContext(maxTurns = 2)
        assertEquals(2, context.size)
        assertEquals("msg2", context[0].userMessage)
        assertEquals("msg3", context[1].userMessage)
    }

    @Test
    fun `addTurn evicts oldest when capacity reached`() {
        repeat(6) { i ->
            memory.addTurn(MemoryTurn("msg$i", "resp$i"))
        }
        assertEquals(5, memory.size())
        val context = memory.getContext()
        assertEquals("msg1", context[0].userMessage)
        assertEquals("msg5", context[4].userMessage)
    }

    @Test
    fun `getContextAsText returns empty string when no turns`() {
        assertEquals("", memory.getContextAsText())
    }

    @Test
    fun `getContextAsText formats turns correctly`() {
        memory.addTurn(MemoryTurn("¿Qué hora es?", "Son las 3pm"))
        val text = memory.getContextAsText()
        assertTrue(text.contains("¿Qué hora es?"))
        assertTrue(text.contains("Son las 3pm"))
        assertTrue(text.contains("=== Contexto de conversación ==="))
    }

    @Test
    fun `clear resets size to zero`() {
        memory.addTurn(MemoryTurn("msg", "resp"))
        memory.clear()
        assertEquals(0, memory.size())
    }

    @Test
    fun `clear makes getContext return empty`() {
        memory.addTurn(MemoryTurn("msg", "resp"))
        memory.clear()
        assertTrue(memory.getContext().isEmpty())
    }
}
