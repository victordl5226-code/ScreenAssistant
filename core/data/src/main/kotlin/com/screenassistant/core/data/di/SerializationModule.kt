package com.screenassistant.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

/**
 * Módulo Hilt que provee instancias de serialización y configuración.
 *
 * Los módulos `@Module` de Hilt NO pueden mezclar `@Binds` (abstract)
 * y `@Provides` (concreto) en la misma clase. Este módulo concentra
 * los `@Provides` de serialización y configuración para que [DataModule] solo contenga
 * `@Binds` de repositorios.
 */
@Module
@InstallIn(SingletonComponent::class)
object SerializationModule {

    /**
     * Instancia singleton de [Json] configurada para el proyecto.
     *
     * Configuración:
     * - [ignoreUnknownKeys]: tolera JSON con campos extras
     * - [isLenient]: acepta JSON con comillas simples y comentarios
     * - [coerceInputValues]: usa valores por defecto en lugar de fallar
     * - [explicitNulls]: no incluye campos null en la serialización
     * - [encodeDefaults]: incluye campos con valores por defecto (CRÍTICO para OpenRouter/Nvidia)
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    // ── Providers de configuración OpenRouter ────────────────────────────────

    @Provides
    @Singleton
    @Named("openrouter_base_url")
    fun provideOpenRouterBaseUrl(): String = "https://openrouter.ai/api/v1/"

    @Provides
    @Singleton
    @Named("openrouter_model")
    fun provideOpenRouterModel(): String = "meta-llama/llama-3.1-8b-instruct"
}
