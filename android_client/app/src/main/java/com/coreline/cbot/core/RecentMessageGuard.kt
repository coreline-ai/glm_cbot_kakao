package com.coreline.cbot.core

class RecentMessageGuard(
    private val windowMs: Long = 8_000L,
    private val maxEntries: Int = 128
) {
    private val seenMessages = LinkedHashMap<String, Long>()

    @Synchronized
    fun shouldProcess(signature: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        prune(nowMs)

        val previousTimestamp = seenMessages[signature]
        if (previousTimestamp != null && nowMs - previousTimestamp < windowMs) {
            return false
        }

        seenMessages[signature] = nowMs
        if (seenMessages.size > maxEntries) {
            val oldestKey = seenMessages.entries.firstOrNull()?.key
            if (oldestKey != null) {
                seenMessages.remove(oldestKey)
            }
        }

        return true
    }

    @Synchronized
    fun clear() {
        seenMessages.clear()
    }

    private fun prune(nowMs: Long) {
        val iterator = seenMessages.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMs - entry.value >= windowMs) {
                iterator.remove()
            }
        }
    }
}
