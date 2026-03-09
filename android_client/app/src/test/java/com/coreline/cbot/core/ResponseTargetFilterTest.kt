package com.coreline.cbot.core

import com.coreline.cbot.domain.model.AppSettings
import com.coreline.cbot.domain.model.IncomingChatMessage
import com.coreline.cbot.domain.model.ResponseTargetMode
import com.coreline.cbot.domain.port.SettingsProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseTargetFilterTest {
    @Test
    fun `selected only mode allows matching room markers`() {
        val settingsStore = object : SettingsProvider {
            override fun currentSettings(): AppSettings {
                return AppSettings(responseTargetMode = ResponseTargetMode.SELECTED_ONLY)
            }
        }
        val filter = ResponseTargetFilter(settingsStore)

        val allowed = filter.shouldRespond(
            message = IncomingChatMessage(
                roomName = "JU신이라불러라 투자방",
                sender = null,
                rawText = "코비서 하이",
                receivedAt = 1L
            ),
            query = "하이"
        )

        assertTrue(allowed)
    }

    @Test
    fun `selected only mode blocks unrelated messages`() {
        val settingsStore = object : SettingsProvider {
            override fun currentSettings(): AppSettings {
                return AppSettings(responseTargetMode = ResponseTargetMode.SELECTED_ONLY)
            }
        }
        val filter = ResponseTargetFilter(settingsStore)

        val allowed = filter.shouldRespond(
            message = IncomingChatMessage(
                roomName = "일반 대화방",
                sender = null,
                rawText = "코비서 하이",
                receivedAt = 1L
            ),
            query = "하이"
        )

        assertFalse(allowed)
    }
}
