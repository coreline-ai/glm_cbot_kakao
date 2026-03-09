package com.coreline.cbot.domain.model

enum class ProviderType {
    GLM,
    OPENAI,
    CODEX_PROXY,
    STOCK_PROXY
}

enum class ResponseTargetMode {
    SELECTED_ONLY,
    ALL
}

data class AppSettings(
    val providerType: ProviderType = ProviderType.GLM,
    val responseTargetMode: ResponseTargetMode = ResponseTargetMode.SELECTED_ONLY,
    val openAiApiKey: String = "",
    val openAiModel: String = ""
) {
    fun currentProviderName(): String {
        return when (providerType) {
            ProviderType.GLM -> com.coreline.cbot.BuildConfig.GLM_PROVIDER_NAME
            ProviderType.OPENAI -> com.coreline.cbot.BuildConfig.OPENAI_PROVIDER_NAME
            ProviderType.CODEX_PROXY -> com.coreline.cbot.BuildConfig.CODEX_PROXY_PROVIDER_NAME
            ProviderType.STOCK_PROXY -> com.coreline.cbot.BuildConfig.STOCK_PROXY_PROVIDER_NAME
        }
    }

    fun currentModel(): String {
        return when (providerType) {
            ProviderType.GLM -> com.coreline.cbot.BuildConfig.GLM_MODEL
            ProviderType.OPENAI -> openAiModel.ifBlank { com.coreline.cbot.BuildConfig.OPENAI_MODEL }
            ProviderType.CODEX_PROXY -> com.coreline.cbot.BuildConfig.CODEX_PROXY_MODEL
            ProviderType.STOCK_PROXY -> com.coreline.cbot.BuildConfig.STOCK_PROXY_MODEL
        }
    }
}
