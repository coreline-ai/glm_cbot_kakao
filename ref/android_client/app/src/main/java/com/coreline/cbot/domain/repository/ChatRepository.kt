package com.coreline.cbot.domain.repository

import com.coreline.cbot.domain.model.ChatResult

/**
 * 채팅 서비스 관련 기능을 정의한 Repository 인터페이스
 */
interface ChatRepository {
    /**
     * 서버에 메시지를 전송하고 결과를 가져옵니다.
     */
    suspend fun processChat(roomId: String, message: String): Result<ChatResult>

    /**
     * 서버의 생존 상태(Health Check)를 확인합니다.
     */
    suspend fun checkHealth(): Result<Boolean>
}
