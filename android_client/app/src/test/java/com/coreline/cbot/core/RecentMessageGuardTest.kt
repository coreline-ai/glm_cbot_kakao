package com.coreline.cbot.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentMessageGuardTest {
    @Test
    fun `blocks duplicate messages within window`() {
        val guard = RecentMessageGuard(windowMs = 5_000L)

        assertTrue(guard.shouldProcess("room::query", nowMs = 1_000L))
        assertFalse(guard.shouldProcess("room::query", nowMs = 2_000L))
    }

    @Test
    fun `allows same message after window expires`() {
        val guard = RecentMessageGuard(windowMs = 5_000L)

        assertTrue(guard.shouldProcess("room::query", nowMs = 1_000L))
        assertTrue(guard.shouldProcess("room::query", nowMs = 6_500L))
    }
}
