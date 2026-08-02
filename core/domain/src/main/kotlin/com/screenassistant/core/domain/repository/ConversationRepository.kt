package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.ChatMessage
import kotlinx.coroutines.flow.StateFlow

interface ConversationRepository {
    val messages: StateFlow<List<ChatMessage>>
    suspend fun addMessage(message: ChatMessage)
    suspend fun clearConversation()
}
