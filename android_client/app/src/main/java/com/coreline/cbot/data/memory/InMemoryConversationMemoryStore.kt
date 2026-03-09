package com.coreline.cbot.data.memory

import com.coreline.cbot.domain.model.ConversationTurn
import java.util.ArrayDeque

class InMemoryConversationMemoryStore(
    private val maxTurnsPerRoom: Int = 4,
    private val expiryWindowMs: Long = 30 * 60 * 1000L
) {
    private val conversations = mutableMapOf<String, ArrayDeque<ConversationTurn>>()

    @Synchronized
    fun getRecentTurns(roomName: String, now: Long = System.currentTimeMillis()): List<ConversationTurn> {
        pruneExpired(now)
        return conversations[roomName]?.toList().orEmpty()
    }

    @Synchronized
    fun appendExchange(
        roomName: String,
        userMessage: String,
        assistantMessage: String,
        now: Long = System.currentTimeMillis()
    ) {
        if (userMessage.isBlank() || assistantMessage.isBlank()) {
            return
        }

        pruneExpired(now)

        val turns = conversations.getOrPut(roomName) { ArrayDeque() }
        turns.addLast(
            ConversationTurn(
                userMessage = userMessage.trim(),
                assistantMessage = assistantMessage.trim(),
                updatedAt = now
            )
        )

        while (turns.size > maxTurnsPerRoom) {
            turns.removeFirst()
        }
    }

    @Synchronized
    fun clearRoom(roomName: String) {
        conversations.remove(roomName)
    }

    private fun pruneExpired(now: Long) {
        val iterator = conversations.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val filtered = entry.value.filter { now - it.updatedAt <= expiryWindowMs }
            if (filtered.isEmpty()) {
                iterator.remove()
                continue
            }

            if (filtered.size != entry.value.size) {
                entry.setValue(ArrayDeque(filtered))
            }
        }
    }
}
