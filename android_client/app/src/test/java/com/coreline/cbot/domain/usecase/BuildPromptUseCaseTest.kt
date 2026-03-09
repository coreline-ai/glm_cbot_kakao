package com.coreline.cbot.domain.usecase

import com.coreline.cbot.core.PromptBuilder
import com.coreline.cbot.domain.model.AppSettings
import com.coreline.cbot.domain.model.ConversationTurn
import com.coreline.cbot.domain.port.SettingsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPromptUseCaseTest {
    @Test
    fun `adds recent room history before current question`() {
        val useCase = BuildPromptUseCase(PromptBuilder(), testSettingsProvider())

        val request = useCase(
            query = "계속 설명해줘",
            history = listOf(
                ConversationTurn(
                    userMessage = "어제 말한 일정 뭐였지?",
                    assistantMessage = "오후 3시에 미팅이 있어요.",
                    updatedAt = 1L
                )
            )
        )

        assertEquals("system", request.messages[0].role)
        assertEquals("user", request.messages[1].role)
        assertEquals("어제 말한 일정 뭐였지?", request.messages[1].content)
        assertEquals("assistant", request.messages[2].role)
        assertEquals("오후 3시에 미팅이 있어요.", request.messages[2].content)
        assertEquals("user", request.messages[3].role)
        assertEquals("계속 설명해줘", request.messages[3].content)
    }

    @Test
    fun `keeps context instruction in system prompt when history exists`() {
        val useCase = BuildPromptUseCase(PromptBuilder(), testSettingsProvider())

        val request = useCase(
            query = "이어서 말해줘",
            history = listOf(
                ConversationTurn("질문", "답변", 1L)
            )
        )

        assertTrue(request.messages.first().content.contains("최근 대화 문맥"))
    }

    private fun testSettingsProvider() = object : SettingsProvider {
        override fun currentSettings(): AppSettings = AppSettings()
    }
}
