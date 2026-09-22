package com.screenassistant.core.data.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore Preferences para la configuración de personalidad del asistente.
 *
 * Almacena los rasgos de personalidad (formality, humor, verbosity, warmth, sarcasm),
 * el nombre del asistente y la configuración de idioma. Estos valores definen el
 * tono y estilo de las respuestas generadas por el asistente.
 *
 * El delegate [personalityDataStore] DEBE estar en el nivel superior del archivo
 * (recomendación oficial de DataStore). No puede estar dentro de un object/class.
 *
 * Patrón idéntico a [HolidayStore]: clase inyectable con `@Inject constructor`,
 * NO object singleton (requisito QA).
 *
 * @property context Contexto de aplicación inyectado por Hilt
 */
private val Context.personalityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "personality_prefs"
)

@Singleton
class PersonalityPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nameKey = stringPreferencesKey("assistant_name")
    private val formalityKey = floatPreferencesKey("trait_formality")
    private val humorKey = floatPreferencesKey("trait_humor")
    private val verbosityKey = floatPreferencesKey("trait_verbosity")
    private val warmthKey = floatPreferencesKey("trait_warmth")
    private val sarcasmKey = floatPreferencesKey("trait_sarcasm")
    private val languageKey = stringPreferencesKey("language")
    private val catchphrasesKey = stringPreferencesKey("catchphrases")

    // Onboarding key
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")

    // User profile keys
    private val userProfileIdKey = stringPreferencesKey("user_profile_id")
    private val userProfileNameKey = stringPreferencesKey("user_profile_name")
    private val userProfilePreferredNameKey = stringPreferencesKey("user_profile_preferred_name")
    private val userProfileStyleKey = stringPreferencesKey("user_profile_style")
    private val userProfileInterestsKey = stringPreferencesKey("user_profile_interests")
    private val userProfileTimezoneKey = stringPreferencesKey("user_profile_timezone")
    private val userProfileRoutinesKey = stringPreferencesKey("user_profile_routines")

    /** Flow reactivo del nombre del asistente. Default: "J.A.R.V.I.S." */
    val assistantName: Flow<String> = context.personalityDataStore.data.map { prefs ->
        prefs[nameKey] ?: "J.A.R.V.I.S."
    }

    /** Flow reactivo del rasgo de formalidad (0.0–1.0). Default: 0.7 */
    val formality: Flow<Float> = context.personalityDataStore.data.map { prefs ->
        prefs[formalityKey] ?: 0.7f
    }

    /** Flow reactivo del rasgo de humor (0.0–1.0). Default: 0.4 */
    val humor: Flow<Float> = context.personalityDataStore.data.map { prefs ->
        prefs[humorKey] ?: 0.4f
    }

    /** Flow reactivo del rasgo de verbosidad (0.0–1.0). Default: 0.4 */
    val verbosity: Flow<Float> = context.personalityDataStore.data.map { prefs ->
        prefs[verbosityKey] ?: 0.4f
    }

    /** Flow reactivo del rasgo de calidez (0.0–1.0). Default: 0.8 */
    val warmth: Flow<Float> = context.personalityDataStore.data.map { prefs ->
        prefs[warmthKey] ?: 0.8f
    }

    /** Flow reactivo del rasgo de sarcasmo (0.0–1.0). Default: 0.3 */
    val sarcasm: Flow<Float> = context.personalityDataStore.data.map { prefs ->
        prefs[sarcasmKey] ?: 0.3f
    }

    /** Flow reactivo del código de idioma. Default: "es" */
    val language: Flow<String> = context.personalityDataStore.data.map { prefs ->
        prefs[languageKey] ?: "es"
    }

    /** Flow reactivo de las frases características (separadas por "|"). Default: "" */
    val catchphrasesRaw: Flow<String> = context.personalityDataStore.data.map { prefs ->
        prefs[catchphrasesKey] ?: ""
    }

    /** Flow reactivo que indica si el onboarding ha sido completado. Default: false */
    val onboardingCompleted: Flow<Boolean> = context.personalityDataStore.data.map { prefs ->
        prefs[onboardingCompletedKey] ?: false
    }

    /**
     * Establece el nombre del asistente.
     *
     * @param name Nuevo nombre (no puede estar vacío)
     */
    suspend fun setName(name: String) {
        require(name.isNotBlank()) { "El nombre del asistente no puede estar vacío" }
        context.personalityDataStore.edit { prefs ->
            prefs[nameKey] = name
        }
    }

    /**
     * Establece el valor de un rasgo de personalidad.
     *
     * @param key Nombre del rasgo: "formality", "humor", "verbosity", "warmth", "sarcasm"
     * @param value Nuevo valor (0.0–1.0)
     */
    suspend fun setTrait(key: String, value: Float) {
        require(value in 0f..1f) { "El valor del trait debe estar entre 0.0 y 1.0, recibido: $value" }
        context.personalityDataStore.edit { prefs ->
            when (key) {
                "formality" -> prefs[formalityKey] = value
                "humor" -> prefs[humorKey] = value
                "verbosity" -> prefs[verbosityKey] = value
                "warmth" -> prefs[warmthKey] = value
                "sarcasm" -> prefs[sarcasmKey] = value
                else -> throw IllegalArgumentException("Trait desconocido: $key")
            }
        }
    }

    /**
     * Establece el código de idioma.
     *
     * @param language Código ISO 639-1 (e.g., "es", "en")
     */
    suspend fun setLanguage(language: String) {
        require(language.isNotBlank()) { "El código de idioma no puede estar vacío" }
        context.personalityDataStore.edit { prefs ->
            prefs[languageKey] = language
        }
    }

    /**
     * Establece las frases características del asistente.
     *
     * @param catchphrases Lista de frases
     */
    suspend fun setCatchphrases(catchphrases: List<String>) {
        context.personalityDataStore.edit { prefs ->
            prefs[catchphrasesKey] = catchphrases.joinToString("|")
        }
    }

    /**
     * Marca el onboarding como completado.
     */
    suspend fun setOnboardingCompleted() {
        context.personalityDataStore.edit { prefs ->
            prefs[onboardingCompletedKey] = true
        }
    }

    /**
     * Lee todos los rasgos de personalidad de forma síncrona (suspend).
     * Retorna un par de nombre a mapa de rasgos.
     *
     * @return Pair<nombre, Map<rasgo, valor>>
     */
    suspend fun getAllTraits(): Pair<String, Map<String, Float>> {
        val prefs = context.personalityDataStore.data.first()
        val name = prefs[nameKey] ?: "J.A.R.V.I.S."
        val traits = mapOf(
            "formality" to (prefs[formalityKey] ?: 0.7f),
            "humor" to (prefs[humorKey] ?: 0.4f),
            "verbosity" to (prefs[verbosityKey] ?: 0.4f),
            "warmth" to (prefs[warmthKey] ?: 0.8f),
            "sarcasm" to (prefs[sarcasmKey] ?: 0.3f)
        )
        return Pair(name, traits)
    }

    // ── User Profile persistence ──────────────────────────────────────────

    /**
     * Lee el ID del perfil de usuario almacenado, o null si no existe.
     */
    suspend fun getUserProfileId(): String? {
        val prefs = context.personalityDataStore.data.first()
        return prefs[userProfileIdKey]
    }

    /**
     * Lee el nombre del perfil de usuario almacenado, o null si no existe.
     */
    suspend fun getUserProfileName(): String? {
        val prefs = context.personalityDataStore.data.first()
        return prefs[userProfileNameKey]
    }

    /**
     * Lee el nombre preferido del usuario, o null si no está configurado.
     */
    suspend fun getUserProfilePreferredName(): String? {
        val prefs = context.personalityDataStore.data.first()
        return prefs[userProfilePreferredNameKey]
    }

    /**
     * Lee el estilo de comunicación del usuario como String, o null si no existe.
     */
    suspend fun getUserProfileStyle(): String? {
        val prefs = context.personalityDataStore.data.first()
        return prefs[userProfileStyleKey]
    }

    /**
     * Lee los intereses del usuario como String separado por "|", o "".
     */
    suspend fun getUserProfileInterests(): String {
        val prefs = context.personalityDataStore.data.first()
        return prefs[userProfileInterestsKey] ?: ""
    }

    /**
     * Lee la zona horaria del usuario, o null si no está configurada.
     */
    suspend fun getUserProfileTimezone(): String? {
        val prefs = context.personalityDataStore.data.first()
        return prefs[userProfileTimezoneKey]
    }

    /**
     * Lee las rutinas del usuario serializadas como String, o "".
     */
    suspend fun getUserProfileRoutines(): String {
        val prefs = context.personalityDataStore.data.first()
        return prefs[userProfileRoutinesKey] ?: ""
    }

    /**
     * Guarda el perfil de usuario completo en DataStore.
     *
     * @param id ID del perfil
     * @param name Nombre del usuario
     * @param preferredName Nombre preferido (nullable)
     * @param style Estilo de comunicación (nombre del enum)
     * @param interests Lista de intereses separados por "|"
     * @param timezone Zona horaria ID
     * @param routines Rutinas serializadas (placeholder, serialización simple)
     */
    suspend fun saveUserProfile(
        id: String,
        name: String,
        preferredName: String?,
        style: String,
        interests: List<String>,
        timezone: String,
        routines: String
    ) {
        require(name.isNotBlank()) { "El nombre del usuario no puede estar vacío" }
        context.personalityDataStore.edit { prefs ->
            prefs[userProfileIdKey] = id
            prefs[userProfileNameKey] = name
            if (preferredName != null) {
                prefs[userProfilePreferredNameKey] = preferredName
            } else {
                prefs.remove(userProfilePreferredNameKey)
            }
            prefs[userProfileStyleKey] = style
            prefs[userProfileInterestsKey] = interests.joinToString("|")
            prefs[userProfileTimezoneKey] = timezone
            prefs[userProfileRoutinesKey] = routines
        }
    }

    /**
     * Elimina el perfil de usuario de DataStore.
     */
    suspend fun clearUserProfile() {
        context.personalityDataStore.edit { prefs ->
            prefs.remove(userProfileIdKey)
            prefs.remove(userProfileNameKey)
            prefs.remove(userProfilePreferredNameKey)
            prefs.remove(userProfileStyleKey)
            prefs.remove(userProfileInterestsKey)
            prefs.remove(userProfileTimezoneKey)
            prefs.remove(userProfileRoutinesKey)
        }
    }
}
