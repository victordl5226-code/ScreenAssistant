package com.screenassistant.core.model.di

import com.screenassistant.core.model.data.ModelAssetRepositoryImpl
import com.screenassistant.core.model.domain.ModelAssetRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ModelModule {

    @Binds
    @Singleton
    abstract fun bindModelAssetRepository(
        impl: ModelAssetRepositoryImpl
    ): ModelAssetRepository
}
