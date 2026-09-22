package com.screenassistant.feature.overlay

import com.screenassistant.core.domain.model.AssistantMode
import com.screenassistant.core.domain.model.ScreenMonitoringState
import com.screenassistant.core.domain.repository.ai.MemoryTurn

data class OverlayUiState(
    val assistantText: String = "",
    val animationState: AnimationState = AnimationState.IDLE,
    val isListening: Boolean = false,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val currentOutfitIndex: Int = 0,
    // M15 (Lote 10): currentOutfitRes ELIMINADO — nunca se escribía ni leía;
    // la UI usa characterState.currentAssetRes (CharacterState.currentOutfitRes
    // es un var real de otro objeto, se mantiene con su test).
    val showHelpCard: Boolean = false,
    // === Monitoreo continuo ===
    val isMonitoring: Boolean = false,
    val monitoringIntervalMs: Long = ScreenMonitoringState.Active.DEFAULT_INTERVAL_MS,
    val monitoringScreenText: String = "",
    // === Multi-paso (A1: Lote MULTI) ===
    // Nullable: null = sin secuencia en curso (el cierre de la secuencia limpia a null).
    val executingStep: Int? = null,
    val totalSteps: Int? = null,
    val currentStepDescription: String = "",
    val isMultiStep: Boolean = false,
    val multiStepError: String? = null,
    // === Historial de conversación ===
    val showHistory: Boolean = false,
    val historyTurns: List<MemoryTurn> = emptyList(),
    // === Nombre del asistente (onboarding) ===
    val assistantName: String = "J.A.R.V.I.S.",
    // === Hardware Status ===
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val isConnected: Boolean = true,
    // === Hotword Detection ===
    val hotwordEnabled: Boolean = false,
    val hotwordListening: Boolean = false,
    // === Modo del asistente (Fase J.A.R.V.I.S.) ===
    val assistantMode: AssistantMode = AssistantMode.TACTICO,
)
