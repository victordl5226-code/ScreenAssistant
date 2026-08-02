package com.screenassistant.core.domain.model

data class ChatMessage(
    val id: Long,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
