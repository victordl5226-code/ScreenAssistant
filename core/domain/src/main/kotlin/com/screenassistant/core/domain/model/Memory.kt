package com.screenassistant.core.domain.model

data class Memory(
    val id: Int = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
