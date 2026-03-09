package com.coreline.cbot.core

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import java.io.File

data class SecurityVerdict(
    val allowed: Boolean,
    val reason: String? = null
)

class SecurityGuard(
    private val application: Application
) {
    fun evaluate(): SecurityVerdict {
        if (com.coreline.cbot.BuildConfig.BLOCK_AUTOREPLY) {
            return SecurityVerdict(false, "디버그 빌드 또는 비보호 빌드")
        }

        if (isAppDebuggable()) {
            return SecurityVerdict(false, "앱이 디버그 가능 상태")
        }

        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return SecurityVerdict(false, "디버거 감지")
        }

        if (hasHookFramework()) {
            return SecurityVerdict(false, "후킹 프레임워크 감지")
        }

        if (isRooted()) {
            return SecurityVerdict(false, "루팅 흔적 감지")
        }

        return SecurityVerdict(true)
    }

    private fun isAppDebuggable(): Boolean {
        return (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun hasHookFramework(): Boolean {
        val suspiciousClasses = listOf(
            "de.robv.android.xposed.XposedBridge",
            "com.saurik.substrate.MS$2",
            "re.frida.ServerManager"
        )

        if (suspiciousClasses.any { className -> runCatching { Class.forName(className) }.isSuccess }) {
            return true
        }

        return runCatching {
            File("/proc/self/maps")
                .takeIf { it.exists() }
                ?.readText()
                ?.lowercase()
                ?.contains("frida") == true
        }.getOrDefault(false)
    }

    private fun isRooted(): Boolean {
        val buildTags = Build.TAGS.orEmpty()
        if (buildTags.contains("test-keys")) {
            return true
        }

        val knownPaths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )

        return knownPaths.any { File(it).exists() }
    }
}
