package com.screenassistant.feature.overlay

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
    val showHelpCard: Boolean = false
)
