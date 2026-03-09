package com.coreline.cbot.domain.port

import com.coreline.cbot.domain.model.LlmRequest
import com.coreline.cbot.domain.model.LlmResponse

interface LlmGateway {
    suspend fun generateReply(request: LlmRequest): Result<LlmResponse>
}
