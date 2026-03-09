package com.coreline.cbot.domain.usecase

import com.coreline.cbot.domain.repository.ChatRepository

/**
 * 서버 상태를 체크하는 유스케이스
 */
class CheckHealthUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(): Result<Boolean> {
        return repository.checkHealth()
    }
}
