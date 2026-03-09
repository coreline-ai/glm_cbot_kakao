package com.coreline.cbot.domain.usecase

import com.coreline.cbot.BuildConfig
import com.coreline.cbot.core.PromptBuilder
import com.coreline.cbot.domain.model.ConversationTurn
import com.coreline.cbot.domain.model.LlmMessage
import com.coreline.cbot.domain.model.LlmRequest
import com.coreline.cbot.domain.model.ProviderType
import com.coreline.cbot.domain.port.SettingsProvider

class BuildPromptUseCase(
    private val promptBuilder: PromptBuilder,
    private val settingsProvider: SettingsProvider
) {
    operator fun invoke(
        query: String,
        history: List<ConversationTurn> = emptyList()
    ): LlmRequest {
        val settings = settingsProvider.currentSettings()
        val spec = promptBuilder.build(query, history)
        val conversationMessages = history.flatMap { turn ->
            listOf(
                LlmMessage(role = "user", content = turn.userMessage),
                LlmMessage(role = "assistant", content = turn.assistantMessage)
            )
        }

        return LlmRequest(
            model = when (settings.providerType) {
                ProviderType.GLM -> BuildConfig.GLM_MODEL
                ProviderType.OPENAI -> settings.openAiModel.ifBlank { BuildConfig.OPENAI_MODEL }
                ProviderType.CODEX_PROXY -> BuildConfig.CODEX_PROXY_MODEL
                ProviderType.STOCK_PROXY -> BuildConfig.STOCK_PROXY_MODEL
            },
            messages = buildList {
                add(LlmMessage(role = "system", content = spec.systemPrompt))
                addAll(conversationMessages)
                add(LlmMessage(role = "user", content = spec.userPrompt))
            },
            temperature = spec.temperature,
            maxTokens = spec.maxTokens,
            thinkingDisabled = true
        )
    }
}
