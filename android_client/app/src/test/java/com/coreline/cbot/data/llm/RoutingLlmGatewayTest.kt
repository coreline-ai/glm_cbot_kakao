package com.coreline.cbot.data.llm

import com.coreline.cbot.BuildConfig
import com.coreline.cbot.domain.model.AppSettings
import com.coreline.cbot.domain.model.LlmMessage
import com.coreline.cbot.domain.model.LlmFailure
import com.coreline.cbot.domain.model.LlmRequest
import com.coreline.cbot.domain.model.LlmResponse
import com.coreline.cbot.domain.model.ProviderType
import com.coreline.cbot.domain.port.LlmGateway
import com.coreline.cbot.domain.port.SettingsProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingLlmGatewayTest {
    private val stockRequest = LlmRequest(
        model = "stock-summary-v1",
        messages = listOf(LlmMessage(role = "user", content = "금일 한국주식시장 정리 해줘")),
        temperature = 0.2,
        maxTokens = 256,
        thinkingDisabled = true
    )

    @Test
    fun `routes stock questions to stock proxy when stock provider is selected`() = runTest {
        var stockCalls = 0
        var openAiCalls = 0
        val gateway = RoutingLlmGateway(
            settingsProvider = fixedSettingsProvider(),
            glmGateway = unusedGateway(),
            openAiGateway = gatewayOf {
                openAiCalls += 1
                LlmResponse("openai", "openai_req", 1, "stop", null)
            },
            codexProxyGateway = unusedGateway(),
            stockProxyGateway = gatewayOf {
                stockCalls += 1
                LlmResponse("stock", "stock_req", 1, "tools:get_quote", null)
            }
        )

        val response = gateway.generateReply(stockRequest).getOrThrow()

        assertEquals("stock", response.text)
        assertEquals(1, stockCalls)
        assertEquals(0, openAiCalls)
        assertTrue(response.finishReason?.contains("route=stock_proxy") == true)
    }

    @Test
    fun `falls back to openai for non stock prompt when stock provider is selected`() = runTest {
        var openAiModel = ""
        var stockCalls = 0
        val gateway = RoutingLlmGateway(
            settingsProvider = fixedSettingsProvider(),
            glmGateway = unusedGateway(),
            openAiGateway = gatewayOf { request ->
                openAiModel = request.model
                LlmResponse("일반 응답", "openai_req", 1, "stop", null)
            },
            codexProxyGateway = unusedGateway(),
            stockProxyGateway = gatewayOf {
                stockCalls += 1
                LlmResponse("stock", "stock_req", 1, "tools:get_quote", null)
            }
        )

        val response = gateway.generateReply(
            stockRequest.copy(messages = listOf(LlmMessage(role = "user", content = "동작하고 있어?")))
        ).getOrThrow()

        assertEquals("일반 응답", response.text)
        assertEquals(0, stockCalls)
        assertEquals(BuildConfig.OPENAI_MODEL, openAiModel)
        assertTrue(response.finishReason?.contains("route=openai_fallback_non_stock") == true)
    }

    @Test
    fun `bubbles stock proxy failure for stock prompt`() = runTest {
        val gateway = RoutingLlmGateway(
            settingsProvider = fixedSettingsProvider(),
            glmGateway = unusedGateway(),
            openAiGateway = unusedGateway(),
            codexProxyGateway = unusedGateway(),
            stockProxyGateway = object : LlmGateway {
                override suspend fun generateReply(request: LlmRequest): Result<LlmResponse> {
                    return Result.failure(LlmFailure.Network())
                }
            }
        )

        val failure = gateway.generateReply(stockRequest).exceptionOrNull()

        assertTrue(failure is LlmFailure.Network)
    }

    private fun fixedSettingsProvider(): SettingsProvider {
        return object : SettingsProvider {
            override fun currentSettings(): AppSettings {
                return AppSettings(
                    providerType = ProviderType.STOCK_PROXY,
                    openAiApiKey = "openai-secret",
                    openAiModel = ""
                )
            }
        }
    }

    private fun gatewayOf(block: suspend (LlmRequest) -> LlmResponse): LlmGateway {
        return object : LlmGateway {
            override suspend fun generateReply(request: LlmRequest): Result<LlmResponse> {
                return runCatching { block(request) }
            }
        }
    }

    private fun unusedGateway(): LlmGateway {
        return gatewayOf { error("unused") }
    }
}
