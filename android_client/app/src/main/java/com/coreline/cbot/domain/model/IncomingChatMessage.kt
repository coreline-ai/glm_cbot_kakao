package com.coreline.cbot.domain.model

data class IncomingChatMessage(
    val roomName: String,
    val sender: String?,
    val rawText: String,
    val receivedAt: Long
)
