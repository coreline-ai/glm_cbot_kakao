package com.coreline.cbot.core

import com.coreline.cbot.domain.model.IncomingChatMessage
import com.coreline.cbot.domain.model.ResponseTargetMode
import com.coreline.cbot.domain.port.SettingsProvider

class ResponseTargetFilter(
    private val settingsProvider: SettingsProvider
) {
    private val selectedMarkers = listOf("최경환", "JU신이라불러라")

    fun shouldRespond(message: IncomingChatMessage, query: String): Boolean {
        val settings = settingsProvider.currentSettings()
        if (settings.responseTargetMode == ResponseTargetMode.ALL) {
            return true
        }

        val candidates = listOf(
            message.roomName,
            message.sender.orEmpty(),
            message.rawText,
            query
        )

        return selectedMarkers.any { marker ->
            candidates.any { candidate -> candidate.contains(marker, ignoreCase = true) }
        }
    }

    fun modeDescription(): String {
        return when (settingsProvider.currentSettings().responseTargetMode) {
            ResponseTargetMode.SELECTED_ONLY -> "최경환/JU신이라불러라만 응답"
            ResponseTargetMode.ALL -> "모든 요청 응답"
        }
    }
}
