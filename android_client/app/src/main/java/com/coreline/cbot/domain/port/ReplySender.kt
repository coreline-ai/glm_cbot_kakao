package com.coreline.cbot.domain.port

import android.content.Context
import android.service.notification.StatusBarNotification

interface ReplySender {
    fun sendReply(context: Context, sbn: StatusBarNotification, message: String): Result<Unit>
}
