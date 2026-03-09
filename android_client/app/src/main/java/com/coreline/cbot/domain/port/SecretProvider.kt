package com.coreline.cbot.domain.port

interface SecretProvider {
    fun getGlmApiKey(): Result<String>
}
