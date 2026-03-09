package com.coreline.cbot.data.openai

import com.coreline.cbot.data.zai.dto.ChatCompletionRequestDto
import com.coreline.cbot.data.zai.dto.ChatCompletionResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAiApiService {
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body body: ChatCompletionRequestDto
    ): Response<ChatCompletionResponseDto>
}
