package com.coreline.cbot.domain.model

data class ConversationTurn(
    val userMessage: String,
    val assistantMessage: String,
    val updatedAt: Long
)
