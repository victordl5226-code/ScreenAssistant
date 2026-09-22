package com.screenassistant.feature.iot.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Módulo DI para el feature IoT.
 *
 * Proporciona bindings específicos del módulo de UI.
 * Los ViewModels se inyectan con @HiltViewModel directamente.
 * Las dependencias de core:iot:domain y core:iot:data ya están
 * provistas por IoTDomainModule e IotDataModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object IotModule {
    // No hay dependencias adicionales necesarias por ahora.
    // Los ViewModels usan @Inject constructor directamente.
}
