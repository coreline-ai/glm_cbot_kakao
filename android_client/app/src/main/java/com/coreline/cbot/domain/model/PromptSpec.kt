package com.coreline.cbot.domain.model

data class PromptSpec(
    val systemPrompt: String,
    val userPrompt: String,
    val maxTokens: Int,
    val temperature: Double
)
