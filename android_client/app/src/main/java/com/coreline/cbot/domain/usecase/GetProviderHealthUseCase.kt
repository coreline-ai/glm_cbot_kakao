package com.coreline.cbot.domain.usecase

import com.coreline.cbot.core.SecurityGuard
import com.coreline.cbot.data.monitoring.InMemoryMonitoringStore
import com.coreline.cbot.data.security.EmbeddedSecretProvider
import com.coreline.cbot.domain.model.EngineStatus
import com.coreline.cbot.domain.model.ProviderHealth
import com.coreline.cbot.domain.model.ProviderType
import com.coreline.cbot.domain.port.SettingsProvider

class GetProviderHealthUseCase(
    private val settingsProvider: SettingsProvider,
    private val embeddedSecretProvider: EmbeddedSecretProvider,
    private val securityGuard: SecurityGuard,
    private val monitoringStore: InMemoryMonitoringStore
) {
    operator fun invoke(notificationPermissionGranted: Boolean): ProviderHealth {
        val settings = settingsProvider.currentSettings()
        val providerName = settings.currentProviderName()
        val model = settings.currentModel()
        monitoringStore.updateConfiguration(
            providerName = providerName,
            model = model,
            responseModeLabel = when (settings.responseTargetMode) {
                com.coreline.cbot.domain.model.ResponseTargetMode.SELECTED_ONLY -> "선택 1"
                com.coreline.cbot.domain.model.ResponseTargetMode.ALL -> "선택 2"
            }
        )

        if (!notificationPermissionGranted) {
            monitoringStore.markPermissionRequired()
            return ProviderHealth(
                providerName = providerName,
                model = model,
                isReady = false,
                blockingReason = "알림 권한 필요"
            )
        }

        val securityVerdict = securityGuard.evaluate()
        if (!securityVerdict.allowed) {
            monitoringStore.setStatus(
                engineStatus = EngineStatus.SECURITY_LOCK,
                isReady = false,
                error = securityVerdict.reason ?: "보안 잠금"
            )
            return ProviderHealth(
                providerName = providerName,
                model = model,
                isReady = false,
                blockingReason = securityVerdict.reason
            )
        }

        val keyResult = when (settings.providerType) {
            ProviderType.GLM -> embeddedSecretProvider.getGlmApiKey()
            ProviderType.OPENAI -> {
                val apiKey = settings.openAiApiKey.takeIf { it.isNotBlank() }
                if (apiKey == null) {
                    embeddedSecretProvider.getOpenAiApiKey()
                } else {
                    Result.success(apiKey)
                }
            }
            ProviderType.CODEX_PROXY -> Result.success("proxy")
            ProviderType.STOCK_PROXY -> Result.success("proxy")
        }
        if (keyResult.isFailure) {
            val message = keyResult.exceptionOrNull()?.message ?: "설정/릴리스 필요"
            monitoringStore.markConfigRequired(message)
            return ProviderHealth(
                providerName = providerName,
                model = model,
                isReady = false,
                blockingReason = message
            )
        }

        monitoringStore.markReady()
        return ProviderHealth(
            providerName = providerName,
            model = model,
            isReady = true
        )
    }
}
