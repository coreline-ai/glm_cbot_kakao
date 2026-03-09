package com.coreline.cbot.data.monitoring

import android.util.Log
import com.coreline.cbot.domain.model.EngineStatus
import com.coreline.cbot.domain.model.MonitoringState
import com.coreline.cbot.domain.model.UiLogLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InMemoryMonitoringStore(
    providerName: String,
    model: String,
    responseModeLabel: String
) {
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _state = MutableStateFlow(
        MonitoringState(
            providerName = providerName,
            model = model,
            responseModeLabel = responseModeLabel,
            isReady = false,
            engineStatus = EngineStatus.INITIALIZING,
            lastLatencyMs = null,
            lastSuccessAt = null,
            lastFailureAt = null,
            lastError = null,
            lastRequestId = null,
            lastFinishReason = null,
            lastTokenUsage = null,
            logs = emptyList()
        )
    )

    val state: StateFlow<MonitoringState> = _state.asStateFlow()

    fun log(message: String) {
        Log.i("CBotMonitor", message)
        mutate { current ->
            current.copy(logs = appendLog(current.logs, message))
        }
    }

    fun updateConfiguration(
        providerName: String,
        model: String,
        responseModeLabel: String
    ) {
        mutate { current ->
            current.copy(
                providerName = providerName,
                model = model,
                responseModeLabel = responseModeLabel
            )
        }
    }

    fun markPermissionRequired() {
        setStatus(EngineStatus.PERMISSION_REQUIRED, false, "알림 권한 필요")
    }

    fun markConfigRequired(message: String) {
        setStatus(EngineStatus.CONFIG_REQUIRED, false, message)
    }

    fun markReady() {
        mutate { current ->
            current.copy(
                isReady = true,
                engineStatus = EngineStatus.READY,
                lastError = null
            )
        }
    }

    fun markSecurityLock(reason: String) {
        mutate { current ->
            current.copy(
                isReady = false,
                engineStatus = EngineStatus.SECURITY_LOCK,
                lastError = reason,
                lastFailureAt = now()
            )
        }
    }

    fun markRequestStarted(query: String) {
        mutate { current ->
            current.copy(
                engineStatus = EngineStatus.BUSY,
                lastError = null,
                logs = appendLog(current.logs, "🚀 [LLM ${providerTag(current)}] 요청 시작: $query")
            )
        }
    }

    fun markReplySent(reply: String) {
        mutate { current ->
            current.copy(
                isReady = true,
                engineStatus = EngineStatus.READY,
                lastSuccessAt = now(),
                lastError = null,
                logs = appendLog(current.logs, "✅ [REPLY ${providerTag(current)}] $reply")
            )
        }
    }

    fun markResponseReceived(
        latencyMs: Long,
        preview: String?,
        requestId: String?,
        finishReason: String?,
        tokenUsage: String?
    ) {
        mutate { current ->
            current.copy(
                lastLatencyMs = latencyMs,
                isReady = true,
                engineStatus = EngineStatus.READY,
                lastSuccessAt = now(),
                lastError = null,
                lastRequestId = requestId,
                lastFinishReason = finishReason,
                lastTokenUsage = tokenUsage,
                logs = appendLog(
                    current.logs,
                    "🤖 [LLM ${providerTag(current)}] 응답 ${latencyMs}ms finish=${finishReason ?: "-"} req=${requestId ?: "-"}: ${preview.orEmpty()}"
                )
            )
        }
    }

    fun markFailure(message: String) {
        Log.e("CBotMonitor", message)
        mutate { current ->
            current.copy(
                isReady = false,
                engineStatus = EngineStatus.DEGRADED,
                lastFailureAt = now(),
                lastError = message,
                logs = appendLog(current.logs, "❌ [ERROR ${providerTag(current)}] $message")
            )
        }
    }

    fun setStatus(
        engineStatus: EngineStatus,
        isReady: Boolean,
        error: String?
    ) {
        mutate { current ->
            current.copy(
                engineStatus = engineStatus,
                isReady = isReady,
                lastError = error
            )
        }
    }

    private fun appendLog(existing: List<UiLogLine>, message: String): List<UiLogLine> {
        val updated = existing.toMutableList()
        updated.add(
            0,
            UiLogLine(
                timestamp = now(),
                message = message
            )
        )

        if (updated.size > 100) {
            updated.subList(100, updated.size).clear()
        }

        return updated
    }

    private fun mutate(transform: (MonitoringState) -> MonitoringState) {
        _state.value = transform(_state.value)
    }

    private fun providerTag(state: MonitoringState): String {
        return "${state.providerName}/${state.model}"
    }

    private fun now(): String = timeFormatter.format(Date())
}
