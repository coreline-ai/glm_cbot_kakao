package com.coreline.cbot.data.reply

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.coreline.cbot.domain.port.ReplySender

class KakaoReplySender : ReplySender {
    override fun sendReply(context: android.content.Context, sbn: StatusBarNotification, message: String): Result<Unit> {
        return runCatching {
            val actions = sbn.notification.actions ?: error("알림 reply action 없음")
            val replyAction = actions.firstOrNull { action ->
                val remoteInputs = action.remoteInputs
                remoteInputs != null && remoteInputs.isNotEmpty()
            } ?: error("reply 가능한 remote input 없음")

            val remoteInputs = replyAction.remoteInputs
            val resultKey = remoteInputs[0].resultKey
            val fillInIntent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(resultKey, message)
            RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, bundle)

            replyAction.actionIntent.send(context, 0, fillInIntent)
        }
    }
}
