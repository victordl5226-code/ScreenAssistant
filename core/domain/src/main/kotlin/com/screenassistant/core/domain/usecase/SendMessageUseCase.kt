package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.ChatMessage
import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.ConversationRepository
import com.screenassistant.core.domain.repository.GeminiRepository
import com.screenassistant.core.domain.repository.MemoryRepository
import com.screenassistant.core.domain.repository.ScreenContextRepository
import kotlinx.coroutines.CoroutineDispatcher

sealed class SendMessageResult {
    data class AiResponse(val text: String, val message: ChatMessage) : SendMessageResult()
    data class CommandExecuted(val result: ActionResult, val message: ChatMessage) : SendMessageResult()
    data class Error(val messageText: String) : SendMessageResult()
}

class SendMessageUseCase(
    private val geminiRepository: GeminiRepository,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
    private val screenContextRepository: ScreenContextRepository,
    private val systemAction: SystemAction,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(
        text: String,
        isNetworkAvailable: Boolean,
        commandParser: SystemCommandParser = SystemCommandParser(systemAction),
        bitmap: ImageData? = null,
        screenText: String = ""
    ): SendMessageResult {
        val userMsg = ChatMessage(
            id = System.currentTimeMillis(),
            text = text,
            isFromUser = true
        )
        conversationRepository.addMessage(userMsg)

        // 1. Comando directo primero (funciona sin red)
        val commandResult = commandParser.parse(text)
        if (commandResult != null) {
            val responseMsg = ChatMessage(
                id = System.currentTimeMillis() + 1,
                text = commandResult,
                isFromUser = false
            )
            conversationRepository.addMessage(responseMsg)
            return SendMessageResult.CommandExecuted(
                result = ActionResult.Success(commandResult),
                message = responseMsg
            )
        }

        // 2. Sin red y sin comando directo → no se puede responder
        if (!isNetworkAvailable) {
            val errorText = "Lo siento, para pensar sobre eso necesito internet. Pero puedo ayudarte a llamar o buscar archivos si quieres."
            val responseMsg = ChatMessage(
                id = System.currentTimeMillis() + 1,
                text = errorText,
                isFromUser = false
            )
            conversationRepository.addMessage(responseMsg)
            return SendMessageResult.Error(errorText)
        }

        // 3. Con red → enviar a Gemini
        val prompt = if (screenText.isNotEmpty()) {
            "Analiza mi pantalla y mi petición.\nTexto en pantalla: $screenText\nUsuario dice: $text"
        } else {
            text
        }

        return try {
            val response = geminiRepository.sendMessage(prompt, bitmap)
            if (response != null) {
                val responseMsg = ChatMessage(
                    id = System.currentTimeMillis() + 1,
                    text = response,
                    isFromUser = false
                )
                conversationRepository.addMessage(responseMsg)
                SendMessageResult.AiResponse(response, responseMsg)
            } else {
                val errorText = "Vaya, me he despistado un segundo. ¿Me lo repites?"
                val responseMsg = ChatMessage(
                    id = System.currentTimeMillis() + 1,
                    text = errorText,
                    isFromUser = false
                )
                conversationRepository.addMessage(responseMsg)
                SendMessageResult.Error(errorText)
            }
        } catch (e: Exception) {
            val errorText = "Parece que mi conexión está un poco lenta. ¿Intentamos de nuevo?"
            val responseMsg = ChatMessage(
                id = System.currentTimeMillis() + 1,
                text = errorText,
                isFromUser = false
            )
            conversationRepository.addMessage(responseMsg)
            SendMessageResult.Error(errorText)
        }
    }
}
