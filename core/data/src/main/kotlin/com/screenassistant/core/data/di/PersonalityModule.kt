package com.screenassistant.core.data.di

import com.screenassistant.core.data.repository.ConversationMemoryRepositoryImpl
import com.screenassistant.core.data.repository.PersonalityRepositoryImpl
import com.screenassistant.core.domain.repository.ConversationMemoryRepository
import com.screenassistant.core.domain.repository.PersonalityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt que provee dependencias del subsistema de personalidad.
 *
 * Concentra los bindings de repositorios de personalidad y memoria
 * conversacional. Los repositorios se implementan en este módulo
 * (core:data) y se enlazan a sus interfaces de dominio.
 *
 * El [PersonalityEngine] no necesita `@Provides` explícito porque
 * su constructor lleva `@Inject` y no tiene dependencias externas
 * (es una clase pura de dominio).
 *
 * Nota: los `@Binds` de [ConversationMemoryRepository] y
 * [PersonalityRepository] se migraron aquí desde [DataModule] para
 * agrupar las dependencias de personalidad en un solo lugar.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PersonalityModule {

    // ── @Binds — Repositorios de personalidad ───────────────────────────

    @Binds
    @Singleton
    abstract fun bindConversationMemoryRepository(impl: ConversationMemoryRepositoryImpl): ConversationMemoryRepository

    @Binds
    @Singleton
    abstract fun bindPersonalityRepository(impl: PersonalityRepositoryImpl): PersonalityRepository
}
