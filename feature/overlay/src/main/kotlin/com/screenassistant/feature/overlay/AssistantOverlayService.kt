package com.screenassistant.feature.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.screenassistant.core.data.local.MessageQueueManager
import com.screenassistant.core.domain.repository.ai.ConnectivityMonitor
import com.screenassistant.core.data.proactive.PatternLearnerWorker
import com.screenassistant.core.data.proactive.ProactiveCheckWorker
import com.screenassistant.core.domain.di.IoDispatcher
import java.util.concurrent.TimeUnit
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.domain.repository.PersonalityRepository
import com.screenassistant.core.domain.repository.ai.AiOrchestrator
import com.screenassistant.core.ai.memory.MemoryEnricher
import com.screenassistant.core.domain.usecase.AnalyzeScreenUseCase
import com.screenassistant.core.domain.usecase.CaptureScreenContextUseCase
import com.screenassistant.core.domain.usecase.GetTemporalContextUseCase
import com.screenassistant.core.domain.usecase.MultiStepExecutor
import com.screenassistant.core.domain.usecase.SequenceParser
import com.screenassistant.core.domain.usecase.SystemCommandParser
import com.screenassistant.core.data.proactive.ProactiveSuggestionManager
import com.screenassistant.core.nlp.parser.NlpCommandParser
import com.screenassistant.feature.overlay.hotword.HotwordPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

/**
 * D1 (Lote 9): el servicio del overlay vive en feature:overlay (movido desde
 * service:system — era la única violación de capas del proyecto). Hostea la UI
 * Compose y los ViewModels de la feature; NO depende de service:system.
 * El <service> se declara en el manifest de feature:overlay (se fusiona en el
 * de app); los permisos (FOREGROUND_SERVICE, SYSTEM_ALERT_WINDOW...) quedan en app.
 */
