package com.screenassistant.core.data.local

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageQueueManager @Inject constructor(
    private val messageDao: MessageDao
) {
    fun getPendingMessages(): Flow<List<PendingMessageEntity>> = messageDao.getAllPendingMessages()

    suspend fun addPendingMessage(platform: String, contact: String, number: String?, message: String) {
        messageDao.insertMessage(
            PendingMessageEntity(
                platform = platform,
                contactName = contact,
                contactNumber = number,
                message = message
            )
        )
    }

    suspend fun removeMessage(id: Int) {
        messageDao.deleteMessage(id)
    }

    suspend fun clear() {
        messageDao.clearAll()
    }
}
