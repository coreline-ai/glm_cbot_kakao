package com.coreline.cbot.domain.usecase

import android.content.Context
import android.service.notification.StatusBarNotification
import com.coreline.cbot.core.RecentMessageGuard
import com.coreline.cbot.core.ResponseTargetFilter
import com.coreline.cbot.core.SecurityGuard
import com.coreline.cbot.core.WakeWordParser
import com.coreline.cbot.data.memory.InMemoryConversationMemoryStore
import com.coreline.cbot.data.monitoring.InMemoryMonitoringStore
import com.coreline.cbot.domain.model.IncomingChatMessage
import com.coreline.cbot.domain.model.LlmFailure
import com.coreline.cbot.domain.model.ProviderType
import com.coreline.cbot.domain.port.ReplySender
import com.coreline.cbot.domain.port.MutableSettingsProvider

class HandleIncomingNotificationUseCase(
    private val wakeWordParser: WakeWordParser,
    private val responseTargetFilter: ResponseTargetFilter,
    private val securityGuard: SecurityGuard,
    private val recentMessageGuard: RecentMessageGuard,
    private val conversationMemoryStore: InMemoryConversationMemoryStore,
    private val generateReplyUseCase: GenerateReplyUseCase,
    private val replySender: ReplySender,
    private val monitoringStore: InMemoryMonitoringStore,
    private val settingsProvider: MutableSettingsProvider
) {
    suspend operator fun invoke(
        context: Context,
        sbn: StatusBarNotification,
        message: IncomingChatMessage
    ) {
        monitoringStore.log("📩 [MESSAGE] room=${message.roomName} text=${message.rawText}")

        val query = wakeWordParser.extractQuery(message.rawText)
        if (query == null) {
            monitoringStore.log("💤 [SKIP] 호출어 없음")
            return
        }

        if (query.isBlank()) {
            monitoringStore.log("⚠️ [SKIP] 호출어 뒤 질문 없음")
            return
        }

        if (!responseTargetFilter.shouldRespond(message, query)) {
            monitoringStore.log("🪪 [SKIP] 응답 대상 필터 미일치")
            return
        }

        val messageSignature = "${message.roomName}::$query"
        if (!recentMessageGuard.shouldProcess(messageSignature, message.receivedAt)) {
            monitoringStore.log("🧯 [SKIP] 중복 메시지 감지")
            return
        }

        val securityVerdict = securityGuard.evaluate()
        if (!securityVerdict.allowed) {
            monitoringStore.markSecurityLock(securityVerdict.reason ?: "보안 정책 위반")
            monitoringStore.log("🔒 [SECURITY] ${securityVerdict.reason}")
            return
        }

        val history = conversationMemoryStore.getRecentTurns(message.roomName, message.receivedAt)
        if (history.isNotEmpty()) {
            monitoringStore.log("🧠 [MEMORY] 최근 ${history.size}턴 문맥 사용")
        }

        monitoringStore.markRequestStarted(query)
        val currentProvider = settingsProvider.currentSettings().providerType

        val llmResponse = generateReplyUseCase(query, history).getOrElse { error ->
            if (shouldSwitchProxyToGlm(currentProvider, error)) {
                switchProxyToGlmAndReply(context, sbn, currentProvider)
                return
            }

            when (error) {
                is LlmFailure.Unauthorized,
                is LlmFailure.Forbidden -> monitoringStore.markConfigRequired(error.message ?: "인증 오류")
                is LlmFailure.RateLimited -> monitoringStore.markFailure("LLM 사용량 제한 도달")
                is LlmFailure.Timeout -> monitoringStore.markFailure("LLM 응답 시간 초과")
                is LlmFailure.Network -> monitoringStore.markFailure("네트워크 연결 실패")
                is LlmFailure.Server -> monitoringStore.markFailure(error.message ?: "LLM 서버 오류")
                else -> monitoringStore.markFailure("LLM 호출 실패: ${error.message}")
            }
            return
        }

        monitoringStore.markResponseReceived(
            latencyMs = llmResponse.latencyMs,
            preview = llmResponse.text,
            requestId = llmResponse.requestId,
            finishReason = llmResponse.finishReason,
            tokenUsage = llmResponse.tokenUsage?.let {
                "p=${it.promptTokens ?: "-"} c=${it.completionTokens ?: "-"} t=${it.totalTokens ?: "-"}"
            }
        )

        val replyText = llmResponse.text
        if (replyText.isNullOrBlank()) {
            monitoringStore.log("⚠️ [LLM] 응답 없음")
            return
        }

        replySender.sendReply(context, sbn, replyText)
            .onSuccess {
                conversationMemoryStore.appendExchange(
                    roomName = message.roomName,
                    userMessage = query,
                    assistantMessage = replyText
                )
                monitoringStore.log("🧠 [MEMORY] 최근 대화 저장 완료")
                monitoringStore.markReplySent(replyText)
            }
            .onFailure { error ->
                monitoringStore.markFailure("자동 회신 실패: ${error.message}")
            }
    }

    private fun shouldSwitchProxyToGlm(
        providerType: ProviderType,
        error: Throwable
    ): Boolean {
        if (providerType != ProviderType.CODEX_PROXY && providerType != ProviderType.STOCK_PROXY) {
            return false
        }

        return error is LlmFailure.Network || error is LlmFailure.Timeout || error is LlmFailure.Server
    }

    private fun switchProxyToGlmAndReply(
        context: Context,
        sbn: StatusBarNotification,
        previousProvider: ProviderType
    ) {
        settingsProvider.switchProvider(ProviderType.GLM)
        val newSettings = settingsProvider.currentSettings()
        val responseModeLabel = when (newSettings.responseTargetMode) {
            com.coreline.cbot.domain.model.ResponseTargetMode.SELECTED_ONLY -> "선택 1"
            com.coreline.cbot.domain.model.ResponseTargetMode.ALL -> "선택 2"
        }
        monitoringStore.updateConfiguration(
            providerName = newSettings.currentProviderName(),
            model = newSettings.currentModel(),
            responseModeLabel = responseModeLabel
        )

        val replyText = PROXY_DISCONNECTED_REPLY
        monitoringStore.log("🔁 [FALLBACK] ${previousProvider.name} 연결 불가 -> GLM 전환")
        replySender.sendReply(context, sbn, replyText)
            .onSuccess {
                monitoringStore.markReplySent(replyText)
            }
            .onFailure { error ->
                monitoringStore.markFailure("자동 회신 실패: ${error.message}")
            }
    }

    private companion object {
        const val PROXY_DISCONNECTED_REPLY = "현재 연결이 끊겼습니다. LLM API 모드로 전환합니다."
    }
}
