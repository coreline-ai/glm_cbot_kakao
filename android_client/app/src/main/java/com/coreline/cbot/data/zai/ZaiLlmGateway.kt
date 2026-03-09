package com.coreline.cbot.data.zai

import com.coreline.cbot.data.zai.dto.ChatCompletionRequestDto
import com.coreline.cbot.data.zai.dto.ChatMessageDto
import com.coreline.cbot.data.zai.dto.ThinkingDto
import com.coreline.cbot.domain.model.LlmFailure
import com.coreline.cbot.domain.model.LlmRequest
import com.coreline.cbot.domain.model.LlmResponse
import com.coreline.cbot.domain.model.TokenUsage
import com.coreline.cbot.domain.port.LlmGateway
import com.coreline.cbot.domain.port.SecretProvider
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class ZaiLlmGateway(
    private val apiService: ZaiApiService,
    private val secretProvider: SecretProvider,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) : LlmGateway {
    override suspend fun generateReply(request: LlmRequest): Result<LlmResponse> {
        return runCatching {
            val apiKey = secretProvider.getGlmApiKey().getOrThrow()
            val startTime = nowMs()
            val response = apiService.createChatCompletion(
                authorization = "Bearer $apiKey",
                body = ChatCompletionRequestDto(
                    model = request.model,
                    messages = request.messages.map { ChatMessageDto(role = it.role, content = it.content) },
                    temperature = request.temperature,
                    maxTokens = request.maxTokens,
                    maxCompletionTokens = null,
                    reasoningEffort = null,
                    stream = false,
                    thinking = if (request.thinkingDisabled) {
                        ThinkingDto(type = "disabled", clearThinking = true)
                    } else {
                        null
                    }
                )
            )

            val latencyMs = nowMs() - startTime
            if (!response.isSuccessful) {
                throw mapHttpCode(response.code())
            }

            val body = response.body() ?: throw LlmFailure.EmptyBody()
            val text = body.choices.firstOrNull()?.message?.content
            LlmResponse(
                text = text,
                requestId = body.id,
                latencyMs = latencyMs,
                finishReason = body.choices.firstOrNull()?.finishReason,
                tokenUsage = body.usage?.let {
                    TokenUsage(
                        promptTokens = it.promptTokens,
                        completionTokens = it.completionTokens,
                        totalTokens = it.totalTokens
                    )
                }
            )
        }.recoverCatching { throwable ->
            throw mapThrowable(throwable)
        }
    }

    private fun mapHttpCode(code: Int): Throwable {
        return when (code) {
            401 -> LlmFailure.Unauthorized()
            403 -> LlmFailure.Forbidden()
            429 -> LlmFailure.RateLimited()
            in 500..599 -> LlmFailure.Server(code)
            else -> LlmFailure.Unknown(IllegalStateException("GLM HTTP $code"))
        }
    }

    private fun mapThrowable(throwable: Throwable): Throwable {
        return when (throwable) {
            is LlmFailure -> throwable
            is SocketTimeoutException -> LlmFailure.Timeout(throwable)
            is IOException -> LlmFailure.Network(throwable)
            is HttpException -> mapHttpCode(throwable.code())
            else -> LlmFailure.Unknown(throwable)
        }
    }
}
