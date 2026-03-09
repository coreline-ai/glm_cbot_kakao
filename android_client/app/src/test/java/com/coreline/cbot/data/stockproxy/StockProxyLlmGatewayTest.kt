package com.coreline.cbot.data.stockproxy

import com.coreline.cbot.domain.model.LlmFailure
import com.coreline.cbot.domain.model.LlmMessage
import com.coreline.cbot.domain.model.LlmRequest
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.net.SocketTimeoutException

class StockProxyLlmGatewayTest {
    private val request = LlmRequest(
        model = "stock-summary-v1",
        messages = listOf(LlmMessage(role = "user", content = "005930 최근 흐름 요약해줘")),
        temperature = 0.2,
        maxTokens = 256,
        thinkingDisabled = true
    )

    @Test
    fun `parses successful response`() = runTest {
        val gateway = StockProxyLlmGateway(
            apiService = object : StockProxyApiService {
                override suspend fun summary(body: StockProxySummaryRequestDto): Response<StockProxySummaryResponseDto> {
                    assertEquals("005930", body.symbol)
                    return Response.success(
                        StockProxySummaryResponseDto(
                            ok = true,
                            symbol = "005930",
                            model = "gpt-5.4",
                            text = "삼성전자 최근 하락 변동성이 큽니다.",
                            responseId = "resp_123",
                            toolCalls = listOf("get_quote", "get_recent_news")
                        )
                    )
                }
            }
        )

        val response = gateway.generateReply(request).getOrThrow()

        assertEquals("resp_123", response.requestId)
        assertEquals("삼성전자 최근 하락 변동성이 큽니다.", response.text)
        assertTrue(response.finishReason?.startsWith("tools:") == true)
    }

    @Test
    fun `extracts symbol from ks suffix query`() {
        val gateway = StockProxyLlmGateway(
            apiService = object : StockProxyApiService {
                override suspend fun summary(body: StockProxySummaryRequestDto): Response<StockProxySummaryResponseDto> {
                    error("not used")
                }
            }
        )

        assertEquals("005930", gateway.extractDomesticSymbol("005930.KS 최근 뉴스 알려줘"))
    }

    @Test
    fun `extracts symbol when korean text is attached`() {
        val gateway = StockProxyLlmGateway(
            apiService = object : StockProxyApiService {
                override suspend fun summary(body: StockProxySummaryRequestDto): Response<StockProxySummaryResponseDto> {
                    error("not used")
                }
            }
        )

        assertEquals("005930", gateway.extractDomesticSymbol("005930최근흐름요약"))
    }

    @Test
    fun `falls back to raw query when symbol code is missing`() = runTest {
        val gateway = StockProxyLlmGateway(
            apiService = object : StockProxyApiService {
                override suspend fun summary(body: StockProxySummaryRequestDto): Response<StockProxySummaryResponseDto> {
                    assertEquals("삼성전자 최근 흐름 요약해줘", body.symbol)
                    return Response.success(
                        StockProxySummaryResponseDto(
                            ok = true,
                            symbol = "005930",
                            model = "gpt-5.4",
                            text = "삼성전자 최근 하락 흐름입니다.",
                            responseId = "resp_name",
                            toolCalls = listOf("get_quote")
                        )
                    )
                }
            }
        )

        val response = gateway.generateReply(
            request.copy(messages = listOf(LlmMessage(role = "user", content = "삼성전자 최근 흐름 요약해줘")))
        ).getOrThrow()

        assertEquals("resp_name", response.requestId)
        assertEquals("삼성전자 최근 하락 흐름입니다.", response.text)
    }

    @Test
    fun `maps socket timeout to typed failure`() = runTest {
        val gateway = StockProxyLlmGateway(
            apiService = object : StockProxyApiService {
                override suspend fun summary(body: StockProxySummaryRequestDto): Response<StockProxySummaryResponseDto> {
                    throw SocketTimeoutException("timeout")
                }
            }
        )

        val failure = gateway.generateReply(request).exceptionOrNull()

        assertTrue(failure is LlmFailure.Timeout)
    }

    @Test
    fun `maps unauthorized response to typed failure`() = runTest {
        val gateway = StockProxyLlmGateway(
            apiService = object : StockProxyApiService {
                override suspend fun summary(body: StockProxySummaryRequestDto): Response<StockProxySummaryResponseDto> {
                    return Response.error(
                        401,
                        """{"error":"unauthorized"}""".toResponseBody("application/json".toMediaType())
                    )
                }
            }
        )

        val failure = gateway.generateReply(request).exceptionOrNull()

        assertTrue(failure is LlmFailure.Unauthorized)
    }
}
