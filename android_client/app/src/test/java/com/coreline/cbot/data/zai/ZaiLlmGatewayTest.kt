package com.coreline.cbot.data.zai

import com.coreline.cbot.data.zai.dto.ChatCompletionResponseDto
import com.coreline.cbot.data.zai.dto.ChoiceDto
import com.coreline.cbot.data.zai.dto.ResponseMessageDto
import com.coreline.cbot.data.zai.dto.UsageDto
import com.coreline.cbot.domain.model.LlmFailure
import com.coreline.cbot.domain.model.LlmMessage
import com.coreline.cbot.domain.model.LlmRequest
import com.coreline.cbot.domain.port.SecretProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.net.SocketTimeoutException

class ZaiLlmGatewayTest {
    private val request = LlmRequest(
        model = "glm-4.5-flash",
        messages = listOf(LlmMessage(role = "user", content = "안녕")),
        temperature = 0.2,
        maxTokens = 96,
        thinkingDisabled = true
    )

    private val secretProvider = object : SecretProvider {
        override fun getGlmApiKey(): Result<String> = Result.success("secret")
    }

    @Test
    fun `parses successful response`() = runTest {
        val gateway = ZaiLlmGateway(
            apiService = object : ZaiApiService {
                override suspend fun createChatCompletion(
                    authorization: String,
                    body: com.coreline.cbot.data.zai.dto.ChatCompletionRequestDto
                ): Response<ChatCompletionResponseDto> {
                    return Response.success(
                        ChatCompletionResponseDto(
                            id = "req_123",
                            choices = listOf(
                                ChoiceDto(
                                    finishReason = "stop",
                                    message = ResponseMessageDto(content = "안녕하세요")
                                )
                            ),
                            usage = UsageDto(
                                promptTokens = 10,
                                completionTokens = 12,
                                totalTokens = 22
                            )
                        )
                    )
                }
            },
            secretProvider = secretProvider
        )

        val response = gateway.generateReply(request).getOrThrow()

        assertEquals("req_123", response.requestId)
        assertEquals("stop", response.finishReason)
        assertEquals("안녕하세요", response.text)
        assertEquals(22, response.tokenUsage?.totalTokens)
    }

    @Test
    fun `maps unauthorized response to typed failure`() = runTest {
        val gateway = ZaiLlmGateway(
            apiService = object : ZaiApiService {
                override suspend fun createChatCompletion(
                    authorization: String,
                    body: com.coreline.cbot.data.zai.dto.ChatCompletionRequestDto
                ): Response<ChatCompletionResponseDto> {
                    return Response.error(
                        401,
                        """{"error":"unauthorized"}""".toResponseBody("application/json".toMediaType())
                    )
                }
            },
            secretProvider = secretProvider
        )

        val failure = gateway.generateReply(request).exceptionOrNull()

        assertTrue(failure is LlmFailure.Unauthorized)
    }

    @Test
    fun `maps socket timeout to typed failure`() = runTest {
        val gateway = ZaiLlmGateway(
            apiService = object : ZaiApiService {
                override suspend fun createChatCompletion(
                    authorization: String,
                    body: com.coreline.cbot.data.zai.dto.ChatCompletionRequestDto
                ): Response<ChatCompletionResponseDto> {
                    throw SocketTimeoutException("timeout")
                }
            },
            secretProvider = secretProvider
        )

        val failure = gateway.generateReply(request).exceptionOrNull()

        assertTrue(failure is LlmFailure.Timeout)
    }
}
