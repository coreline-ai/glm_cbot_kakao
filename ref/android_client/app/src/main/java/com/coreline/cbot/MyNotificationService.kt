package com.coreline.cbot

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.coreline.cbot.data.repository.ChatRepositoryImpl
import com.coreline.cbot.domain.usecase.ProcessChatUseCase
import com.coreline.cbot.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 카카오톡 알림을 가로채고 자동 답장을 보내는 알림 리스너 서비스
 */
class MyNotificationService : NotificationListenerService() {
    private val TAG = "BotService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 유스케이스 수동 주입
    private val processChatUseCase by lazy {
        val repository = ChatRepositoryImpl(RetrofitClient.apiService)
        ProcessChatUseCase(repository)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.kakao.talk") return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        // "코비서"로 시작하는 메시지만 처리
        if (!text.trim().startsWith("코비서")) return

        // 한 번의 호출로 묶어서 전송 (UI 갱신 누락 방지)
        val batchedLog = """
            ------------------------------
            📩 [MESSAGE] 수신됨
            👤 Sender: $title
            💬 Content: $text
        """.trimIndent()
        
        sendLog(batchedLog)
        
        serviceScope.launch {
            sendLog("🚀 [SYSTEM] 서버 분석 요청 중...")
            processChatUseCase(roomId = title, message = text).fold(
                onSuccess = { result ->
                    val replyText = result.summary
                    if (!replyText.isNullOrBlank()) {
                        sendLog("✅ 답변 수신: $replyText")
                        replyToNotification(sbn, replyText)
                    } else {
                        sendLog("⚠️ 답변 없음 (Null/Empty)")
                    }
                },
                onFailure = { error ->
                    sendLog("❌ 서버 에러: ${error.message}")
                    Log.e(TAG, "Process Chat Failed", error)
                }
            )
        }
    }

    private fun sendLog(message: String) {
        val intent = Intent("com.coreline.cbot.LOG")
        intent.putExtra("log", message)
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun replyToNotification(sbn: StatusBarNotification, message: String) {
        val actions = sbn.notification.actions ?: return
        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                val inputKey = remoteInputs[0].resultKey
                val intent = Intent()
                val bundle = Bundle()
                bundle.putCharSequence(inputKey, message)
                RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

                try {
                    action.actionIntent.send(this, 0, intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Reply Failed", e)
                }
                return
            }
        }
    }
}
