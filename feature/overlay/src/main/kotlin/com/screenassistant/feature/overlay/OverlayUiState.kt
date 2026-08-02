package com.screenassistant.feature.overlay

import com.screenassistant.core.domain.model.ChatMessage

data class OverlayUiState(
    val assistantText: String = "",
    val animationState: AnimationState = AnimationState.IDLE,
    val isListening: Boolean = false,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val currentOutfitIndex: Int = 0,
    val currentOutfitRes: Int? = null,
    val messages: List<ChatMessage> = emptyList(),
    val showHelpCard: Boolean = false
)
