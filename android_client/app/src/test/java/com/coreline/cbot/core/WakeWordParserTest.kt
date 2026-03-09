package com.coreline.cbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordParserTest {
    private val parser = WakeWordParser()

    @Test
    fun `extracts query when wake word is present`() {
        assertEquals("오늘 날씨 어때", parser.extractQuery("코비서 오늘 날씨 어때"))
    }

    @Test
    fun `returns null when wake word is missing`() {
        assertNull(parser.extractQuery("안녕 오늘 날씨 어때"))
    }

    @Test
    fun `returns empty string when only wake word is present`() {
        assertEquals("", parser.extractQuery("코비서"))
    }
}
