package com.screenassistant.core.data.di

import com.screenassistant.core.data.remote.GeminiRepositoryImpl
import com.screenassistant.core.data.remote.openrouter.DefaultOpenRouterApiClient
import com.screenassistant.core.data.remote.openrouter.OpenRouterApiClient
import com.screenassistant.core.data.repository.AiOrchestratorImpl
import com.screenassistant.core.data.repository.BatteryMonitorImpl
import com.screenassistant.core.data.repository.ConnectivityMonitorImpl
import com.screenassistant.core.data.repository.MemoryRepositoryImpl
import com.screenassistant.core.data.repository.ScreenContextRepositoryImpl
import com.screenassistant.core.data.repository.TemporalRepositoryImpl
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.domain.repository.ScreenContextRepository
import com.screenassistant.core.domain.repository.TemporalRepository
import com.screenassistant.core.domain.repository.ai.AiOrchestrator
import com.screenassistant.core.domain.repository.ai.BatteryMonitor
import com.screenassistant.core.domain.repository.ai.ConnectivityMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindOpenRouterApiClient(impl: DefaultOpenRouterApiClient): OpenRouterApiClient

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindGeminiRepository(impl: GeminiRepositoryImpl): GeminiRepository

    @Binds
    @Singleton
    abstract fun bindScreenContextRepository(impl: ScreenContextRepositoryImpl): ScreenContextRepository

    @Binds
    @Singleton
    abstract fun bindTemporalRepository(impl: TemporalRepositoryImpl): TemporalRepository

    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(impl: ConnectivityMonitorImpl): ConnectivityMonitor

    @Binds
    @Singleton
    abstract fun bindAiOrchestrator(impl: AiOrchestratorImpl): AiOrchestrator

    @Binds
    @Singleton
    abstract fun bindBatteryMonitor(impl: BatteryMonitorImpl): BatteryMonitor
}
