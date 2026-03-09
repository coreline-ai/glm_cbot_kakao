package com.coreline.cbot.domain.model

enum class EngineStatus {
    INITIALIZING,
    PERMISSION_REQUIRED,
    CONFIG_REQUIRED,
    READY,
    BUSY,
    DEGRADED,
    SECURITY_LOCK
}

data class UiLogLine(
    val timestamp: String,
    val message: String
)

data class MonitoringState(
    val providerName: String,
    val model: String,
    val responseModeLabel: String,
    val isReady: Boolean,
    val engineStatus: EngineStatus,
    val lastLatencyMs: Long?,
    val lastSuccessAt: String?,
    val lastFailureAt: String?,
    val lastError: String?,
    val lastRequestId: String?,
    val lastFinishReason: String?,
    val lastTokenUsage: String?,
    val logs: List<UiLogLine>
)
