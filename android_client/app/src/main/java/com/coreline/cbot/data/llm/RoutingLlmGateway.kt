package com.coreline.cbot.data.llm

import com.coreline.cbot.BuildConfig
import com.coreline.cbot.core.StockQueryClassifier
import com.coreline.cbot.domain.model.LlmRequest
import com.coreline.cbot.domain.model.LlmResponse
import com.coreline.cbot.domain.model.ProviderType
import com.coreline.cbot.domain.port.LlmGateway
import com.coreline.cbot.domain.port.SettingsProvider

class RoutingLlmGateway(
    private val settingsProvider: SettingsProvider,
    private val glmGateway: LlmGateway,
    private val openAiGateway: LlmGateway,
    private val codexProxyGateway: LlmGateway,
    private val stockProxyGateway: LlmGateway,
    private val stockQueryClassifier: StockQueryClassifier = StockQueryClassifier()
) : LlmGateway {
    override suspend fun generateReply(request: LlmRequest): Result<LlmResponse> {
        return when (settingsProvider.currentSettings().providerType) {
            ProviderType.GLM -> glmGateway.generateReply(request)
            ProviderType.OPENAI -> openAiGateway.generateReply(request)
            ProviderType.CODEX_PROXY -> codexProxyGateway.generateReply(request)
            ProviderType.STOCK_PROXY -> routeStockProxyRequest(request)
        }
    }

    private suspend fun routeStockProxyRequest(request: LlmRequest): Result<LlmResponse> {
        val userQuery = request.messages.lastOrNull { it.role == "user" }?.content?.trim().orEmpty()
        if (!stockQueryClassifier.shouldUseStockProxy(userQuery)) {
            return openAiGateway.generateReply(request.forOpenAiFallback())
                .map { response -> response.withRoute("route=openai_fallback_non_stock") }
        }

        return stockProxyGateway.generateReply(request)
            .map { response -> response.withRoute("route=stock_proxy") }
    }

    private fun LlmRequest.forOpenAiFallback(): LlmRequest {
        val settings = settingsProvider.currentSettings()
        return copy(model = settings.openAiModel.ifBlank { BuildConfig.OPENAI_MODEL })
    }

    private fun LlmResponse.withRoute(route: String): LlmResponse {
        val finish = listOfNotNull(route, finishReason).joinToString(" | ")
        return copy(finishReason = finish)
    }
}
