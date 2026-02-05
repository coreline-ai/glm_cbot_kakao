package com.coreline.cbot.data.repository

import com.coreline.cbot.data.remote.ApiService
import com.coreline.cbot.data.remote.dto.ChatRequestDto
import com.coreline.cbot.domain.model.ChatResult
import com.coreline.cbot.domain.repository.ChatRepository

/**
 * ChatRepository의 실제 구현체
 */
class ChatRepositoryImpl(private val apiService: ApiService) : ChatRepository {

    override suspend fun processChat(roomId: String, message: String): Result<ChatResult> {
        return try {
            val response = apiService.processChat(ChatRequestDto(roomId, message))
            Result.success(ChatResult(summary = response.summary))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkHealth(): Result<Boolean> {
        return try {
            val response = apiService.checkHealth()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
