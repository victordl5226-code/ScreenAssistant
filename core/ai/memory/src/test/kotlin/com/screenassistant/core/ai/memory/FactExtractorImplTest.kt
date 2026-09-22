package com.screenassistant.core.ai.memory

import com.screenassistant.core.domain.repository.ai.FactCategory
import com.screenassistant.core.domain.repository.ai.MemoryEntry
import com.screenassistant.core.domain.repository.ai.MemoryRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FactExtractorImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var extractor: FactExtractorImpl

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        extractor = FactExtractorImpl()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── PREFERENCE ───────────────────────────────────────────────

    @Test
    fun `extrae preferencia me gusta`() = runTest {
        val facts = extractor.extractFromMessage("me gusta el café")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.PREFERENCE, facts[0].category)
        assertTrue(facts[0].`object`.contains("café"))
    }

    @Test
    fun `extrae preferencia favorito`() = runTest {
        val facts = extractor.extractFromMessage("mi color favorito es el azul")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.PREFERENCE, facts[0].category)
        assertTrue(facts[0].`object`.contains("azul"))
    }

    @Test
    fun `extrae preferencia no me gusta`() = runTest {
        val facts = extractor.extractFromMessage("no me gusta el vino")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.PREFERENCE, facts[0].category)
        assertEquals("no me gusta", facts[0].predicate)
        assertTrue(facts[0].`object`.contains("vino"))
    }

    @Test
    fun `extrae preferencia prefiero`() = runTest {
        val facts = extractor.extractFromMessage("prefiero el té sobre el café")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.PREFERENCE, facts[0].category)
    }

    // ── CONTACT ──────────────────────────────────────────────────

    @Test
    fun `extrae nombre`() = runTest {
        val facts = extractor.extractFromMessage("me llamo Carlos")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.CONTACT, facts[0].category)
        assertEquals("nombre", facts[0].predicate)
        assertTrue(facts[0].`object`.contains("Carlos"))
    }

    @Test
    fun `extrae nombre mi nombre es`() = runTest {
        val facts = extractor.extractFromMessage("mi nombre es María")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.CONTACT, facts[0].category)
        assertTrue(facts[0].`object`.contains("María"))
    }

    @Test
    fun `extrae edad`() = runTest {
        val facts = extractor.extractFromMessage("tengo 25 años")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.CONTACT, facts[0].category)
        assertEquals("edad", facts[0].predicate)
        assertTrue(facts[0].`object`.contains("25"))
    }

    @Test
    fun `extrae telefono`() = runTest {
        val facts = extractor.extractFromMessage("mi teléfono es 555-1234")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.CONTACT, facts[0].category)
        assertTrue(facts[0].`object`.contains("555-1234"))
    }

    // ── ROUTINE ──────────────────────────────────────────────────

    @Test
    fun `extrae rutina siempre`() = runTest {
        val facts = extractor.extractFromMessage("siempre tomo café por la mañana")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.ROUTINE, facts[0].category)
    }

    @Test
    fun `extrae rutina cada dia`() = runTest {
        val facts = extractor.extractFromMessage("cada día hago ejercicio")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.ROUTINE, facts[0].category)
    }

    @Test
    fun `extrae hora levantarse`() = runTest {
        val facts = extractor.extractFromMessage("me levanto a las siete")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.ROUTINE, facts[0].category)
        assertEquals("hora levantarse", facts[0].predicate)
    }

    @Test
    fun `extrae hora dormir`() = runTest {
        val facts = extractor.extractFromMessage("me acuesto a las 11")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.ROUTINE, facts[0].category)
        assertEquals("hora dormir", facts[0].predicate)
    }

    // ── WORK ─────────────────────────────────────────────────────

    @Test
    fun `extrae lugar trabajo`() = runTest {
        val facts = extractor.extractFromMessage("trabajo en Google")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.WORK, facts[0].category)
        assertEquals("lugar trabajo", facts[0].predicate)
        assertTrue(facts[0].`object`.contains("Google"))
    }

    @Test
    fun `extrae profesion`() = runTest {
        val facts = extractor.extractFromMessage("soy desarrollador")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.WORK, facts[0].category)
        assertEquals("profesión", facts[0].predicate)
    }

    @Test
    fun `extrae proyecto`() = runTest {
        val facts = extractor.extractFromMessage("mi proyecto es la app de nicho")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.WORK, facts[0].category)
    }

    // ── RELATIONSHIP ─────────────────────────────────────────────

    @Test
    fun `extrae contacto madre`() = runTest {
        val facts = extractor.extractFromMessage("mi madre se llama Ana")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.RELATIONSHIP, facts[0].category)
        assertTrue(facts[0].`object`.contains("Ana"))
    }

    @Test
    fun `extrae contacto amigo`() = runTest {
        val facts = extractor.extractFromMessage("mi amigo se llama Pedro")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.RELATIONSHIP, facts[0].category)
        assertTrue(facts[0].`object`.contains("Pedro"))
    }

    // ── LOCATION ─────────────────────────────────────────────────

    @Test
    fun `extrae ubicacion vivo en`() = runTest {
        val facts = extractor.extractFromMessage("vivo en Madrid")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.LOCATION, facts[0].category)
        assertEquals("ubicación", facts[0].predicate)
        assertTrue(facts[0].`object`.contains("Madrid"))
    }

    @Test
    fun `extrae casa`() = runTest {
        val facts = extractor.extractFromMessage("mi casa está en el centro")
        assertEquals(1, facts.size)
        assertEquals(FactCategory.LOCATION, facts[0].category)
        assertEquals("casa", facts[0].predicate)
    }

    // ── CASOS BORDE ──────────────────────────────────────────────

    @Test
    fun `mensaje sin patron no extrae nada`() = runTest {
        val facts = extractor.extractFromMessage("hola que tal")
        assertTrue(facts.isEmpty())
    }

    @Test
    fun `mensaje vacio no extrae nada`() = runTest {
        val facts = extractor.extractFromMessage("")
        assertTrue(facts.isEmpty())
    }

    @Test
    fun `ignora mensajes del asistente`() = runTest {
        val entries = listOf(
            MemoryEntry(role = MemoryRole.ASSISTANT, content = "me llamo JARVIS"),
        )
        val facts = extractor.extractFacts(entries)
        assertTrue(facts.isEmpty())
    }

    @Test
    fun `extrae multiples hechos de un mensaje`() = runTest {
        val facts = extractor.extractFromMessage("me llamo Carlos y vivo en Madrid")
        assertTrue(facts.size >= 2)
        val categories = facts.map { it.category }.toSet()
        assertTrue(categories.contains(FactCategory.CONTACT))
        assertTrue(categories.contains(FactCategory.LOCATION))
    }

    @Test
    fun `extractFacts deduplica hechos`() = runTest {
        val entries = listOf(
            MemoryEntry(role = MemoryRole.USER, content = "me gusta el café"),
            MemoryEntry(role = MemoryRole.USER, content = "me gusta el café"),
        )
        val facts = extractor.extractFacts(entries)
        // Deduplicados por category+subject+predicate
        assertEquals(1, facts.size)
    }

    @Test
    fun `case insensitive`() = runTest {
        val facts = extractor.extractFromMessage("ME GUSTA EL CAFÉ")
        assertEquals(1, facts.size)
    }
}
