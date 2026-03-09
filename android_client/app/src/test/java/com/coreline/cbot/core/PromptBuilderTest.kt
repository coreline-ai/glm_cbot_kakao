package com.coreline.cbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    private val promptBuilder = PromptBuilder()

    @Test
    fun `builds short korean assistant prompt`() {
        val prompt = promptBuilder.build("오늘 일정 알려줘")

        assertEquals("오늘 일정 알려줘", prompt.userPrompt)
        assertEquals(256, prompt.maxTokens)
        assertEquals(0.2, prompt.temperature, 0.0)
        assertTrue(prompt.systemPrompt.contains("최대 6문장"))
    }

    @Test
    fun `mentions current-question-only rule when no history exists`() {
        val prompt = promptBuilder.build("오늘 일정 알려줘")

        assertTrue(prompt.systemPrompt.contains("현재 질문만 기준"))
    }
}
