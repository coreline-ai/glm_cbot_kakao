package com.coreline.cbot.core

import com.coreline.cbot.domain.model.ConversationTurn
import com.coreline.cbot.domain.model.PromptSpec

class PromptBuilder {
    fun build(userPrompt: String, history: List<ConversationTurn> = emptyList()): PromptSpec {
        val contextRule = if (history.isEmpty()) {
            "이전 대화 문맥이 없으면 현재 질문만 기준으로 답한다."
        } else {
            "아래에 제공되는 최근 대화 문맥이 현재 질문과 관련 있으면 이어서 답하고, 관련 없으면 현재 질문을 우선한다."
        }

        return PromptSpec(
            systemPrompt = """
                너는 '코비서'라는 이름의 한국어 AI 비서다.
                답변은 자연스럽고 간결하게 한다.
                필요하면 최대 6문장 이내로 답한다.
                불필요한 서론, 마크다운, 코드블록, 이모지 사용을 피한다.
                질문이 모호하면 짧게 되묻는다.
                $contextRule
            """.trimIndent(),
            userPrompt = userPrompt.trim(),
            maxTokens = 256,
            temperature = 0.2
        )
    }
}
