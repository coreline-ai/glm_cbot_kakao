package com.coreline.cbot.data.codexproxy

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface CodexProxyApiService {
    @POST("api/v1/chat")
    suspend fun chat(
        @Body body: CodexProxyChatRequestDto
    ): Response<CodexProxyChatResponseDto>
}

data class CodexProxyChatRequestDto(
    val prompt: String,
    val model: String? = null,
    val systemPrompt: String? = null,
    val timeoutMs: Int? = null,
    val maxOutputTokens: Int? = null
)

data class CodexProxyChatResponseDto(
    val ok: Boolean,
    val provider: String,
    val model: String,
    val text: String?,
    val requestId: String?,
    val finishReason: String?,
    val elapsedMs: Long,
    val usage: CodexProxyUsageDto?
)

data class CodexProxyUsageDto(
    val inputTokens: Int?,
    val outputTokens: Int?,
    val totalTokens: Int?
)
