package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.model.ScreenMonitoringState
import com.screenassistant.core.domain.repository.ScreenContextRepository
import kotlinx.coroutines.flow.StateFlow

class CaptureScreenContextUseCase(
    private val screenContextRepository: ScreenContextRepository
) {
    suspend fun captureScreenshot(): ImageData? {
        return screenContextRepository.captureScreenshot()
    }

    fun getScreenText(): String {
        return screenContextRepository.screenText.value
    }

    val activeAppPackage: StateFlow<String?>
        get() = screenContextRepository.activeAppPackage

    // === Monitoreo continuo (delegación) ===
    val monitoringState: StateFlow<ScreenMonitoringState>
        get() = screenContextRepository.monitoringState

    fun toggleMonitoring(intervalMs: Long = ScreenMonitoringState.Active.DEFAULT_INTERVAL_MS) {
        val current = screenContextRepository.monitoringState.value
        if (current is ScreenMonitoringState.Active) {
            screenContextRepository.stopMonitoring()
        } else {
            screenContextRepository.startMonitoring(intervalMs)
        }
    }

    fun startMonitoring(intervalMs: Long = ScreenMonitoringState.Active.DEFAULT_INTERVAL_MS) {
        screenContextRepository.startMonitoring(intervalMs)
    }

    fun stopMonitoring() {
        screenContextRepository.stopMonitoring()
    }
}
