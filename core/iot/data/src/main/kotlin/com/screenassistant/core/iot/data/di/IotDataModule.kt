package com.screenassistant.core.iot.data.di

import android.content.Context
import com.screenassistant.core.data.local.AppDatabase
import com.screenassistant.core.data.local.iot.IotDao
import com.screenassistant.core.iot.data.repository.CarAppRepositoryImpl
import com.screenassistant.core.iot.data.repository.HealthConnectRepositoryImpl
import com.screenassistant.core.iot.data.repository.HomeAssistantRepositoryImpl
import com.screenassistant.core.iot.data.repository.IotRepositoryImpl
import com.screenassistant.core.iot.data.repository.MatterDeviceRepositoryImpl
import com.screenassistant.core.iot.data.util.IotClock
import com.screenassistant.core.iot.data.util.IotEncryptedPrefs
import com.screenassistant.core.iot.data.util.RealIotClock
import com.screenassistant.core.iot.domain.repository.CarAppRepository
import com.screenassistant.core.iot.domain.repository.HealthConnectRepository
import com.screenassistant.core.iot.domain.repository.HomeAssistantRepository
import com.screenassistant.core.iot.domain.repository.IotRepository
import com.screenassistant.core.iot.domain.repository.MatterDeviceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para repositorios y wrappers de IoT (bindings abstractos).
 *
 * Proporciona bindings (@Binds) para interfaces del domain
 * hacia sus implementaciones en este módulo data.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IotDataModule {

    // ── @Binds — Repositories (interfaces del domain) ──────────────────────

    @Binds
    @Singleton
    abstract fun bindMatterDeviceRepository(impl: MatterDeviceRepositoryImpl): MatterDeviceRepository

    @Binds
    @Singleton
    abstract fun bindHealthConnectRepository(impl: HealthConnectRepositoryImpl): HealthConnectRepository

    @Binds
    @Singleton
    abstract fun bindCarAppRepository(impl: CarAppRepositoryImpl): CarAppRepository

    @Binds
    @Singleton
    abstract fun bindHomeAssistantRepository(impl: HomeAssistantRepositoryImpl): HomeAssistantRepository

    @Binds
    @Singleton
    abstract fun bindIotRepository(impl: IotRepositoryImpl): IotRepository
}

/**
 * Módulo Hilt para clases concretas de IoT (utils, DAOs).
 *
 * Los @Provides van en un módulo separado para evitar mezclar
 * bindings abstractos y concretos en el mismo módulo.
 */
@Module
@InstallIn(SingletonComponent::class)
object IotDataProvidesModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideIotEncryptedPrefs(
        @ApplicationContext context: Context,
    ): IotEncryptedPrefs = IotEncryptedPrefs(context)

    @Provides
    @Singleton
    @JvmStatic
    fun provideIotClock(): IotClock = RealIotClock()

    // ── @Provides — DAO ────────────────────────────────────────────────────

    @Provides
    @Singleton
    @JvmStatic
    fun provideIotDao(database: AppDatabase): IotDao = database.iotDao()

    // SyncIotDevicesUseCase se resuelve automáticamente via @Inject constructor
    // una vez que IotRepository tiene binding (bindIotRepository).
}
