package com.screenassistant.core.ai.memory

import com.screenassistant.core.domain.repository.ai.FactCategory
import com.screenassistant.core.domain.repository.ai.FactExtractor
import com.screenassistant.core.domain.repository.ai.KnowledgeFact
import com.screenassistant.core.domain.repository.ai.MemoryEntry
import com.screenassistant.core.domain.repository.ai.MemoryRole
import com.screenassistant.core.domain.repository.ai.MemoryStore
import com.screenassistant.core.domain.repository.ai.MemoryTurn
import com.screenassistant.core.domain.repository.ai.MemoryType
import com.screenassistant.core.domain.repository.ai.SemanticMemoryRepository
import com.screenassistant.core.domain.repository.ai.SemanticFactResult
import com.screenassistant.core.domain.repository.ai.ShortTermMemory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para [MemoryEnricher].
 *
 * Verifica:
 * - Fail-soft: cada operación retorna valor por defecto si falla
 * - Composición correcta de resultados
 * - Persistencia correcta de turnos (shortTerm + longTerm + facts)
 * - Formato del contexto retornado
 * - Búsqueda semántica con fallback a LIKE
 */
class MemoryEnricherTest {

    private lateinit var shortTermMemory: ShortTermMemory
    private lateinit var memoryStore: MemoryStore
    private lateinit var factExtractor: FactExtractor
    private lateinit var semanticMemoryRepository: SemanticMemoryRepository
    private lateinit var logger: MemoryLogger
    private lateinit var memoryEnricher: MemoryEnricher

    @Before
    fun setUp() {
        shortTermMemory = mockk(relaxed = true)
        memoryStore = mockk(relaxed = true)
        factExtractor = mockk(relaxed = true)
        semanticMemoryRepository = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        memoryEnricher = MemoryEnricher(shortTermMemory, memoryStore, factExtractor, semanticMemoryRepository, logger)
    }

    // ─── getRelevantContext ──────────────────────────────────────────────

    @Test
    fun `getRelevantContext usa busqueda semantica cuando esta listo`() = runTest {
        coEvery { semanticMemoryRepository.isReady() } returns true
        coEvery { semanticMemoryRepository.searchSimilar("me gusta el café", 3) } returns listOf(
            SemanticFactResult(
                factId = "f1",
                text = "café: prefiere = sin azúcar",
                category = "PREFERENCE",
                confidence = 0.9f,
                similarity = 0.85f,
                timestamp = System.currentTimeMillis(),
            ),
        )

        val result = memoryEnricher.getRelevantContext("me gusta el café")

        assertEquals("\n[CONOCIMIENTO DEL USUARIO]: café: prefiere = sin azúcar", result)
        coVerify(exactly = 1) { semanticMemoryRepository.searchSimilar(any(), any()) }
        coVerify(exactly = 0) { memoryStore.search(any(), any()) }
    }

    @Test
    fun `getRelevantContext usa fallback LIKE cuando semantico no esta listo`() = runTest {
        coEvery { semanticMemoryRepository.isReady() } returns false
        val entries = listOf(
            MemoryEntry(id = "1", role = MemoryRole.USER, content = "café: prefiere = sin azúcar", timestamp = System.currentTimeMillis(), type = MemoryType.FACT),
        )
        coEvery { memoryStore.search("me gusta el café", 3) } returns entries

        val result = memoryEnricher.getRelevantContext("me gusta el café")

        assertEquals("\n[CONOCIMIENTO DEL USUARIO]: café: prefiere = sin azúcar", result)
        coVerify(exactly = 0) { semanticMemoryRepository.searchSimilar(any(), any()) }
        coVerify(exactly = 1) { memoryStore.search(any(), any()) }
    }

    @Test
    fun `getRelevantContext fallback a LIKE cuando busqueda semantica falla`() = runTest {
        coEvery { semanticMemoryRepository.isReady() } returns true
        coEvery { semanticMemoryRepository.searchSimilar(any(), any()) } throws RuntimeException("Embedding error")
        val entries = listOf(
            MemoryEntry(id = "1", role = MemoryRole.USER, content = "café: prefiere = sin azúcar", timestamp = System.currentTimeMillis(), type = MemoryType.FACT),
        )
        coEvery { memoryStore.search(any(), any()) } returns entries

        val result = memoryEnricher.getRelevantContext("me gusta el café")

        assertEquals("\n[CONOCIMIENTO DEL USUARIO]: café: prefiere = sin azúcar", result)
    }

    @Test
    fun `getRelevantContext retorna vacío cuando no hay hechos`() = runTest {
        coEvery { semanticMemoryRepository.isReady() } returns true
        coEvery { semanticMemoryRepository.searchSimilar(any(), any()) } returns emptyList()

        val result = memoryEnricher.getRelevantContext("algo random")

        assertEquals("", result)
    }

    @Test
    fun `getRelevantContext retorna vacío cuando memoryStore falla`() = runTest {
        coEvery { semanticMemoryRepository.isReady() } returns false
        coEvery { memoryStore.search(any(), any()) } throws RuntimeException("DB error")

        val result = memoryEnricher.getRelevantContext("test query")

        assertEquals("", result)
    }

    @Test
    fun `getRelevantContext multiplos hechos separados por punto y coma`() = runTest {
        coEvery { semanticMemoryRepository.isReady() } returns true
        coEvery { semanticMemoryRepository.searchSimilar(any(), 3) } returns listOf(
            SemanticFactResult("f1", "café: prefiere = con leche", "PREFERENCE", 0.8f, 0.9f, 100L),
            SemanticFactResult("f2", "mamá: se llama = Laura", "RELATIONSHIP", 0.9f, 0.85f, 200L),
        )

        val result = memoryEnricher.getRelevantContext("habla de café y mamá")

        assertEquals("\n[CONOCIMIENTO DEL USUARIO]: café: prefiere = con leche; mamá: se llama = Laura", result)
    }

