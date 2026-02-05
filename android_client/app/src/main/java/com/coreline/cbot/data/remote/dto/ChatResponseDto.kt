package com.coreline.cbot.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 서버 응답을 위한 DTO (Data Transfer Object)
 */
data class ChatResponseDto(
    @SerializedName("summary") val summary: String?
)
