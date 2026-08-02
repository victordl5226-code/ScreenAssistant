package com.screenassistant.core.domain.model

data class Message(
    val id: Long,
    val text: String,
    val isFromUser: Boolean
)
