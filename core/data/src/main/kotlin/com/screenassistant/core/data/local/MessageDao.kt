package com.screenassistant.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert
    suspend fun insertMessage(message: PendingMessageEntity)

    @Query("SELECT * FROM pending_messages ORDER BY timestamp ASC")
    fun getAllPendingMessages(): Flow<List<PendingMessageEntity>>

    @Query("DELETE FROM pending_messages WHERE id = :id")
    suspend fun deleteMessage(id: Int)

    @Query("DELETE FROM pending_messages")
    suspend fun clearAll()
}
