package com.coreline.cbot

import android.app.Notification
import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.coreline.cbot.domain.model.IncomingChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MyNotificationService : NotificationListenerService() {
    companion object {
        private const val TAG = "CBotNotification"
        private const val KAKAO_PACKAGE = "com.kakao.talk"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != KAKAO_PACKAGE) {
            return
        }

        val extras = sbn.notification.extras
        val roomName = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getString(Notification.EXTRA_TITLE)
            ?: run {
                Log.w(TAG, "Skip notification: missing room/title")
                return
            }
        val text = extractMessageText(sbn.notification) ?: run {
            Log.w(TAG, "Skip notification: missing text room=$roomName")
            return
        }
        val sender = extras.getString(Notification.EXTRA_SUB_TEXT)

        val app = application as? CBotApplication ?: return
        val message = IncomingChatMessage(
            roomName = roomName,
            sender = sender,
            rawText = text,
            receivedAt = System.currentTimeMillis()
        )

        Log.i(TAG, "Posted room=$roomName sender=${sender ?: "-"} text=$text")

        serviceScope.launch {
            app.container.handleIncomingNotificationUseCase(this@MyNotificationService, sbn, message)
        }
    }

    private fun extractMessageText(notification: Notification): String? {
        val extras = notification.extras
        extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val bundledMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundledMessages)
        return messages.lastOrNull()
            ?.text
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
