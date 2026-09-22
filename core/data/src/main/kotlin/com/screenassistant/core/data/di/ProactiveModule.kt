package com.screenassistant.core.data.di

import com.screenassistant.core.data.repository.ContextAggregatorRepositoryImpl
import com.screenassistant.core.data.repository.ProactiveRuleRepositoryImpl
import com.screenassistant.core.data.repository.UserPatternRepositoryImpl
import com.screenassistant.core.domain.repository.ContextAggregatorRepository
import com.screenassistant.core.domain.repository.ProactiveRuleRepository
import com.screenassistant.core.domain.repository.UserPatternRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Módulo Hilt que provee dependencias del subsistema proactivo.
 *
 * Concentra los bindings de repositorios proactivos y las dependencias
 * del motor de evaluación de reglas. Los repositorios se implementan
 * en este módulo (core:data) y se enlazan a sus interfaces de dominio.
 *
 * Nota: los `@Binds` de [ProactiveRuleRepository], [UserPatternRepository]
 * y [ContextAggregatorRepository] se migraron aquí desde [DataModule] para
 * agrupar las dependencias proactivas en un solo lugar.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProactiveModule {

    // ── @Binds — Repositorios proactivos ────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindProactiveRuleRepository(impl: ProactiveRuleRepositoryImpl): ProactiveRuleRepository

    @Binds
    @Singleton
    abstract fun bindUserPatternRepository(impl: UserPatternRepositoryImpl): UserPatternRepository

    @Binds
    @Singleton
    abstract fun bindContextAggregatorRepository(impl: ContextAggregatorRepositoryImpl): ContextAggregatorRepository

    companion object {

        /**
         * Provee un [Clock] del sistema para el motor proactivo.
         *
         * Se usa en [ProactiveEngine] para generar timestamps deterministas
         * y en tests se puede inyectar un Clock falso.
         *
         * @return [Clock] configurado con la zona horaria del sistema
         */
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}
