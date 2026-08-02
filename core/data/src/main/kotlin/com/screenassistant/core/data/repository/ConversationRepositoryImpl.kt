package com.screenassistant.core.data.repository

import com.screenassistant.core.domain.model.ChatMessage
import com.screenassistant.core.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor() : ConversationRepository {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    override suspend fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    override suspend fun clearConversation() {
        _messages.value = emptyList()
    }
}