    // ─── persistTurn ─────────────────────────────────────────────────────

    @Test
    fun `persistTurn guarda en shortTerm, longTerm y extrae hechos`() = runTest {
        coEvery { factExtractor.extractFromMessage("me gusta el café") } returns listOf(
            KnowledgeFact("f1", FactCategory.PREFERENCE, "café", "prefiere", "café", 0.8f, 100L, "test"),
        )

        memoryEnricher.persistTurn("me gusta el café", "¡Qué rico!")

        coVerify(exactly = 1) {
            shortTermMemory.addTurn(match {
                it.userMessage == "me gusta el café" && it.assistantResponse == "¡Qué rico!"
            })
        }
        coVerify(exactly = 1) { memoryStore.save(match {
            it.role == MemoryRole.USER && it.content == "me gusta el café"
        }) }
        coVerify(exactly = 1) { memoryStore.saveFact(match {
            it.id == "f1" && it.subject == "café"
        }) }
    }

    @Test
    fun `persistTurn genera embedding cuando semantico esta listo`() = runTest {
        coEvery { factExtractor.extractFromMessage("me gusta el café") } returns listOf(
            KnowledgeFact("f1", FactCategory.PREFERENCE, "café", "prefiere", "sin azúcar", 0.8f, 100L, "test"),
        )
        coEvery { semanticMemoryRepository.isReady() } returns true

        memoryEnricher.persistTurn("me gusta el café", "¡Qué rico!")

        coVerify(exactly = 1) {
            semanticMemoryRepository.storeFactEmbedding(
                factId = "f1",
                text = "café: prefiere = sin azúcar",
                category = "PREFERENCE",
            )
        }
    }

    @Test
    fun `persistTurn no genera embedding cuando semantico no esta listo`() = runTest {
        coEvery { factExtractor.extractFromMessage("me gusta el café") } returns listOf(
            KnowledgeFact("f1", FactCategory.PREFERENCE, "café", "prefiere", "sin azúcar", 0.8f, 100L, "test"),
        )
        coEvery { semanticMemoryRepository.isReady() } returns false

        memoryEnricher.persistTurn("me gusta el café", "¡Qué rico!")

        coVerify(exactly = 0) { semanticMemoryRepository.storeFactEmbedding(any(), any(), any()) }
    }

    @Test
    fun `persistTurn no falla si shortTermMemory falla`() = runTest {
        coEvery { shortTermMemory.addTurn(any()) } throws RuntimeException("STM error")
        coEvery { factExtractor.extractFromMessage(any()) } returns emptyList()

        // No lanza excepción
        memoryEnricher.persistTurn("test", "response")

        // longTerm sigue funcionando (USER + ASSISTANT)
        coVerify(exactly = 2) { memoryStore.save(any()) }
    }

    @Test
    fun `persistTurn no falla si memoryStore falla`() = runTest {
        coEvery { memoryStore.save(any()) } throws RuntimeException("Room error")
        coEvery { factExtractor.extractFromMessage(any()) } returns emptyList()

        // No lanza excepción
        memoryEnricher.persistTurn("test", "response")

        // shortTerm sigue funcionando
        coVerify(exactly = 1) { shortTermMemory.addTurn(any()) }
    }

    @Test
    fun `persistTurn no falla si factExtractor falla`() = runTest {
        coEvery { factExtractor.extractFromMessage(any()) } throws RuntimeException("Regex error")

        // No lanza excepción
        memoryEnricher.persistTurn("test", "response")

        // shortTerm y longTerm siguen funcionando
        coVerify(exactly = 1) { shortTermMemory.addTurn(any()) }
        coVerify(exactly = 2) { memoryStore.save(any()) }
        // No se guardan hechos porque el extractor falló
        coVerify(exactly = 0) { memoryStore.saveFact(any()) }
    }

    @Test
    fun `persistTurn no falla si embedding falla`() = runTest {
        coEvery { factExtractor.extractFromMessage("test") } returns listOf(
            KnowledgeFact("f1", FactCategory.PREFERENCE, "test", "es", "test", 0.8f, 100L, "test"),
        )
        coEvery { semanticMemoryRepository.isReady() } returns true
        coEvery { semanticMemoryRepository.storeFactEmbedding(any(), any(), any()) } throws RuntimeException("ONNX error")

        // No lanza excepción
        memoryEnricher.persistTurn("test", "response")

        // El hecho se guarda normalmente
        coVerify(exactly = 1) { memoryStore.saveFact(any()) }
    }

    // ─── getRecentContext ────────────────────────────────────────────────

    @Test
    fun `getRecentContext retorna historial formateado`() {
        val turns = listOf(
            MemoryTurn("hola", "¡Hola! ¿Cómo estás?"),
            MemoryTurn("qué tiempo hace", "Hace soleado"),
        )
        every { shortTermMemory.getContext(5) } returns turns

        val result = memoryEnricher.getRecentContext(5)

        assertEquals(
            "\nHistorial reciente:\nUsuario: hola -> Asistente: ¡Hola! ¿Cómo estás?\nUsuario: qué tiempo hace -> Asistente: Hace soleado",
            result,
        )
    }

    @Test
    fun `getRecentContext retorna vacío cuando no hay turnos`() {
        every { shortTermMemory.getContext(5) } returns emptyList()

        val result = memoryEnricher.getRecentContext(5)

        assertEquals("", result)
    }

    @Test
    fun `getRecentContext retorna vacío cuando shortTermMemory falla`() {
        every { shortTermMemory.getContext(any()) } throws RuntimeException("STM error")

        val result = memoryEnricher.getRecentContext(5)

        assertEquals("", result)
    }
}