@AndroidEntryPoint
class AssistantOverlayService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    @Inject lateinit var geminiRepository: GeminiRepository
    @Inject lateinit var aiOrchestrator: AiOrchestrator
    @Inject lateinit var memoryEnricher: MemoryEnricher
    @Inject lateinit var commandParser: SystemCommandParser
    @Inject lateinit var nlpCommandParser: NlpCommandParser
    @Inject lateinit var sequenceParser: SequenceParser
    @Inject lateinit var multiStepExecutor: MultiStepExecutor
    @Inject lateinit var captureScreenContextUseCase: CaptureScreenContextUseCase
    @Inject lateinit var analyzeScreenUseCase: AnalyzeScreenUseCase
    @Inject lateinit var getTemporalContextUseCase: GetTemporalContextUseCase
    @Inject lateinit var proactiveSuggestionManager: ProactiveSuggestionManager
    @Inject lateinit var memoryRepository: MemoryRepository
    @Inject lateinit var personalityRepository: PersonalityRepository
    @Inject lateinit var messageQueueManager: MessageQueueManager
    @Inject lateinit var connectivityMonitor: ConnectivityMonitor
    @Inject lateinit var batteryMonitor: com.screenassistant.core.domain.repository.ai.BatteryMonitor
    @Inject lateinit var hotwordPreferences: HotwordPreferences
    @Inject lateinit var modelAssetRepository: com.screenassistant.core.model.domain.ModelAssetRepository
    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ComposeView
    private lateinit var overlayWindowManager: OverlayWindowManager

    private val _viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val viewModelStore: ViewModelStore get() = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    // Los ViewModels se construyen manualmente con dependencias inyectadas por Hilt:
    // los @HiltViewModel de los módulos feature NO se agregan al factory del servicio
    // (limitación de la agregación multi-módulo con KSP), así que se evita viewModel().
    // M15 (Lote 10): screenContextRepository ELIMINADO del passthrough — el use case
    // CaptureScreenContextUseCase ya lo lleva inyectado; el servicio solo inyecta el use case.
    private val overlayViewModel: OverlayViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OverlayViewModel(
                    geminiRepository = geminiRepository,
                    aiOrchestrator = aiOrchestrator,
                    memoryEnricher = memoryEnricher,
                    commandParser = commandParser,
                    nlpCommandParser = nlpCommandParser,
                    sequenceParser = sequenceParser,
                    multiStepExecutor = multiStepExecutor,
                    captureScreenContextUseCase = captureScreenContextUseCase,
                    analyzeScreenUseCase = analyzeScreenUseCase,
                    getTemporalContextUseCase = getTemporalContextUseCase,
                    proactiveSuggestionManager = proactiveSuggestionManager,
                    memoryRepository = memoryRepository,
                    messageQueueManager = messageQueueManager,
                    connectivityMonitor = connectivityMonitor,
                    batteryMonitor = batteryMonitor,
                    personalityRepository = personalityRepository,
                    hotwordPreferences = hotwordPreferences,
                    ioDispatcher = ioDispatcher,
                    context = applicationContext
                ) as T
            }
        })[OverlayViewModel::class.java]
    }

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()

        startForegroundService()
        cancelConnectivityWorkerZombie()
        schedulePeriodicWorkers()

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            overlayWindowManager = OverlayWindowManager(windowManager)
            showOverlay()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    /**
     * M22: el trabajo periódico que instalaciones ANTERIORES dejaron encolado bajo
     * "connectivity_worker" apunta a ConnectivityWorker (clase ELIMINADA en el Lote 8):
     * sin esta cancelación, WorkManager reintentaría cada hora un worker inexistente
     * (work zombie). Cancelación ÚNICA por nombre — no-op seguro si nunca se programó.
     */
    private fun cancelConnectivityWorkerZombie() {
        runCatching {
            WorkManager.getInstance(this).cancelUniqueWork("connectivity_worker")
        }
    }

    /**
     * Programa los workers periódicos del sistema proactivo y de aprendizaje de patrones.
     *
     * - [ProactiveCheckWorker]: cada 15 minutos, evalúa reglas proactivas contra el contexto.
     * - [PatternLearnerWorker]: una vez al día, limpia patrones antiguos y recalcula frecuencias.
     *
     * Se usa [ExistingPeriodicWorkPolicy.KEEP] para no duplicar trabajo si el servicio
     * se reinicia (ej. tras un crash del sistema).
     */
    private fun schedulePeriodicWorkers() {
        val workManager = WorkManager.getInstance(this)

        val proactiveRequest = PeriodicWorkRequestBuilder<ProactiveCheckWorker>(
            15, TimeUnit.MINUTES
        ).build()
        workManager.enqueueUniquePeriodicWork(
            ProactiveCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            proactiveRequest
        )

        val learnerRequest = PeriodicWorkRequestBuilder<PatternLearnerWorker>(
            1, TimeUnit.DAYS
        ).build()
        workManager.enqueueUniquePeriodicWork(
            PatternLearnerWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            learnerRequest
        )
    }

    private fun startForegroundService() {
        val channelId = "assistant_overlay_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun showOverlay() {
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AssistantOverlayService)
            setViewTreeViewModelStoreOwner(this@AssistantOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AssistantOverlayService)

            setContent {
                AssistantOverlayUI(
                    viewModel = overlayViewModel,
                    geminiRepository = geminiRepository,
                    commandParser = commandParser,
                    modelAssetRepository = modelAssetRepository,
                    onDrag = { dx, dy ->
                        overlayWindowManager.updatePosition(dx.toInt(), dy.toInt())
                    },
                    onFocusChange = { isFocused ->
                        overlayWindowManager.setFocusable(isFocused)
                    }
                )
            }
        }

        overlayWindowManager.addView(overlayView)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // M20 (Lote 10) — GARANTÍA REAL corregida: los ViewModels de la feature se
        // construyen MANUALMENTE (limitación KSP multi-módulo, ver overlayViewModel)
        // y NUNCA se registran en este store → `clear()` es un NO-OP formal
        // (`onCleared` del VM no se dispara por diseño). La limpieza real es:
        // (1) el DisposableEffect de AssistantOverlayUI destruye TTS/STT al
        // descomponerse el ComposeView (ANTES de este onDestroy — orden correcto);
        // (2) los `by lazy` del servicio mueren con la instancia por GC.
        // NO registrar los VMs (veto del Arquitecto: doble destroy de TTS/STT sin
        // valor). El ViewModelStore se MANTIENE porque `setViewTreeViewModelStoreOwner`
        // (showOverlay) lo requiere como estándar del host Compose en un servicio.
        _viewModelStore.clear()
    }
}
