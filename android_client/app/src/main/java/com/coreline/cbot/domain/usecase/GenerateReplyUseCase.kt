package com.coreline.cbot.domain.usecase

import com.coreline.cbot.core.ReplySanitizer
import com.coreline.cbot.domain.model.ConversationTurn
import com.coreline.cbot.domain.model.LlmResponse
import com.coreline.cbot.domain.port.LlmGateway

class GenerateReplyUseCase(
    private val buildPromptUseCase: BuildPromptUseCase,
    private val llmGateway: LlmGateway,
    private val replySanitizer: ReplySanitizer
) {
    suspend operator fun invoke(
        query: String,
        history: List<ConversationTurn> = emptyList()
    ): Result<LlmResponse> {
        val request = buildPromptUseCase(query, history)
        return llmGateway.generateReply(request).map { response ->
            response.copy(text = replySanitizer.sanitize(response.text))
        }
    }
}
