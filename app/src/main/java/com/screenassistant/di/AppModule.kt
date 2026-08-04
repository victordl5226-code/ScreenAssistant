package com.screenassistant.di

import android.content.Context
import androidx.room.Room
import com.screenassistant.core.data.local.ActiveAlarmStore
import com.screenassistant.core.data.local.AlarmDao
import com.screenassistant.core.data.local.AppDatabase
import com.screenassistant.core.data.local.MemoryDao
import com.screenassistant.core.data.local.MessageDao
import com.screenassistant.core.data.local.MessageQueueManager
import com.screenassistant.core.data.util.ApiKeyProvider
import com.screenassistant.core.data.util.PuenteConfigStore
import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.bridge.CommandBridge
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec
import com.screenassistant.core.domain.di.IoDispatcher
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.domain.repository.ScreenContextRepository
import com.screenassistant.core.domain.service.TextToSpeech
import com.screenassistant.core.domain.usecase.CaptureScreenContextUseCase
import com.screenassistant.core.domain.usecase.SystemCommandParser
import com.screenassistant.feature.overlay.TextToSpeechManager
import com.screenassistant.service.system.SystemActionHandler
import com.screenassistant.service.system.action.AlarmAction
import com.screenassistant.service.system.action.AlarmNotificationHelper
import com.screenassistant.service.system.action.AppLauncherAction
import com.screenassistant.service.system.action.CallAction
import com.screenassistant.service.system.action.MediaAction
import com.screenassistant.service.system.action.MessagingAction
import com.screenassistant.service.system.action.SearchAction
import com.screenassistant.service.system.action.SettingsAction
import com.screenassistant.service.system.action.SystemVolumeAction
import com.screenassistant.service.system.action.TimerAction
import com.screenassistant.service.system.action.LanguageAction
import com.screenassistant.service.system.action.MapsAction
import com.screenassistant.service.system.action.MemoryAction
import com.screenassistant.service.system.action.NoteAction
import com.screenassistant.service.system.bridge.AutoRemoteUrlCallback
import com.screenassistant.service.system.bridge.HttpUrlSender
import com.screenassistant.service.system.bridge.SystemCommandBridgeImpl
import com.screenassistant.service.system.bridge.TaskerMessageHandler
import com.screenassistant.service.system.bridge.TaskerMessageHandlerImpl
import com.screenassistant.service.system.bridge.TaskerResponseEmitter
import com.screenassistant.service.system.bridge.TaskerResponseEmitterImpl
import com.screenassistant.service.system.bridge.UrlSender
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "assistant_db"
        )
            // QA #2: migración explícita v2→v3 (tabla alarms); la destructiva
            // queda solo como último recurso para versiones sin migración.
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMemoryDao(database: AppDatabase): MemoryDao {
        return database.memoryDao()
    }

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideAlarmDao(database: AppDatabase): AlarmDao {
        return database.alarmDao()
    }

    // SystemAction (interfaz domain) → SystemActionHandler
    @Provides
    @Singleton
    fun provideSystemAction(actionHandler: SystemActionHandler): SystemAction {
        return actionHandler
    }

    @Provides
    @Singleton
    fun provideSystemCommandParser(systemAction: SystemAction): SystemCommandParser {
        return SystemCommandParser(systemAction)
    }

    // === Puente Tasker (Lote 5 / Fase 1) ===

    @Provides
    @Singleton
    fun provideSystemCommandJsonCodec(): SystemCommandJsonCodec {
        return SystemCommandJsonCodec()
    }

    @Provides
    @Singleton
    fun provideCommandBridge(
        systemAction: SystemAction,
        codec: SystemCommandJsonCodec
    ): CommandBridge {
        return SystemCommandBridgeImpl(systemAction, codec)
    }

    // === Puente Tasker — Fase 2 (Lote 6 / ADR-014): transporte ===

    // (a) TTS — canal humano (D4/T4): binding HILT NUEVO TextToSpeech → TextToSpeechManager.
    // Riesgo aceptado (H7): coexisten dos instancias TTS (overlay y puente), cada una
    // con su PROPIO motor → riesgo real = habla SIMULTÁNEA de dos motores si coinciden,
    // no colisión de cola; uso secuencial aceptado (guion Tasker no corre con el overlay
    // hablando a la vez). Reutilizar la del overlay sería un refactor fuera de alcance.
    @Provides
    @Singleton
    fun provideTextToSpeech(@ApplicationContext context: Context): TextToSpeech {
        return TextToSpeechManager(context)
    }

    // === Puente Tasker — Fase 3A (Lote 7 / ADR-015 v1.2): token F1, privacidad F2, URL F3 ===
    // 4 providers (O2): el STORE no lleva @Provides — creación ÚNICA vía
    // @Inject constructor + @Singleton (precedente TaskerMessageHandlerImpl).
    // ApiKeyProvider conserva su @Provides por la carga inicial con BuildConfig
    // (caso distinto, no replicado). ELIMINADO en v1.2: provideTaskerOrigenVerifier
    // (muerto con el veto B1/H1, ADR-015 §2.6).

    @Provides
    @Singleton
    fun provideTaskerMessageHandler(
        bridge: CommandBridge,
        codec: SystemCommandJsonCodec,
        configStore: PuenteConfigStore,
    ): TaskerMessageHandler {
        return TaskerMessageHandlerImpl(bridge, codec, configStore)
    }

    // Sin @Inject constructor (precedente AlarmAction): se provee aquí con
    // @ApplicationContext (el Context sin calificador no es inyectable por Hilt).
    @Provides
    @Singleton
    fun provideTaskerResponseEmitter(
        @ApplicationContext context: Context,
        tts: TextToSpeech,
        configStore: PuenteConfigStore,
    ): TaskerResponseEmitter {
        return TaskerResponseEmitterImpl(context, tts, configStore)
    }

    @Provides
    @Singleton
    fun provideUrlSender(): UrlSender {
        return HttpUrlSender()
    }

    @Provides
    @Singleton
    fun provideAutoRemoteUrlCallback(
        configStore: PuenteConfigStore,
        urlSender: UrlSender,
        @IoDispatcher io: CoroutineDispatcher,
    ): AutoRemoteUrlCallback {
        return AutoRemoteUrlCallback(configStore, urlSender, io)
    }

    @Provides
    @Singleton
    fun provideCaptureScreenContextUseCase(
        screenContextRepository: ScreenContextRepository
    ): CaptureScreenContextUseCase {
        return CaptureScreenContextUseCase(screenContextRepository)
    }

    // === Acciones especializadas ===

    @Provides
    @Singleton
    fun provideCallAction(@ApplicationContext context: Context): CallAction = CallAction(context)

    @Provides
    @Singleton
    fun provideMessagingAction(
        @ApplicationContext context: Context,
        messageQueue: MessageQueueManager,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): MessagingAction = MessagingAction(context, messageQueue, ioDispatcher)

    @Provides
    @Singleton
    fun provideMediaAction(@ApplicationContext context: Context): MediaAction = MediaAction(context)

    @Provides
    @Singleton
    fun provideActiveAlarmStore(alarmDao: AlarmDao): ActiveAlarmStore = ActiveAlarmStore(alarmDao)

    @Provides
    @Singleton
    fun provideAlarmNotificationHelper(
        @ApplicationContext context: Context
    ): AlarmNotificationHelper = AlarmNotificationHelper(context)

    // AlarmAction tiene lambdas providers con valor por defecto (testabilidad);
    // Dagger no soporta defaults en constructores @Inject → se provee explícitamente
    // con los defaults de Kotlin (mismo patrón que NoteAction). Única vía de creación.
    @Provides
    @Singleton
    fun provideAlarmAction(
        @ApplicationContext context: Context,
        store: ActiveAlarmStore,
        notifier: AlarmNotificationHelper
    ): AlarmAction = AlarmAction(context, store, notifier)

    @Provides
    @Singleton
    fun provideSearchAction(@ApplicationContext context: Context): SearchAction = SearchAction(context)

    @Provides
    @Singleton
    fun provideAppLauncherAction(@ApplicationContext context: Context): AppLauncherAction = AppLauncherAction(context)

    @Provides
    @Singleton
    fun provideSystemVolumeAction(@ApplicationContext context: Context): SystemVolumeAction = SystemVolumeAction(context)

    @Provides
    @Singleton
    fun provideTimerAction(@ApplicationContext context: Context): TimerAction = TimerAction(context)

    @Provides
    @Singleton
    fun provideMapsAction(@ApplicationContext context: Context): MapsAction = MapsAction(context)

    @Provides
    @Singleton
    fun provideSettingsAction(@ApplicationContext context: Context): SettingsAction = SettingsAction(context)

    @Provides
    @Singleton
    fun provideLanguageAction(
        geminiRepository: dagger.Lazy<GeminiRepository>
    ): LanguageAction = LanguageAction(geminiRepository)

    @Provides
    @Singleton
    fun provideMemoryAction(
        memoryRepository: MemoryRepository
    ): MemoryAction = MemoryAction(memoryRepository)

    // NoteAction tiene params con valor por defecto (lambdas de test); Dagger no
    // soporta defaults en constructores @Inject → se provee explícitamente con los
    // defaults de Kotlin (mismo patrón que el resto de acciones).
    @Provides
    @Singleton
    fun provideNoteAction(@ApplicationContext context: Context): NoteAction = NoteAction(context)

    // ApiKeyProvider: la siembra inicial desde BuildConfig se movió a
    // ScreenAssistantApp.onCreate (B4, Lote 8) — Dagger es lazy y el primer
    // @Provides se disparaba en la primera inyección, no en el arranque.
    @Provides
    @Singleton
    fun provideApiKeyProvider(@ApplicationContext context: Context): ApiKeyProvider {
        return ApiKeyProvider(context)
    }

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO}
