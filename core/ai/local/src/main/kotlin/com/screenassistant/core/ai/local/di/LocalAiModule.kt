package com.screenassistant.core.ai.local.di

import android.content.Context
import com.screenassistant.core.ai.local.llama.LlamaCppEngine
import com.screenassistant.core.ai.local.llama.bridge.LlamaCppBridge
import com.screenassistant.core.ai.local.llama.bridge.LlamaCppBridgeInterface
import com.screenassistant.core.ai.local.llama.config.LlamaCppConfig
import com.screenassistant.core.ai.local.llama.model.LlamaModelManager
import com.screenassistant.core.ai.local.llama.model.ModelDownloader
import com.screenassistant.core.domain.repository.ai.LocalInferenceEngine
import com.screenassistant.core.domain.repository.ai.ModelManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo DI para Local AI (llama.cpp).
 *
 * Provee los componentes del motor de inferencia local:
 * - LlamaCppBridge: Bridge JNI (implementación concreta)
 * - LlamaCppConfig: Configuración
 * - LlamaCppEngine: Motor de inferencia
 * - LlamaModelManager: Gestión de modelos
 * - ModelDownloader: Descarga de modelos
 *
 * La implementación anterior (MlcLlmEngine) ha sido reemplazada
 * por LlamaCppEngine que usa llama.cpp via JNI.
 */
@Module
@InstallIn(SingletonComponent::class)
object LocalAiModule {

    @Provides
    @Singleton
    fun provideLlamaCppBridge(): LlamaCppBridgeInterface {
        return LlamaCppBridge()
    }

    @Provides
    @Singleton
    fun provideLlamaCppConfig(): LlamaCppConfig {
        return LlamaCppConfig()
    }

    @Provides
    @Singleton
    fun provideModelDownloader(): ModelDownloader {
        return ModelDownloader()
    }

    @Provides
    @Singleton
    fun provideLlamaCppEngine(
        bridge: LlamaCppBridgeInterface,
        config: LlamaCppConfig,
    ): LlamaCppEngine {
        return LlamaCppEngine(bridge, config)
    }

    @Provides
    @Singleton
    fun provideLocalInferenceEngine(
        engine: LlamaCppEngine,
    ): LocalInferenceEngine {
        return engine
    }

    @Provides
    @Singleton
    fun provideModelManager(
        @ApplicationContext context: Context,
        engine: LlamaCppEngine,
        downloader: ModelDownloader,
    ): ModelManager {
        return LlamaModelManager(context, engine, downloader)
    }
}
