package com.coreline.cbot.data.openai

import com.coreline.cbot.data.security.EmbeddedSecretProvider
import com.coreline.cbot.data.zai.dto.ChatCompletionRequestDto
import com.coreline.cbot.data.zai.dto.ChatCompletionResponseDto
import com.coreline.cbot.data.zai.dto.ChoiceDto
import com.coreline.cbot.data.zai.dto.ResponseMessageDto
import com.coreline.cbot.domain.model.AppSettings
import com.coreline.cbot.domain.model.LlmMessage
import com.coreline.cbot.domain.model.LlmRequest
import com.coreline.cbot.domain.port.SettingsProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class OpenAiLlmGatewayTest {
    @Test
    fun `uses max completion tokens for gpt5 models`() = runTest {
        var capturedBody: ChatCompletionRequestDto? = null
        val gateway = OpenAiLlmGateway(
            apiService = object : OpenAiApiService {
                override suspend fun createChatCompletion(
                    authorization: String,
                    body: ChatCompletionRequestDto
                ): Response<ChatCompletionResponseDto> {
                    capturedBody = body
                    return Response.success(
                        ChatCompletionResponseDto(
                            id = "openai_req",
                            choices = listOf(
                                ChoiceDto(
                                    finishReason = "stop",
                                    message = ResponseMessageDto(content = "안녕하세요")
                                )
                            ),
                            usage = null
                        )
                    )
                }
            },
            settingsProvider = object : SettingsProvider {
                override fun currentSettings(): AppSettings = AppSettings(openAiApiKey = "openai-secret")
            },
            embeddedOpenAiKeyProvider = { Result.success("fallback") }
        )

        val response = gateway.generateReply(
            LlmRequest(
                model = "gpt-5.4",
                messages = listOf(LlmMessage(role = "user", content = "하이")),
                temperature = 0.2,
                maxTokens = 96,
                thinkingDisabled = true
            )
        ).getOrThrow()

        assertEquals("안녕하세요", response.text)
        assertNull(capturedBody?.maxTokens)
        assertEquals(256, capturedBody?.maxCompletionTokens)
        assertEquals("none", capturedBody?.reasoningEffort)
    }

    @Test
    fun `uses max tokens for gpt4 models`() = runTest {
        var capturedBody: ChatCompletionRequestDto? = null
        val gateway = OpenAiLlmGateway(
            apiService = object : OpenAiApiService {
                override suspend fun createChatCompletion(
                    authorization: String,
                    body: ChatCompletionRequestDto
                ): Response<ChatCompletionResponseDto> {
                    capturedBody = body
                    return Response.success(
                        ChatCompletionResponseDto(
                            id = "openai_req",
                            choices = listOf(
                                ChoiceDto(
                                    finishReason = "stop",
                                    message = ResponseMessageDto(content = "안녕하세요")
                                )
                            ),
                            usage = null
                        )
                    )
                }
            },
            settingsProvider = object : SettingsProvider {
                override fun currentSettings(): AppSettings = AppSettings(openAiApiKey = "openai-secret")
            },
            embeddedOpenAiKeyProvider = { Result.success("fallback") }
        )

        gateway.generateReply(
            LlmRequest(
                model = "gpt-4.1-mini",
                messages = listOf(LlmMessage(role = "user", content = "하이")),
                temperature = 0.2,
                maxTokens = 96,
                thinkingDisabled = true
            )
        ).getOrThrow()

        assertEquals(96, capturedBody?.maxTokens)
        assertNull(capturedBody?.maxCompletionTokens)
        assertNull(capturedBody?.reasoningEffort)
    }
}
