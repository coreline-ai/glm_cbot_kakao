package com.coreline.cbot.data.zai.dto

import com.google.gson.annotations.SerializedName

data class ChatCompletionResponseDto(
    @SerializedName("id") val id: String?,
    @SerializedName("choices") val choices: List<ChoiceDto>,
    @SerializedName("usage") val usage: UsageDto?
)

data class ChoiceDto(
    @SerializedName("finish_reason") val finishReason: String?,
    @SerializedName("message") val message: ResponseMessageDto
)

data class ResponseMessageDto(
    @SerializedName("content") val content: String?
)

data class UsageDto(
    @SerializedName("prompt_tokens") val promptTokens: Int?,
    @SerializedName("completion_tokens") val completionTokens: Int?,
    @SerializedName("total_tokens") val totalTokens: Int?
)
