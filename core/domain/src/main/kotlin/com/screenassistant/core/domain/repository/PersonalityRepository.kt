package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.personality.PersonalityConfig
import com.screenassistant.core.domain.model.personality.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de acceso a la configuración de personalidad del asistente
 * y al perfil del usuario.
 *
 * Gestiona la configuración que define el tono, estilo y comportamiento
 * del asistente ([PersonalityConfig]) así como la información del usuario
 * ([UserProfile]) que permite la personalización.
 */
interface PersonalityRepository {

    /**
     * Flujo reactivo de la configuración de personalidad actual.
     * Emite una nueva configuración cada vez que el usuario o el sistema
     * modifican los rasgos, idioma o frases del asistente.
     *
     * @return [Flow] que emite la [PersonalityConfig] vigente
     */
    fun getPersonalityConfig(): Flow<PersonalityConfig>

    /**
     * Actualiza la configuración de personalidad del asistente.
     *
     * @param config Nueva configuración a aplicar
     */
    suspend fun updatePersonalityConfig(config: PersonalityConfig)

    /**
     * Obtiene el perfil del usuario actualmente registrado.
     *
     * @return El [UserProfile] si existe un perfil registrado, o `null` si
     *         el usuario aún no ha configurado su perfil
     */
    suspend fun getUserProfile(): UserProfile?

    /**
     * Actualiza o crea el perfil del usuario.
     *
     * @param profile Perfil del usuario a persistir
     */
    suspend fun updateUserProfile(profile: UserProfile)

    /**
     * Indica si el usuario ya completó el onboarding (personalización del nombre).
     *
     * @return `true` si el onboarding fue completado, `false` en caso contrario
     */
    suspend fun isOnboardingCompleted(): Boolean

    /**
     * Completa el onboarding guardando la configuración de personalidad proporcionada.
     *
     * @param config Configuración de personalidad con el nombre elegido por el usuario
     */
    suspend fun completeOnboarding(config: PersonalityConfig)
}
