package com.coreline.cbot.data.security

class NativeSecrets {
    private val isLoaded: Boolean = runCatching {
        System.loadLibrary("cbot-secrets")
    }.isSuccess

    external fun nativeGetEmbeddedGlmKey(): String
    external fun nativeGetEmbeddedOpenAiKey(): String

    fun getEmbeddedGlmKey(): Result<String> {
        if (!isLoaded) {
            return Result.failure(IllegalStateException("네이티브 시크릿 로더 초기화 실패"))
        }

        return runCatching { nativeGetEmbeddedGlmKey() }
    }

    fun getEmbeddedOpenAiKey(): Result<String> {
        if (!isLoaded) {
            return Result.failure(IllegalStateException("네이티브 시크릿 로더 초기화 실패"))
        }

        return runCatching { nativeGetEmbeddedOpenAiKey() }
    }
}
