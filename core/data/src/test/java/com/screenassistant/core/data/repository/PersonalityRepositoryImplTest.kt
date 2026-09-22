package com.screenassistant.core.data.repository

import com.screenassistant.core.data.util.PersonalityPreferences
import com.screenassistant.core.domain.model.personality.CommunicationStyle
import com.screenassistant.core.domain.model.personality.ConversationTurn
import com.screenassistant.core.domain.model.personality.PersonalityConfig
import com.screenassistant.core.domain.model.personality.PersonalityTraits
import com.screenassistant.core.domain.model.personality.UserProfile
import com.screenassistant.core.domain.model.personality.UserRoutine
import com.screenassistant.core.domain.model.proactive.LocationType
import com.screenassistant.core.domain.model.proactive.PatternContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId

class PersonalityRepositoryImplTest {

    private lateinit var preferences: PersonalityPreferences
    private lateinit var repository: PersonalityRepositoryImpl

    @Before
    fun setup() {
        preferences = mockk(relaxed = true)
        every { preferences.assistantName } returns flowOf("J.A.R.V.I.S.")
        every { preferences.formality } returns flowOf(0.7f)
        every { preferences.humor } returns flowOf(0.4f)
        every { preferences.verbosity } returns flowOf(0.4f)
        every { preferences.warmth } returns flowOf(0.8f)
        every { preferences.sarcasm } returns flowOf(0.3f)
        every { preferences.language } returns flowOf("es")
        every { preferences.catchphrasesRaw } returns flowOf("A su servicio.|Como desee.")
        repository = PersonalityRepositoryImpl(preferences)
    }

    @Test
    fun `getPersonalityConfig retorna configuracion correcta`() = runTest {
        repository.getPersonalityConfig().collect { config ->
            assertEquals("J.A.R.V.I.S.", config.name)
            assertEquals(0.7f, config.traits.formality)
            assertEquals(0.4f, config.traits.humor)
            assertEquals(0.4f, config.traits.verbosity)
            assertEquals(0.8f, config.traits.warmth)
            assertEquals(0.3f, config.traits.sarcasm)
            assertEquals("es", config.language)
            assertEquals(2, config.catchphrases.size)
        }
    }

    @Test
    fun `getPersonalityConfig filtra catchphrases vacias`() = runTest {
        every { preferences.catchphrasesRaw } returns flowOf("Frase una||")

        repository.getPersonalityConfig().collect { config ->
            assertEquals(1, config.catchphrases.size)
        }
    }

    @Test
    fun `getPersonalityConfig catchphrases vacias`() = runTest {
        every { preferences.catchphrasesRaw } returns flowOf("")

        repository.getPersonalityConfig().collect { config ->
            assertTrue(config.catchphrases.isEmpty())
        }
    }

    @Test
    fun `updatePersonalityConfig guarda todos los campos`() = runTest {
        coEvery { preferences.setName(any()) } returns Unit
        coEvery { preferences.setTrait(any(), any()) } returns Unit
        coEvery { preferences.setLanguage(any()) } returns Unit
        coEvery { preferences.setCatchphrases(any()) } returns Unit

        val config = PersonalityConfig(
            name = "CustomBot",
            traits = PersonalityTraits(formality = 0.5f, humor = 0.6f, verbosity = 0.3f, warmth = 0.9f, sarcasm = 0.1f),
            language = "en",
            catchphrases = listOf("Hello!", "Welcome!")
        )

        repository.updatePersonalityConfig(config)

        coVerify { preferences.setName("CustomBot") }
        coVerify { preferences.setTrait("formality", 0.5f) }
        coVerify { preferences.setTrait("humor", 0.6f) }
        coVerify { preferences.setTrait("verbosity", 0.3f) }
        coVerify { preferences.setTrait("warmth", 0.9f) }
        coVerify { preferences.setTrait("sarcasm", 0.1f) }
        coVerify { preferences.setLanguage("en") }
        coVerify { preferences.setCatchphrases(listOf("Hello!", "Welcome!")) }
    }

    @Test
    fun `getUserProfile retorna null cuando no hay nombre`() = runTest {
        coEvery { preferences.getUserProfileName() } returns null

        val result = repository.getUserProfile()

        assertNull(result)
    }

    @Test
    fun `getUserProfile retorna perfil completo`() = runTest {
        coEvery { preferences.getUserProfileName() } returns "Carlos"
        coEvery { preferences.getUserProfileId() } returns "id-123"
        coEvery { preferences.getUserProfilePreferredName() } returns "Carlitos"
        coEvery { preferences.getUserProfileStyle() } returns "CASUAL"
        coEvery { preferences.getUserProfileInterests() } returns "programacion|musica"
        coEvery { preferences.getUserProfileTimezone() } returns "Europe/Madrid"
        coEvery { preferences.getUserProfileRoutines() } returns ""

        val result = repository.getUserProfile()

        assertNotNull(result)
        assertEquals("Carlos", result?.name)
        assertEquals("id-123", result?.id)
        assertEquals("Carlitos", result?.preferredName)
        assertEquals(CommunicationStyle.CASUAL, result?.communicationStyle)
        assertEquals(2, result?.interests?.size)
        assertEquals(ZoneId.of("Europe/Madrid"), result?.timezone)
    }

