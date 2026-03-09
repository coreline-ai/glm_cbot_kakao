package com.coreline.cbot.core

class StockQueryClassifier {
    private val marketKeywords = listOf(
        "코스피",
        "코스닥",
        "한국주식시장",
        "국내주식시장",
        "한국증시",
        "국내증시",
        "주식시장",
        "증시",
        "시황",
        "시장 정리",
        "시장요약",
        "시장 요약",
        "장마감",
        "장 중",
        "장중"
    )

    private val financeKeywords = listOf(
        "주가",
        "종목",
        "증권",
        "차트",
        "시세",
        "목표가",
        "실적",
        "수급",
        "매수",
        "매도",
        "뉴스",
        "흐름",
        "캔들",
        "거래량",
        "배당",
        "전망",
        "동향",
        "통향",
        "오를까",
        "내릴까",
        "살까",
        "팔까",
        "per",
        "pbr",
        "eps"
    )

    private val stockIntentKeywords = listOf(
        "정리",
        "요약",
        "분석",
        "동향",
        "통향",
        "흐름",
        "뉴스",
        "전망",
        "어때",
        "어떄"
    )

    private val shortPromptWhitelist = listOf(
        "삼성전자 어때",
        "삼성전자 어때?",
        "sk하이닉스 어때",
        "sk하이닉스 어때?",
        "하이닉스 어때",
        "하이닉스 어때?",
        "하닉 어때",
        "하닉 어때?",
        "삼전 어때",
        "삼전 어때?",
        "카카오 어때",
        "카카오 어때?",
        "네이버 어때",
        "네이버 어때?"
    )

    private val stockAliases = listOf(
        "삼성전자",
        "삼전",
        "sk하이닉스",
        "하이닉스",
        "하닉",
        "네이버",
        "naver",
        "카카오",
        "카카오뱅크",
        "카뱅",
        "현대차",
        "lg화학",
        "lg전자",
        "포스코홀딩스",
        "포홀",
        "삼바"
    )

    fun shouldUseStockProxy(query: String): Boolean {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) {
            return false
        }

        if (DOMESTIC_SYMBOL_REGEX.containsMatchIn(normalized)) {
            return true
        }

        if (marketKeywords.any { normalized.contains(it.lowercase()) }) {
            return true
        }

        if (financeKeywords.any { normalized.contains(it.lowercase()) }) {
            return true
        }

        if (stockAliases.any { normalized.contains(it.lowercase()) } && stockIntentKeywords.any { normalized.contains(it.lowercase()) }) {
            return true
        }

        if (containsLikelyCompanyName(normalized) && stockIntentKeywords.any { normalized.contains(it.lowercase()) }) {
            return true
        }

        if (shortPromptWhitelist.any { normalized.contains(it.lowercase()) }) {
            return true
        }

        return false
    }

    private fun containsLikelyCompanyName(normalized: String): Boolean {
        val clean = normalized.replace(Regex("""[^0-9a-z가-힣\s]"""), " ")
        val tokens = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val filtered = tokens.filter { it !in genericConversationTokens && it !in fillerTokens }
        return filtered.any { token ->
            token.length >= 2 && Regex("""[가-힣a-z]""").containsMatchIn(token)
        }
    }

    private companion object {
        val DOMESTIC_SYMBOL_REGEX = Regex("""(?<!\d)\d{6}(?:\.[a-z]{2,4})?(?!\d)""")
        val genericConversationTokens = setOf(
            "코비서",
            "하이",
            "안녕",
            "동작",
            "동작중이야",
            "동작하고있어",
            "정상",
            "지금",
            "오늘",
            "금일",
            "해줘",
            "알려줘",
            "부탁해",
            "부탁"
        )
        val fillerTokens = setOf(
            "오늘",
            "금일",
            "최근",
            "정리",
            "요약",
            "분석",
            "뉴스",
            "브리핑",
            "동향",
            "통향",
            "흐름",
            "어때",
            "어떄",
            "해줘",
            "알려줘"
        )
    }
}
