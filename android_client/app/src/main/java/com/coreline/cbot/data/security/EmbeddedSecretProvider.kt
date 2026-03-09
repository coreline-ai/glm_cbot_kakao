package com.coreline.cbot.data.security

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.coreline.cbot.BuildConfig
import com.coreline.cbot.domain.port.SecretProvider
import java.security.MessageDigest

class EmbeddedSecretProvider(
    private val application: Application,
    private val nativeSecrets: NativeSecrets
) : SecretProvider {

    override fun getGlmApiKey(): Result<String> {
        if (BuildConfig.DEBUG) {
            return Result.failure(IllegalStateException("디버그 빌드에서는 내장 키를 사용하지 않습니다"))
        }

        if (!BuildConfig.GLM_EMBEDDED_SECRET_AVAILABLE) {
            return Result.failure(IllegalStateException("릴리스 키가 주입되지 않았습니다"))
        }

        return validatedEmbeddedKey(nativeSecrets::getEmbeddedGlmKey)
    }

    fun getOpenAiApiKey(): Result<String> {
        if (BuildConfig.DEBUG) {
            return Result.failure(IllegalStateException("디버그 빌드에서는 내장 키를 사용하지 않습니다"))
        }

        if (!BuildConfig.OPENAI_EMBEDDED_SECRET_AVAILABLE) {
            return Result.failure(IllegalStateException("OpenAI 내장 키가 주입되지 않았습니다"))
        }

        return validatedEmbeddedKey(nativeSecrets::getEmbeddedOpenAiKey)
    }

    private fun validatedEmbeddedKey(loader: () -> Result<String>): Result<String> {

        if (application.packageName != BuildConfig.APPLICATION_ID) {
            return Result.failure(SecurityException("패키지 검증 실패"))
        }

        val expectedCert = BuildConfig.ALLOWED_CERT_SHA256
        if (expectedCert.isBlank()) {
            return Result.failure(SecurityException("허용된 인증서 해시가 설정되지 않았습니다"))
        }

        val actualCert = runCatching { signingCertSha256() }
            .getOrElse { return Result.failure(it) }

        if (actualCert != expectedCert) {
            return Result.failure(SecurityException("서명 인증서 검증 실패"))
        }

        return loader().mapCatching { secret ->
            secret.takeIf { it.isNotBlank() } ?: error("내장 키가 비어 있습니다")
        }
    }

    private fun signingCertSha256(): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            application.packageManager.getPackageInfo(
                application.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            application.packageManager.getPackageInfo(
                application.packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo.apkContentsSigners.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures.firstOrNull()?.toByteArray()
        } ?: error("앱 서명 정보를 찾을 수 없습니다")

        val digest = MessageDigest.getInstance("SHA-256").digest(signatureBytes)
        return digest.joinToString(separator = "") { "%02X".format(it) }
    }
}
