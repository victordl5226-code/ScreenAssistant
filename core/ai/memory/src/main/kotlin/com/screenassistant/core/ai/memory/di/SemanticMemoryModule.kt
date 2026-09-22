package com.screenassistant.core.ai.memory.di

import android.content.Context
import com.screenassistant.core.ai.memory.KnowledgeFactDao
import com.screenassistant.core.ai.memory.semantic.BruteForceVectorStore
import com.screenassistant.core.ai.memory.semantic.EmbeddingGenerator
import com.screenassistant.core.ai.memory.semantic.OnDeviceEmbeddingGenerator
import com.screenassistant.core.ai.memory.semantic.SemanticFactDao
import com.screenassistant.core.ai.memory.semantic.SemanticMemoryRepositoryImpl
import com.screenassistant.core.ai.memory.semantic.VectorStore
import com.screenassistant.core.domain.repository.ai.SemanticMemoryRepository
import com.screenassistant.core.model.domain.ModelAssetRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Módulo DI para Memoria Semántica (RAG).
 *
 * Proporciona:
 * - [EmbeddingGenerator] — generación de vectores con ONNX Runtime
 * - [VectorStore] — búsqueda brute-force por cosine similarity
 * - [SemanticMemoryRepository] — orquestación embedding + store + Room
 *
 * ## Descarga de modelos
 * [OnDeviceEmbeddingGenerator] descarga el modelo MiniLM bajo demanda
 * a través de [ModelAssetRepository]. La carga es lazy: se realiza
 * solo en la primera llamada a `embed()`.
 *
 * ## Manejo de errores
 * Si el `EmbeddingGenerator` no está listo, el `SemanticMemoryRepository`
 * retornará `isReady() = false` y `MemoryEnricher` usará fallback LIKE.
 */
@Module
@InstallIn(SingletonComponent::class)
object SemanticMemoryModule {

    /**
     * Generador de embeddings en-device.
     * Usa ONNX Runtime + all-MiniLM-L6-v2 (INT8 quantized).
     * Descarga el modelo bajo demanda a través de [ModelAssetRepository].
     */
    @Provides
    @Singleton
    fun provideEmbeddingGenerator(
        @ApplicationContext context: Context,
        modelRepository: ModelAssetRepository,
    ): EmbeddingGenerator {
        return OnDeviceEmbeddingGenerator(
            context = context,
            modelRepository = modelRepository,
            dispatcher = Dispatchers.IO,
        )
    }

    /**
     * Vector Store en memoria (brute-force).
     * Para <10K vectores, esto es ~15ms por búsqueda.
     */
    @Provides
    @Singleton
    fun provideVectorStore(): VectorStore {
        return BruteForceVectorStore()
    }

    /**
     * Repositorio de memoria semántica.
     *
     * Orquesta: EmbeddingGenerator → VectorStore → SemanticFactDao (Room)
     */
    @Provides
    @Singleton
    fun provideSemanticMemoryRepository(
        embeddingGenerator: EmbeddingGenerator,
        vectorStore: VectorStore,
        semanticFactDao: SemanticFactDao,
        knowledgeFactDao: KnowledgeFactDao,
    ): SemanticMemoryRepository {
        return SemanticMemoryRepositoryImpl(embeddingGenerator, vectorStore, semanticFactDao, knowledgeFactDao)
    }
}
