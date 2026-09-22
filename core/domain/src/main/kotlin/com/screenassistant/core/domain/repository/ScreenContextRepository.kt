package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.model.ScreenMonitoringState
import kotlinx.coroutines.flow.StateFlow

interface ScreenContextRepository {
    val screenText: StateFlow<String>
    val activeAppPackage: StateFlow<String?>
    suspend fun captureScreenshot(): ImageData?
    fun updateScreenText(text: String)
    fun updateActiveApp(packageName: String?)

    // === Monitoreo continuo ===
    val monitoringState: StateFlow<ScreenMonitoringState>
    fun startMonitoring(intervalMs: Long = ScreenMonitoringState.Active.DEFAULT_INTERVAL_MS)
    fun stopMonitoring()
}
