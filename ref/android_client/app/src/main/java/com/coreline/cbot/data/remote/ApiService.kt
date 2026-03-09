package com.coreline.cbot.data.remote

import com.coreline.cbot.data.remote.dto.ChatRequestDto
import com.coreline.cbot.data.remote.dto.ChatResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 서버와 통신하는 API 인터페이스
 */
interface ApiService {
    @POST("chat/process")
    suspend fun processChat(@Body request: ChatRequestDto): ChatResponseDto

    @GET("/")
    suspend fun checkHealth(): Map<String, Any>
}
