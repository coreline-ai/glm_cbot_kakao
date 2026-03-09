package com.coreline.cbot.domain.usecase

import com.coreline.cbot.core.PromptBuilder
import com.coreline.cbot.core.ReplySanitizer
import com.coreline.cbot.domain.model.AppSettings
import com.coreline.cbot.domain.model.LlmRequest
import com.coreline.cbot.domain.model.LlmResponse
import com.coreline.cbot.domain.port.LlmGateway
import com.coreline.cbot.domain.port.SettingsProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerateReplyUseCaseTest {
    @Test
    fun `sanitizes provider response`() = runTest {
        val settingsStore = object : SettingsProvider {
            override fun currentSettings(): AppSettings = AppSettings()
        }
        val useCase = GenerateReplyUseCase(
            buildPromptUseCase = BuildPromptUseCase(PromptBuilder(), settingsStore),
            llmGateway = object : LlmGateway {
                override suspend fun generateReply(request: LlmRequest): Result<LlmResponse> {
                    return Result.success(
                        LlmResponse(
                            text = "```안녕하세요``` 답변입니다.",
                            requestId = "req_1",
                            latencyMs = 123,
                            finishReason = "stop",
                            tokenUsage = null
                        )
                    )
                }
            },
            replySanitizer = ReplySanitizer()
        )

        val result = useCase("테스트 질문").getOrThrow()

        assertEquals("안녕하세요 답변입니다.", result.text)
        assertEquals(123, result.latencyMs)
    }
}
