package com.coreline.cbot.data.zai.dto

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequestDto(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessageDto>,
    @SerializedName("temperature") val temperature: Double,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    @SerializedName("reasoning_effort") val reasoningEffort: String? = null,
    @SerializedName("stream") val stream: Boolean,
    @SerializedName("thinking") val thinking: ThinkingDto?
)

data class ChatMessageDto(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class ThinkingDto(
    @SerializedName("type") val type: String,
    @SerializedName("clear_thinking") val clearThinking: Boolean
)
