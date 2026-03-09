package com.coreline.cbot.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coreline.cbot.data.monitoring.InMemoryMonitoringStore
import com.coreline.cbot.data.settings.AppSettingsStore
import com.coreline.cbot.domain.model.LlmFailure
import com.coreline.cbot.domain.model.ProviderType
import com.coreline.cbot.domain.model.ResponseTargetMode
import com.coreline.cbot.domain.usecase.GenerateReplyUseCase
import com.coreline.cbot.domain.usecase.GetProviderHealthUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val monitoringStore: InMemoryMonitoringStore,
    private val appSettingsStore: AppSettingsStore,
    private val generateReplyUseCase: GenerateReplyUseCase,
    private val getProviderHealthUseCase: GetProviderHealthUseCase
) : ViewModel() {
    private var lastPermissionGranted: Boolean = false

    val monitoringState: StateFlow<com.coreline.cbot.domain.model.MonitoringState> = monitoringStore.state
    val settingsState = appSettingsStore.state

    fun refreshStatus(notificationPermissionGranted: Boolean) {
        lastPermissionGranted = notificationPermissionGranted
        getProviderHealthUseCase(notificationPermissionGranted)
    }

    fun applySettings(
        providerType: ProviderType,
        responseTargetMode: ResponseTargetMode,
        openAiApiKey: String,
        openAiModel: String
    ) {
        appSettingsStore.update(
            providerType = providerType,
            responseTargetMode = responseTargetMode,
            openAiApiKey = openAiApiKey,
            openAiModel = openAiModel
        )
        monitoringStore.log("🛠️ [SETTINGS] provider=${providerType.name} mode=${responseTargetMode.name}")
        getProviderHealthUseCase(lastPermissionGranted)
    }

    fun runSelfTest(prompt: String) {
        viewModelScope.launch {
            val currentSettings = appSettingsStore.currentSettings()
            val normalizedPrompt = prompt.trim().ifBlank {
                if (currentSettings.providerType == ProviderType.STOCK_PROXY) {
                    "005930 최근 흐름 요약해줘"
                } else {
                    "하이"
                }
            }
            val providerName = currentSettings.currentProviderName()
            monitoringStore.log("🧪 [SELF-TEST] provider=$providerName prompt=$normalizedPrompt")
            monitoringStore.markRequestStarted("self-test: $normalizedPrompt")
            Log.i("CBotSelfTest", "start provider=$providerName prompt=$normalizedPrompt")

            val result = generateReplyUseCase(normalizedPrompt)
            result.onSuccess { response ->
                monitoringStore.markResponseReceived(
                    latencyMs = response.latencyMs,
                    preview = response.text,
                    requestId = response.requestId,
                    finishReason = response.finishReason,
                    tokenUsage = response.tokenUsage?.let {
                        "p=${it.promptTokens ?: "-"} c=${it.completionTokens ?: "-"} t=${it.totalTokens ?: "-"}"
                    }
                )
                monitoringStore.log("🧪 [SELF-TEST] 응답=${response.text.orEmpty()}")
                Log.i("CBotSelfTest", "success text=${response.text.orEmpty()}")
            }.onFailure { error ->
                val message = when (error) {
                    is LlmFailure.Unauthorized,
                    is LlmFailure.Forbidden -> error.message ?: "인증 오류"
                    is LlmFailure.RateLimited -> "LLM 사용량 제한 도달"
                    is LlmFailure.Timeout -> "LLM 응답 시간 초과"
                    is LlmFailure.Network -> "네트워크 연결 실패"
                    is LlmFailure.Server -> error.message ?: "LLM 서버 오류"
                    else -> "LLM 호출 실패: ${error.message}"
                }
                monitoringStore.markFailure(message)
                Log.e("CBotSelfTest", "failure $message", error)
            }
        }
    }
}
