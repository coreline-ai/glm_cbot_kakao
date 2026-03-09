package com.coreline.cbot.data.stockproxy

import com.coreline.cbot.domain.model.LlmFailure
import com.coreline.cbot.domain.model.LlmRequest
import com.coreline.cbot.domain.model.LlmResponse
import com.coreline.cbot.domain.port.LlmGateway
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class StockProxyLlmGateway(
    private val apiService: StockProxyApiService
) : LlmGateway {
    override suspend fun generateReply(request: LlmRequest): Result<LlmResponse> {
        return runCatching {
            val userQuery = request.messages.lastOrNull { it.role == "user" }?.content?.trim().orEmpty()
            val symbol = extractDomesticSymbol(userQuery) ?: userQuery

            val startedAt = System.currentTimeMillis()
            val response = apiService.summary(
                StockProxySummaryRequestDto(
                    symbol = symbol,
                    question = userQuery,
                    includeNews = true,
                    candlePoints = 10
                )
            )
            val latencyMs = System.currentTimeMillis() - startedAt

            if (!response.isSuccessful) {
                throw mapHttpCode(response.code())
            }

            val body = response.body() ?: throw LlmFailure.EmptyBody()
            LlmResponse(
                text = body.text,
                requestId = body.responseId,
                latencyMs = latencyMs,
                finishReason = body.toolCalls?.joinToString(prefix = "tools:", separator = ","),
                tokenUsage = null
            )
        }.recoverCatching { throwable ->
            throw mapThrowable(throwable)
        }
    }

    internal fun extractDomesticSymbol(query: String): String? {
        return Regex("""(?<!\d)(\d{6})(?:\.[A-Z]{2,4})?(?!\d)""")
            .find(query.uppercase())
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun mapHttpCode(code: Int): Throwable {
        return when (code) {
            401 -> LlmFailure.Unauthorized()
            403 -> LlmFailure.Forbidden()
            429 -> LlmFailure.RateLimited()
            in 500..599 -> LlmFailure.Server(code)
            else -> LlmFailure.Unknown(IllegalStateException("Stock proxy HTTP $code"))
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
