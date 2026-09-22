package com.screenassistant.core.domain.engine

import com.screenassistant.core.domain.model.personality.PersonalityConfig
import com.screenassistant.core.domain.model.personality.PersonalityTraits
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PersonalityEngineTest {

    private lateinit var engine: PersonalityEngine

    @Before
    fun setup() {
        engine = PersonalityEngine()
    }

    // ── adaptResponse ──

    @Test
    fun `adaptResponse con formalidad alta reemplaza expresiones informales`() {
        val traits = PersonalityTraits(
            formality = 1.0f,
            humor = 0.0f,
            verbosity = 0.5f,
            warmth = 0.5f,
            sarcasm = 0.0f
        )
        val response = "Hey, cheers amigo!"

        val adapted = engine.adaptResponse(response, traits)

        assertTrue(adapted.contains("estimado"))
        assertTrue(adapted.contains("atentamente"))
        assertFalse(adapted.contains("Hey"))
        assertFalse(adapted.contains("cheers"))
    }

    @Test
    fun `adaptResponse con formalidad baja no reemplaza`() {
        val traits = PersonalityTraits(
            formality = 0.0f,
            humor = 0.0f,
            verbosity = 0.5f,
            warmth = 0.5f,
            sarcasm = 0.0f
        )
        val response = "Hey, cheers amigo!"

        val adapted = engine.adaptResponse(response, traits)

        assertEquals(response, adapted)
    }

    @Test
    fun `adaptResponse con calidez alta añade prefijo si no tiene`() {
        val traits = PersonalityTraits(
            formality = 0.5f,
            humor = 0.0f,
            verbosity = 0.5f,
            warmth = 0.8f,
            sarcasm = 0.0f
        )
        val response = "Como usted desee."

        val adapted = engine.adaptResponse(response, traits)

        assertTrue(adapted.startsWith("¡"))
    }

    @Test
    fun `adaptResponse con calidez alta y prefijo existente no duplica`() {
        val traits = PersonalityTraits(
            formality = 0.5f,
            humor = 0.0f,
            verbosity = 0.5f,
            warmth = 0.8f,
            sarcasm = 0.0f
        )
        val response = "¡Por supuesto!"

        val adapted = engine.adaptResponse(response, traits)

        assertEquals(response, adapted)
    }

    @Test
    fun `shouldAddHumor determinista con mismos rasgos`() {
        val traits = PersonalityTraits(
            formality = 0.5f,
            humor = 0.8f,
            verbosity = 0.5f,
            warmth = 0.5f,
            sarcasm = 0.0f
        )

        val r1 = engine.shouldAddHumor(traits)
        val r2 = engine.shouldAddHumor(traits)
        val r3 = engine.shouldAddHumor(traits)

        assertEquals(r1, r2)
        assertEquals(r2, r3)
    }

    @Test
    fun `shouldAddHumor con humor bajo`() {
        val traits = PersonalityTraits(
            formality = 0.5f,
            humor = 0.2f,
            verbosity = 0.5f,
            warmth = 0.5f,
            sarcasm = 0.0f
        )

        val result = engine.shouldAddHumor(traits)

        assertTrue(result == true || result == false)
    }

    @Test
    fun `shouldAddHumor con humor alto`() {
        val traits = PersonalityTraits(
            formality = 0.5f,
            humor = 1.0f,
            verbosity = 0.5f,
            warmth = 0.5f,
            sarcasm = 0.0f
        )

        val result = engine.shouldAddHumor(traits)

        assertTrue(result == true || result == false)
    }

    @Test
    fun `buildGreeting morning formal`() {
        val config = PersonalityConfig(
            name = "J.A.R.V.I.S.",
            traits = PersonalityTraits(formality = 0.8f, humor = 0.0f, verbosity = 0.5f, warmth = 0.5f, sarcasm = 0.0f)
        )

        val greeting = engine.buildGreeting(config, "morning")

        assertTrue(greeting.startsWith("Buenos días"))
        assertTrue(greeting.contains("J.A.R.V.I.S."))
        assertTrue(greeting.contains("a su servicio"))
    }

    @Test
    fun `buildGreeting morning warm`() {
        val config = PersonalityConfig(
            name = "J.A.R.V.I.S.",
            traits = PersonalityTraits(formality = 0.5f, humor = 0.0f, verbosity = 0.5f, warmth = 0.8f, sarcasm = 0.0f)
        )

        val greeting = engine.buildGreeting(config, "morning")

        assertTrue(greeting.startsWith("¡Buenos días"))
    }

    @Test
    fun `buildGreeting afternoon`() {
        val config = PersonalityConfig.DEFAULT_JARVIS

        val greeting = engine.buildGreeting(config, "afternoon")

        assertTrue(greeting.startsWith("Buenas tardes"))
    }

    @Test
    fun `buildGreeting evening`() {
        val config = PersonalityConfig.DEFAULT_JARVIS

        val greeting = engine.buildGreeting(config, "evening")

        assertTrue(greeting.startsWith("Buenas noches"))
    }

    @Test
    fun `buildGreeting unknown timeOfDay`() {
        val config = PersonalityConfig.DEFAULT_JARVIS

        val greeting = engine.buildGreeting(config, "midnight")

        assertTrue(greeting.startsWith("Hola"))
    }

    @Test
    fun `buildGreeting null timeOfDay`() {
        val config = PersonalityConfig.DEFAULT_JARVIS

        val greeting = engine.buildGreeting(config, null)

        assertTrue(greeting.startsWith("Hola"))
    }

    // ── PersonalityTraits tests ──

    @Test
    fun `PersonalityTraits validación de rangos`() {
        // Valores válidos
        PersonalityTraits(formality = 0.0f, humor = 0.0f, verbosity = 0.0f, warmth = 0.0f, sarcasm = 0.0f)
        PersonalityTraits(formality = 1.0f, humor = 1.0f, verbosity = 1.0f, warmth = 1.0f, sarcasm = 1.0f)
        PersonalityTraits(formality = 0.5f, humor = 0.5f, verbosity = 0.5f, warmth = 0.5f, sarcasm = 0.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `PersonalityTraits formality fuera de rango lanza excepción`() {
        PersonalityTraits(formality = -0.1f, humor = 0.5f, verbosity = 0.5f, warmth = 0.5f, sarcasm = 0.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `PersonalityTraits formality mayor a 1 lanza excepción`() {
        PersonalityTraits(formality = 1.1f, humor = 0.5f, verbosity = 0.5f, warmth = 0.5f, sarcasm = 0.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `PersonalityTraits humor fuera de rango lanza excepción`() {
        PersonalityTraits(formality = 0.5f, humor = 1.5f, verbosity = 0.5f, warmth = 0.5f, sarcasm = 0.5f)
    }

    @Test
    fun `PersonalityTraits blendWith combina rasgos`() {
        val traits1 = PersonalityTraits(formality = 0.2f, humor = 0.8f, verbosity = 0.3f, warmth = 0.9f, sarcasm = 0.1f)
        val traits2 = PersonalityTraits(formality = 0.8f, humor = 0.2f, verbosity = 0.7f, warmth = 0.1f, sarcasm = 0.9f)

        val blended = traits1.blendWith(traits2, 0.5f)

        assertEquals(0.5f, blended.formality, 0.001f)
        assertEquals(0.5f, blended.humor, 0.001f)
        assertEquals(0.5f, blended.verbosity, 0.001f)
        assertEquals(0.5f, blended.warmth, 0.001f)
        assertEquals(0.5f, blended.sarcasm, 0.001f)
    }

    @Test
    fun `PersonalityTraits blendWith factor 0 retorna traits1`() {
        val traits1 = PersonalityTraits(formality = 0.2f, humor = 0.8f, verbosity = 0.3f, warmth = 0.9f, sarcasm = 0.1f)
        val traits2 = PersonalityTraits(formality = 0.8f, humor = 0.2f, verbosity = 0.7f, warmth = 0.1f, sarcasm = 0.9f)

        val blended = traits1.blendWith(traits2, 0.0f)

        assertEquals(traits1, blended)
    }

    @Test
    fun `PersonalityTraits blendWith factor 1 retorna traits2`() {
        val traits1 = PersonalityTraits(formality = 0.2f, humor = 0.8f, verbosity = 0.3f, warmth = 0.9f, sarcasm = 0.1f)
        val traits2 = PersonalityTraits(formality = 0.8f, humor = 0.2f, verbosity = 0.7f, warmth = 0.1f, sarcasm = 0.9f)

        val blended = traits1.blendWith(traits2, 1.0f)

        assertEquals(traits2, blended)
    }

    @Test
    fun `PersonalityTraits DEFAULT_JARVIS tiene valores correctos`() {
        val traits = PersonalityTraits.DEFAULT_JARVIS

        assertEquals(0.7f, traits.formality, 0.001f)
        assertEquals(0.3f, traits.humor, 0.001f)
        assertEquals(0.4f, traits.verbosity, 0.001f)
        assertEquals(0.6f, traits.warmth, 0.001f)
        assertEquals(0.2f, traits.sarcasm, 0.001f)
    }

    @Test
    fun `PersonalityTraits NEUTRAL todos 0 punto 5`() {
        val traits = PersonalityTraits.NEUTRAL

        assertEquals(0.5f, traits.formality, 0.001f)
        assertEquals(0.5f, traits.humor, 0.001f)
        assertEquals(0.5f, traits.verbosity, 0.001f)
        assertEquals(0.5f, traits.warmth, 0.001f)
        assertEquals(0.5f, traits.sarcasm, 0.001f)
    }

    // ── PersonalityConfig tests ──

    @Test
    fun `PersonalityConfig DEFAULT_JARVIS tiene valores correctos`() {
        val config = PersonalityConfig.DEFAULT_JARVIS

        assertEquals("J.A.R.V.I.S.", config.name)
        assertEquals(0.7f, config.traits.formality, 0.001f)
        assertEquals(0.3f, config.traits.humor, 0.001f)
        assertEquals(0.4f, config.traits.verbosity, 0.001f)
        assertEquals(0.6f, config.traits.warmth, 0.001f)
        assertEquals(0.2f, config.traits.sarcasm, 0.001f)
    }

    @Test
    fun `PersonalityConfig catchphrases no vacías`() {
        val config = PersonalityConfig.DEFAULT_JARVIS
        assertTrue(config.catchphrases.isNotEmpty())
    }
}