    @Test
    fun `getUserProfile usa MIXED cuando estilo no es valido`() = runTest {
        coEvery { preferences.getUserProfileName() } returns "Test"
        coEvery { preferences.getUserProfileId() } returns null
        coEvery { preferences.getUserProfilePreferredName() } returns null
        coEvery { preferences.getUserProfileStyle() } returns "INVALID_STYLE"
        coEvery { preferences.getUserProfileInterests() } returns ""
        coEvery { preferences.getUserProfileTimezone() } returns null
        coEvery { preferences.getUserProfileRoutines() } returns ""

        val result = repository.getUserProfile()

        assertEquals(CommunicationStyle.MIXED, result?.communicationStyle)
    }

    @Test
    fun `getUserProfile usa timezone default cuando invalida`() = runTest {
        coEvery { preferences.getUserProfileName() } returns "Test"
        coEvery { preferences.getUserProfileId() } returns null
        coEvery { preferences.getUserProfilePreferredName() } returns null
        coEvery { preferences.getUserProfileStyle() } returns null
        coEvery { preferences.getUserProfileInterests() } returns ""
        coEvery { preferences.getUserProfileTimezone() } returns "Invalid/Zone"
        coEvery { preferences.getUserProfileRoutines() } returns ""

        val result = repository.getUserProfile()

        assertEquals(ZoneId.systemDefault(), result?.timezone)
    }

    @Test
    fun `updateUserProfile guarda perfil correctamente`() = runTest {
        coEvery { preferences.saveUserProfile(any(), any(), any(), any(), any(), any(), any()) } returns Unit
        val profile = UserProfile(
            id = "u1", name = "Carlos", preferredName = "Carlitos",
            communicationStyle = CommunicationStyle.FORMAL,
            interests = listOf("tech"), timezone = ZoneId.of("UTC")
        )

        repository.updateUserProfile(profile)

        coVerify {
            preferences.saveUserProfile(
                id = "u1", name = "Carlos", preferredName = "Carlitos",
                style = "FORMAL", interests = listOf("tech"),
                timezone = "UTC", routines = ""
            )
        }
    }

    @Test
    fun `parseRoutines maneja formato corrupto`() = runTest {
        coEvery { preferences.getUserProfileName() } returns "Test"
        coEvery { preferences.getUserProfileId() } returns null
        coEvery { preferences.getUserProfilePreferredName() } returns null
        coEvery { preferences.getUserProfileStyle() } returns null
        coEvery { preferences.getUserProfileInterests() } returns ""
        coEvery { preferences.getUserProfileTimezone() } returns null
        coEvery { preferences.getUserProfileRoutines() } returns "bad|data|too|few|parts"

        val result = repository.getUserProfile()

        assertNotNull(result)
        assertTrue(result?.routines?.isEmpty() ?: false)
    }

    @Test
    fun `serializeRoutines y parseRoutines roundtrip`() = runTest {
        val routines = listOf(
            UserRoutine(
                name = "Morning Coffee",
                action = "make_coffee",
                pattern = PatternContext(DayOfWeek.MONDAY, 7, 9, LocationType.HOME),
                confidence = 0.8f
            )
        )
        coEvery { preferences.saveUserProfile(any(), any(), any(), any(), any(), any(), any()) } returns Unit
        val profile = UserProfile(id = "u1", name = "Test", routines = routines)

        repository.updateUserProfile(profile)

        coVerify {
            preferences.saveUserProfile(
                id = any(), name = any(), preferredName = any(),
                style = any(), interests = any(), timezone = any(),
                routines = withArg { raw ->
                    assertTrue(raw.contains("Morning Coffee"))
                    assertTrue(raw.contains("make_coffee"))
                    assertTrue(raw.contains("MONDAY"))
                }
            )
        }
    }

    // ── Onboarding tests ───────────────────────────────────────────────

    @Test
    fun `isOnboardingCompleted retorna true cuando DataStore indica completado`() = runTest {
        every { preferences.onboardingCompleted } returns flowOf(true)

        val result = repository.isOnboardingCompleted()

        assertTrue(result)
    }

    @Test
    fun `isOnboardingCompleted retorna false cuando DataStore indica no completado`() = runTest {
        every { preferences.onboardingCompleted } returns flowOf(false)

        val result = repository.isOnboardingCompleted()

        assertFalse(result)
    }

    @Test
    fun `completeOnboarding guarda nombre y marca onboarding completado`() = runTest {
        coEvery { preferences.setName(any()) } returns Unit
        coEvery { preferences.setOnboardingCompleted() } returns Unit

        val config = PersonalityConfig(
            name = "Friday",
            traits = PersonalityTraits.DEFAULT_JARVIS,
            language = "es"
        )

        repository.completeOnboarding(config)

        coVerify(exactly = 1) { preferences.setName("Friday") }
        coVerify(exactly = 1) { preferences.setOnboardingCompleted() }
    }

    @Test
    fun `completeOnboarding con nombre J A R V I S guarda correctamente`() = runTest {
        coEvery { preferences.setName(any()) } returns Unit
        coEvery { preferences.setOnboardingCompleted() } returns Unit

        repository.completeOnboarding(PersonalityConfig.DEFAULT_JARVIS)

        coVerify(exactly = 1) { preferences.setName("J.A.R.V.I.S.") }
        coVerify(exactly = 1) { preferences.setOnboardingCompleted() }
    }
}
