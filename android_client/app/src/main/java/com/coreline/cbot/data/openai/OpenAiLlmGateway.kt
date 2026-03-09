package com.coreline.cbot.data.openai

import com.coreline.cbot.data.zai.dto.ChatCompletionRequestDto
import com.coreline.cbot.data.zai.dto.ChatMessageDto
import com.coreline.cbot.domain.model.LlmFailure
import com.coreline.cbot.domain.model.LlmRequest
import com.coreline.cbot.domain.model.LlmResponse
import com.coreline.cbot.domain.model.TokenUsage
import com.coreline.cbot.domain.port.LlmGateway
import com.coreline.cbot.domain.port.SettingsProvider
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class OpenAiLlmGateway(
    private val apiService: OpenAiApiService,
    private val settingsProvider: SettingsProvider,
    private val embeddedOpenAiKeyProvider: () -> Result<String>,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) : LlmGateway {
    override suspend fun generateReply(request: LlmRequest): Result<LlmResponse> {
        return runCatching {
            val apiKey = settingsProvider.currentSettings().openAiApiKey
                .takeIf { it.isNotBlank() }
                ?: embeddedOpenAiKeyProvider().getOrElse { throw it }

            val startTime = nowMs()
            val response = apiService.createChatCompletion(
                authorization = "Bearer $apiKey",
                body = ChatCompletionRequestDto(
                    model = request.model,
                    messages = request.messages.map { ChatMessageDto(role = it.role, content = it.content) },
                    temperature = request.temperature,
                    maxTokens = request.maxTokens.takeUnless { usesMaxCompletionTokens(request.model) },
                    maxCompletionTokens = request.maxTokens
                        .takeIf { usesMaxCompletionTokens(request.model) }
                        ?.coerceAtLeast(256),
                    reasoningEffort = "none".takeIf { usesMaxCompletionTokens(request.model) },
                    stream = false,
                    thinking = null
                )
            )

            val latencyMs = nowMs() - startTime
            if (!response.isSuccessful) {
                throw mapHttpCode(response.code())
            }

            val body = response.body() ?: throw LlmFailure.EmptyBody()
            LlmResponse(
                text = body.choices.firstOrNull()?.message?.content,
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
            else -> LlmFailure.Unknown(IllegalStateException("OpenAI HTTP $code"))
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

    private fun usesMaxCompletionTokens(model: String): Boolean {
        return model.startsWith("gpt-5")
    }
}
