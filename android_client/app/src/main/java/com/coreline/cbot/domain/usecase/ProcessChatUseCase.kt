package com.coreline.cbot.domain.usecase

import com.coreline.cbot.domain.model.ChatResult
import com.coreline.cbot.domain.repository.ChatRepository

/**
 * 채팅 메시지를 처리하는 유스케이스
 */
class ProcessChatUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(roomId: String, message: String): Result<ChatResult> {
        return repository.processChat(roomId, message)
    }
}
