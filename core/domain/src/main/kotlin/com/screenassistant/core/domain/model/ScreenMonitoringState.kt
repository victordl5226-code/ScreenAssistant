package com.screenassistant.core.domain.model

sealed class ScreenMonitoringState {
    data object Idle : ScreenMonitoringState()
    data class Active(
        val intervalMs: Long = DEFAULT_INTERVAL_MS,
        val screenText: String = ""
    ) : ScreenMonitoringState() {
        companion object {
            const val DEFAULT_INTERVAL_MS: Long = 5000L
        }
    }
    data class Error(val message: String) : ScreenMonitoringState()
}
