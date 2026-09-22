package com.screenassistant.core.iot.domain.di

/**
 * Módulo para el dominio IoT.
 *
 * Este módulo está vacío intencionalmente: los bindings de las interfaces
 * del dominio (repositories, transports, usecases) se realizan en el
 * módulo de datos (:core:iot:data) donde existen las implementaciones
 * concretas.
 *
 * Se mantiene este archivo por consistencia con la arquitectura del proyecto
 * y para permitir configuración futura de bindings de ámbito de dominio
 * (qualifiers, scopes, etc.) sin tocar el módulo de datos.
 */
interface IoTDomainModule {
    // Bindings de interfaces de dominio → implementaciones
    // Se realizan en :core:iot:data para mantener separación de capas.
    //
    // Ejemplo de lo que NO va aquí:
    // @Binds
    // abstract fun bindMatterDeviceRepository(impl: MatterDeviceRepositoryImpl): MatterDeviceRepository
    //
    // Ejemplo de lo que SÍ podría ir aquí en el futuro:
    // @Provides
    // @Singleton
    // fun provideSomeDomainScopedObject(): SomeDomainObject = ...
}