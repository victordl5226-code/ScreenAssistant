package com.screenassistant.di

import android.content.Context
import androidx.room.Room
import com.screenassistant.core.data.local.ActiveAlarmStore
import com.screenassistant.core.data.local.AlarmDao
import com.screenassistant.core.data.local.AppDatabase
import com.screenassistant.core.data.local.ConversationTurnDao
import com.screenassistant.core.data.local.MemoryDao
import com.screenassistant.core.data.local.MessageDao
import com.screenassistant.core.data.local.ProactiveRuleDao
import com.screenassistant.core.data.local.UserPatternDao
import com.screenassistant.core.data.local.MessageQueueManager
import com.screenassistant.core.data.util.ApiKeyProvider
import com.screenassistant.core.data.util.PuenteConfigStore
import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.bridge.CommandBridge
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec
import com.screenassistant.core.domain.di.IoDispatcher
import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.domain.repository.ScreenContextRepository
import com.screenassistant.core.domain.service.TextToSpeech
import com.screenassistant.core.domain.usecase.AnalyzeScreenUseCase
import com.screenassistant.core.domain.usecase.CaptureScreenContextUseCase
import com.screenassistant.core.domain.usecase.MultiStepExecutor
import com.screenassistant.core.domain.usecase.MultiStepExecutorImpl
import com.screenassistant.core.domain.usecase.ScreenAnalysisPromptBuilder
import com.screenassistant.core.domain.usecase.SequenceParser
import com.screenassistant.core.domain.usecase.SequenceParserImpl
import com.screenassistant.core.domain.usecase.SystemCommandParser
import com.screenassistant.feature.overlay.TextToSpeechManager
import com.screenassistant.service.system.SystemActionHandler
import com.screenassistant.service.system.action.*
import com.screenassistant.service.system.bridge.*
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
            .addMigrations(
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .build()
    }

    @Provides
    fun provideMemoryDao(database: AppDatabase): MemoryDao = database.memoryDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideAlarmDao(database: AppDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideConversationTurnDao(database: AppDatabase): ConversationTurnDao = database.conversationTurnDao()

    @Provides
    fun provideUserPatternDao(database: AppDatabase): UserPatternDao = database.userPatternDao()

    @Provides
    fun provideProactiveRuleDao(database: AppDatabase): ProactiveRuleDao = database.proactiveRuleDao()

    @Provides
    @Singleton
    fun provideSystemAction(actionHandler: SystemActionHandler): SystemAction = actionHandler

    @Provides
    @Singleton
    fun provideSystemCommandParser(systemAction: SystemAction): SystemCommandParser = SystemCommandParser(systemAction)

    @Provides
    @Singleton
    fun provideSequenceParser(): SequenceParser = SequenceParserImpl()

    @Provides
    @Singleton
    fun provideMultiStepExecutor(
        systemAction: SystemAction,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): MultiStepExecutor = MultiStepExecutorImpl(systemAction, ioDispatcher, maxSteps = 10)

    @Provides
    @Singleton
    fun provideSystemCommandJsonCodec(): SystemCommandJsonCodec = SystemCommandJsonCodec()

    @Provides
    @Singleton
    fun provideCommandBridge(
        systemAction: SystemAction,
        codec: SystemCommandJsonCodec
    ): CommandBridge = SystemCommandBridgeImpl(systemAction, codec)

    @Provides
    @Singleton
    fun provideTextToSpeech(@ApplicationContext context: Context): TextToSpeech = TextToSpeechManager(context)

    @Provides
    @Singleton
    fun provideTaskerMessageHandler(
        bridge: CommandBridge,
        codec: SystemCommandJsonCodec,
        configStore: PuenteConfigStore,
    ): TaskerMessageHandler = TaskerMessageHandlerImpl(bridge, codec, configStore)

    @Provides
    @Singleton
    fun provideTaskerResponseEmitter(
        @ApplicationContext context: Context,
        tts: TextToSpeech,
        configStore: PuenteConfigStore,
    ): TaskerResponseEmitter = TaskerResponseEmitterImpl(context, tts, configStore)

    @Provides
    @Singleton
    fun provideUrlSender(): UrlSender = HttpUrlSender()

    @Provides
    @Singleton
    fun provideAutoRemoteUrlCallback(
        configStore: PuenteConfigStore,
        urlSender: UrlSender,
        @IoDispatcher io: CoroutineDispatcher,
    ): AutoRemoteUrlCallback = AutoRemoteUrlCallback(configStore, urlSender, io)

    @Provides
    @Singleton
    fun provideCaptureScreenContextUseCase(
        screenContextRepository: ScreenContextRepository
    ): CaptureScreenContextUseCase = CaptureScreenContextUseCase(screenContextRepository)

    @Provides
    @Singleton
    fun provideAssistantLanguage(): AssistantLanguage = AssistantLanguage.SPANISH

    @Provides
    @Singleton
    fun provideScreenAnalysisPromptBuilder(assistantLanguage: AssistantLanguage): ScreenAnalysisPromptBuilder = ScreenAnalysisPromptBuilder(assistantLanguage)

    @Provides
    @Singleton
    fun provideAnalyzeScreenUseCase(
        screenContextRepository: ScreenContextRepository,
        geminiRepository: GeminiRepository,
        promptBuilder: ScreenAnalysisPromptBuilder,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): AnalyzeScreenUseCase = AnalyzeScreenUseCase(screenContextRepository, geminiRepository, promptBuilder, ioDispatcher)

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
    fun provideAlarmNotificationHelper(@ApplicationContext context: Context): AlarmNotificationHelper = AlarmNotificationHelper(context)

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
    fun provideLanguageAction(geminiRepository: dagger.Lazy<GeminiRepository>): LanguageAction = LanguageAction(geminiRepository)

    @Provides
    @Singleton
    fun provideMemoryAction(memoryRepository: MemoryRepository): MemoryAction = MemoryAction(memoryRepository)

    @Provides
    @Singleton
    fun provideNoteAction(@ApplicationContext context: Context): NoteAction = NoteAction(context)

    @Provides
    @Singleton
    fun provideCalculatorAction(): CalculatorAction = CalculatorAction()

    @Provides
    @Singleton
    fun provideStopwatchAction(): StopwatchActionProvider = StopwatchActionProvider()

    @Provides
    @Singleton
    fun provideDeviceInfoAction(@ApplicationContext context: Context): DeviceInfoAction = DeviceInfoAction(context)

    @Provides
    @Singleton
    fun provideClipboardAction(@ApplicationContext context: Context): ClipboardAction = ClipboardAction(context)

    @Provides
    @Singleton
    fun provideContactsAction(@ApplicationContext context: Context): ContactsAction = ContactsAction(context)

    @Provides
    @Singleton
    fun provideWifiInfoAction(@ApplicationContext context: Context): WifiInfoAction = WifiInfoAction(context)

    @Provides
    @Singleton
    fun provideBluetoothAction(@ApplicationContext context: Context): BluetoothAction = BluetoothAction(context)

    @Provides
    @Singleton
    fun provideBrightnessAction(@ApplicationContext context: Context): BrightnessAction = BrightnessAction(context)

    @Provides
    @Singleton
    fun provideFlashlightAction(@ApplicationContext context: Context): FlashlightAction = FlashlightAction(context)

    @Provides
    @Singleton
    fun provideAirplaneModeAction(@ApplicationContext context: Context): AirplaneModeAction = AirplaneModeAction(context)

    @Provides
    @Singleton
    fun provideMobileDataAction(@ApplicationContext context: Context): MobileDataAction = MobileDataAction(context)

    @Provides
    @Singleton
    fun provideOpenFileAction(@ApplicationContext context: Context): OpenFileAction = OpenFileAction(context)

    @Provides
    @Singleton
    fun provideCallHistoryAction(@ApplicationContext context: Context): CallHistoryAction = CallHistoryAction(context)

    @Provides
    @Singleton
    fun provideQrScanAction(@ApplicationContext context: Context): QrScanAction = QrScanAction(context)

    @Provides
    @Singleton
    fun provideOcrAction(@ApplicationContext context: Context): OcrAction = OcrAction(context)

    @Provides
    @Singleton
    fun provideTranslateAction(@ApplicationContext context: Context): TranslateAction = TranslateAction(context)

    @Provides
    @Singleton
    fun provideFaceDetectionAction(@ApplicationContext context: Context): FaceDetectionAction = FaceDetectionAction(context)

    @Provides
    @Singleton
    fun provideApiKeyProvider(@ApplicationContext context: Context): ApiKeyProvider = ApiKeyProvider(context)

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
