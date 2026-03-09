package com.coreline.cbot.data.codexproxy

import com.coreline.cbot.domain.model.LlmFailure
import com.coreline.cbot.domain.model.LlmMessage
import com.coreline.cbot.domain.model.LlmRequest
import com.coreline.cbot.domain.model.LlmResponse
import com.coreline.cbot.domain.model.TokenUsage
import com.coreline.cbot.domain.port.LlmGateway
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class CodexProxyLlmGateway(
    private val apiService: CodexProxyApiService
) : LlmGateway {
    override suspend fun generateReply(request: LlmRequest): Result<LlmResponse> {
        return runCatching {
            val response = apiService.chat(
                body = CodexProxyChatRequestDto(
                    prompt = renderPrompt(request.messages),
                    model = request.model,
                    systemPrompt = request.messages.firstOrNull { it.role == "system" }?.content,
                    timeoutMs = 60_000,
                    maxOutputTokens = request.maxTokens
                )
            )

            if (!response.isSuccessful) {
                throw mapHttpCode(response.code())
            }

            val body = response.body() ?: throw LlmFailure.EmptyBody()
            LlmResponse(
                text = body.text,
                requestId = body.requestId,
                latencyMs = body.elapsedMs,
                finishReason = body.finishReason,
                tokenUsage = body.usage?.let {
                    TokenUsage(
                        promptTokens = it.inputTokens,
                        completionTokens = it.outputTokens,
                        totalTokens = it.totalTokens
                    )
                }
            )
        }.recoverCatching { throwable ->
            throw mapThrowable(throwable)
        }
    }

    private fun renderPrompt(messages: List<LlmMessage>): String {
        return messages
            .filter { it.role != "system" }
            .joinToString(separator = "\n\n") { message ->
                "${message.role}: ${message.content}"
            }
    }

    private fun mapHttpCode(code: Int): Throwable {
        return when (code) {
            401 -> LlmFailure.Unauthorized()
            403 -> LlmFailure.Forbidden()
            429 -> LlmFailure.RateLimited()
            in 500..599 -> LlmFailure.Server(code)
            else -> LlmFailure.Unknown(IllegalStateException("Codex proxy HTTP $code"))
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
