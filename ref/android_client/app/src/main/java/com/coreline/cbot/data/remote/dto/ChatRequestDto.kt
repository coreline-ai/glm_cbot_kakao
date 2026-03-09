package com.coreline.cbot.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 서버 요청을 위한 DTO (Data Transfer Object)
 */
data class ChatRequestDto(
    @SerializedName("roomId") val roomId: String,
    @SerializedName("message") val message: String
)
