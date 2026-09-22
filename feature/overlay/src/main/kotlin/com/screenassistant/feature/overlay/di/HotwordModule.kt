package com.screenassistant.feature.overlay.di

import com.screenassistant.core.domain.service.HotwordDetector
import com.screenassistant.feature.overlay.hotword.PorcupineHotwordDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HotwordModule {
    @Binds
    @Singleton
    abstract fun bindHotwordDetector(impl: PorcupineHotwordDetector): HotwordDetector
}
