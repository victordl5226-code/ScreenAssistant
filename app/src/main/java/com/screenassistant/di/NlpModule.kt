package com.screenassistant.di

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.nlp.EntityExtractor
import com.screenassistant.core.domain.nlp.IntentClassifier
import com.screenassistant.core.domain.nlp.LocalNlpEngine
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.nlp.classifier.RuleBasedIntentClassifier
import com.screenassistant.core.nlp.engine.LocalNlpEngineImpl
import com.screenassistant.core.nlp.extractor.SpanishEntityExtractor
import com.screenassistant.core.nlp.parser.NlpCommandParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para inyección de dependencias del módulo NLP.
 *
 * Proporciona: IntentClassifier, EntityExtractor, LocalNlpEngine, NlpCommandParser.
 * Todos son Singletons (una sola instancia en toda la app).
 */
@Module
@InstallIn(SingletonComponent::class)
object NlpModule {

    @Provides
    @Singleton
    fun proveerClasificadorIntenciones(): IntentClassifier {
        return RuleBasedIntentClassifier()
    }

    @Provides
    @Singleton
    fun proveerExtractoEntidades(): EntityExtractor {
        return SpanishEntityExtractor()
    }

    @Provides
    @Singleton
    fun proveerMotorNlp(
        clasificador: IntentClassifier,
        extractor: EntityExtractor
    ): LocalNlpEngine {
        return LocalNlpEngineImpl(clasificador, extractor)
    }

    @Provides
    @Singleton
    fun proveerParserNlp(
        motor: LocalNlpEngine,
        systemAction: SystemAction,
        memoryRepository: MemoryRepository
    ): NlpCommandParser {
        return NlpCommandParser(motor, systemAction, memoryRepository)
    }
}
