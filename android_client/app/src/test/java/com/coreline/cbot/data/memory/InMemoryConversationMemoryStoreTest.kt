package com.coreline.cbot.data.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryConversationMemoryStoreTest {
    @Test
    fun `keeps only recent turns within limit`() {
        val store = InMemoryConversationMemoryStore(maxTurnsPerRoom = 2, expiryWindowMs = 1_000L)

        store.appendExchange("room", "u1", "a1", now = 100L)
        store.appendExchange("room", "u2", "a2", now = 200L)
        store.appendExchange("room", "u3", "a3", now = 300L)

        val turns = store.getRecentTurns("room", now = 300L)

        assertEquals(2, turns.size)
        assertEquals("u2", turns[0].userMessage)
        assertEquals("u3", turns[1].userMessage)
    }

    @Test
    fun `expires stale room history`() {
        val store = InMemoryConversationMemoryStore(maxTurnsPerRoom = 4, expiryWindowMs = 100L)

        store.appendExchange("room", "u1", "a1", now = 100L)

        val turns = store.getRecentTurns("room", now = 250L)

        assertTrue(turns.isEmpty())
    }
}
