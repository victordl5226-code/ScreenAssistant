package com.screenassistant.core.data.di

import com.screenassistant.core.data.remote.DefaultGenerativeModelFactory
import com.screenassistant.core.data.remote.GenerativeModelFactory
import com.screenassistant.core.data.remote.GeminiRepositoryImpl
import com.screenassistant.core.data.repository.MemoryRepositoryImpl
import com.screenassistant.core.data.repository.ScreenContextRepositoryImpl
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.domain.repository.ScreenContextRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// M24 (Lote 10): los 4 bindings de interfaz de core:data viven AQUÍ (el impl
// vive en este módulo → el binding le pertenece, no a :app). Los 3 @Provides
// FQCN de AppModule se eliminaron en el mismo lote.
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindGenerativeModelFactory(impl: DefaultGenerativeModelFactory): GenerativeModelFactory

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindGeminiRepository(impl: GeminiRepositoryImpl): GeminiRepository

    @Binds
    @Singleton
    abstract fun bindScreenContextRepository(impl: ScreenContextRepositoryImpl): ScreenContextRepository
}