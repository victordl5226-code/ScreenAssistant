package com.screenassistant.feature.overlay

data class OverlayUiState(
    val assistantText: String = "",
    val animationState: AnimationState = AnimationState.IDLE,
    val isListening: Boolean = false,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val currentOutfitIndex: Int = 0,
    val currentOutfitRes: Int? = null,
    val showHelpCard: Boolean = false
)
