package com.screenassistant.service.system

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
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.work.WorkManager
import com.screenassistant.core.domain.di.IoDispatcher
import com.screenassistant.core.domain.repository.ConversationRepository
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.ScreenContextRepository
import com.screenassistant.core.domain.usecase.CaptureScreenContextUseCase
import com.screenassistant.core.domain.usecase.SystemCommandParser
import com.screenassistant.feature.chat.ChatViewModel
import com.screenassistant.feature.overlay.AssistantOverlayUI
import com.screenassistant.feature.overlay.OverlayViewModel
import com.screenassistant.feature.overlay.OverlayWindowManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

@AndroidEntryPoint
class AssistantOverlayService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    @Inject lateinit var geminiRepository: GeminiRepository
    @Inject lateinit var commandParser: SystemCommandParser
    @Inject lateinit var conversationRepository: ConversationRepository
    @Inject lateinit var screenContextRepository: ScreenContextRepository
    @Inject lateinit var captureScreenContextUseCase: CaptureScreenContextUseCase
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
    private val overlayViewModel: OverlayViewModel by lazy {
        OverlayViewModel(
            geminiRepository = geminiRepository,
            conversationRepository = conversationRepository,
            screenContextRepository = screenContextRepository,
            commandParser = commandParser,
            captureScreenContextUseCase = captureScreenContextUseCase,
            ioDispatcher = ioDispatcher
        )
    }

    private val chatViewModel: ChatViewModel by lazy {
        ChatViewModel(
            geminiRepository = geminiRepository,
            ioDispatcher = ioDispatcher
        )
    }

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()

        startForegroundService()
        cancelConnectivityWorkerZombie()

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
        // Tras descomponer la UI (los ViewModels lazy mueren con la instancia del servicio)
        _viewModelStore.clear()
    }
}
