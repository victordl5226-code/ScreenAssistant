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
import com.screenassistant.core.domain.repository.PersonalityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [PersonalityRepository] que persiste la configuración
 * de personalidad del asistente y el perfil del usuario en DataStore
 * vía [PersonalityPreferences].
 *
 * La conversión entre los tipos de dominio ([PersonalityConfig], [UserProfile])
 * y las preferencias DataStore se realiza en esta clase. Cada campo se almacena
 * como una preferencia individual para permitir lecturas parciales reactivas.
 *
 * @property preferences Almacén de preferencias de personalidad
 */
@Singleton
class PersonalityRepositoryImpl @Inject constructor(
    private val preferences: PersonalityPreferences
) : PersonalityRepository {

    override fun getPersonalityConfig(): Flow<PersonalityConfig> {
        return combine(
            preferences.assistantName,
            preferences.formality,
            preferences.humor,
            preferences.verbosity,
            preferences.warmth,
            preferences.sarcasm,
            preferences.language,
            preferences.catchphrasesRaw
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val name = values[0] as String
            val formality = values[1] as Float
            val humor = values[2] as Float
            val verbosity = values[3] as Float
            val warmth = values[4] as Float
            val sarcasm = values[5] as Float
            val language = values[6] as String
            val catchphrasesRaw = values[7] as String

            PersonalityConfig(
                name = name,
                traits = PersonalityTraits(
                    formality = formality,
                    humor = humor,
                    verbosity = verbosity,
                    warmth = warmth,
                    sarcasm = sarcasm
                ),
                language = language,
                catchphrases = catchphrasesRaw.split("|").filter { it.isNotBlank() }
            )
        }
    }

    override suspend fun updatePersonalityConfig(config: PersonalityConfig) {
        preferences.setName(config.name)
        preferences.setTrait("formality", config.traits.formality)
        preferences.setTrait("humor", config.traits.humor)
        preferences.setTrait("verbosity", config.traits.verbosity)
        preferences.setTrait("warmth", config.traits.warmth)
        preferences.setTrait("sarcasm", config.traits.sarcasm)
        preferences.setLanguage(config.language)
        preferences.setCatchphrases(config.catchphrases)
    }

    override suspend fun getUserProfile(): UserProfile? {
        val name = preferences.getUserProfileName() ?: return null
        val id = preferences.getUserProfileId() ?: UUID.randomUUID().toString()
        val preferredName = preferences.getUserProfilePreferredName()
        val styleStr = preferences.getUserProfileStyle() ?: CommunicationStyle.MIXED.name
        val interestsRaw = preferences.getUserProfileInterests()
        val timezoneStr = preferences.getUserProfileTimezone()
        val routinesRaw = preferences.getUserProfileRoutines()

        return UserProfile(
            id = id,
            name = name,
            preferredName = preferredName,
            communicationStyle = runCatching {
                CommunicationStyle.valueOf(styleStr)
            }.getOrDefault(CommunicationStyle.MIXED),
            interests = interestsRaw.split("|").filter { it.isNotBlank() },
            timezone = runCatching {
                ZoneId.of(timezoneStr)
            }.getOrDefault(ZoneId.systemDefault()),
            routines = parseRoutines(routinesRaw)
        )
    }

    override suspend fun updateUserProfile(profile: UserProfile) {
        preferences.saveUserProfile(
            id = profile.id,
            name = profile.name,
            preferredName = profile.preferredName,
            style = profile.communicationStyle.name,
            interests = profile.interests,
            timezone = profile.timezone.id,
            routines = serializeRoutines(profile.routines)
        )
    }

    override suspend fun isOnboardingCompleted(): Boolean {
        return preferences.onboardingCompleted.first()
    }

    override suspend fun completeOnboarding(config: PersonalityConfig) {
        preferences.setName(config.name)
        preferences.setOnboardingCompleted()
    }

    /**
     * Serializa una lista de [UserRoutine] a formato simple de texto.
     * Formato: "name|action|dayOfWeek|hourStart|hourEnd|locationType|confidence#..."
     */
    private fun serializeRoutines(routines: List<UserRoutine>): String {
        if (routines.isEmpty()) return ""
        return routines.joinToString("#") { routine ->
            listOf(
                routine.name,
                routine.action,
                routine.pattern.dayOfWeek.name,
                routine.pattern.hourStart.toString(),
                routine.pattern.hourEnd.toString(),
                routine.pattern.location.name,
                routine.confidence.toString()
            ).joinToString("|")
        }
    }

    /**
     * Deserializa una cadena de texto a lista de [UserRoutine].
     * Maneja formato corrupto de forma segura retornando lista vacía.
     */
    private fun parseRoutines(raw: String): List<UserRoutine> {
        if (raw.isBlank()) return emptyList()
        return raw.split("#").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size < 7) return@mapNotNull null
            runCatching {
                UserRoutine(
                    name = parts[0],
                    action = parts[1],
                    pattern = PatternContext(
                        dayOfWeek = DayOfWeek.valueOf(parts[2]),
                        hourStart = parts[3].toInt(),
                        hourEnd = parts[4].toInt(),
                        location = LocationType.valueOf(parts[5])
                    ),
                    confidence = parts[6].toFloat()
                )
            }.getOrNull()
        }
    }
}
