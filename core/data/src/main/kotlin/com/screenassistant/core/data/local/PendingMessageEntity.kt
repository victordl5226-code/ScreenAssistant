package com.screenassistant.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val platform: String, // "WhatsApp", "SMS", "Telegram"
    val contactName: String,
    val contactNumber: String? = null,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
