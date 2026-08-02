package com.screenassistant.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenassistant.core.domain.model.Message
import com.screenassistant.core.domain.di.IoDispatcher
import com.screenassistant.core.domain.repository.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private var nextId = 0L

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank()) return

        val userMessage = Message(
            id = nextId++,
            text = text,
            isFromUser = true
        )
        _messages.value = _messages.value + userMessage
        _inputText.value = ""
        _isLoading.value = true

        viewModelScope.launch(ioDispatcher) {
            try {
                val response = geminiRepository.sendMessage(text)
                val assistantMessage = Message(
                    id = nextId++,
                    text = response ?: "No pude procesar eso. Revisa tu conexión.",
                    isFromUser = false
                )
                _messages.value = _messages.value + assistantMessage
            } catch (e: Exception) {
                val errorMessage = Message(
                    id = nextId++,
                    text = "Error: ${e.localizedMessage ?: "Error desconocido"}",
                    isFromUser = false
                )
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
}
