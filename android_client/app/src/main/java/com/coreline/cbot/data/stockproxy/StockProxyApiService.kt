package com.coreline.cbot.data.stockproxy

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface StockProxyApiService {
    @POST("api/v1/summary?provider=naver_domestic")
    suspend fun summary(
        @Body body: StockProxySummaryRequestDto
    ): Response<StockProxySummaryResponseDto>
}

data class StockProxySummaryRequestDto(
    val symbol: String,
    val question: String,
    val includeNews: Boolean = true,
    val candlePoints: Int = 10
)

data class StockProxySummaryResponseDto(
    val ok: Boolean,
    val symbol: String,
    val model: String,
    val text: String?,
    val responseId: String?,
    val toolCalls: List<String>?
)
