package com.coreline.cbot.domain.model

data class LlmMessage(
    val role: String,
    val content: String
)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Double,
    val maxTokens: Int,
    val thinkingDisabled: Boolean
)

data class TokenUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

data class LlmResponse(
    val text: String?,
    val requestId: String?,
    val latencyMs: Long,
    val finishReason: String?,
    val tokenUsage: TokenUsage?
)

data class ProviderHealth(
    val providerName: String,
    val model: String,
    val isReady: Boolean,
    val blockingReason: String? = null
)

sealed class LlmFailure(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    class Unauthorized : LlmFailure("LLM 인증 실패 (401)")
    class Forbidden : LlmFailure("LLM 접근 거부 (403)")
    class RateLimited : LlmFailure("LLM 사용량 제한 도달 (429)")
    class Server(code: Int) : LlmFailure("LLM 서버 오류 ($code)")
    class Timeout(cause: Throwable? = null) : LlmFailure("LLM 응답 시간 초과", cause)
    class Network(cause: Throwable? = null) : LlmFailure("네트워크 연결 실패", cause)
    class EmptyBody : LlmFailure("LLM 응답 본문 비어 있음")
    class Unknown(cause: Throwable? = null) : LlmFailure("LLM 호출 실패", cause)
}
