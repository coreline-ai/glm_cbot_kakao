package com.coreline.cbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplySanitizerTest {
    private val sanitizer = ReplySanitizer()

    @Test
    fun `removes markdown fences and keeps concise response`() {
        val sanitized = sanitizer.sanitize("```안녕하세요``` 첫째 문장입니다. 둘째 문장입니다. 셋째 문장입니다. 넷째 문장입니다. 다섯째 문장입니다. 여섯째 문장입니다. 일곱째 문장입니다.")

        assertEquals("안녕하세요 첫째 문장입니다. 둘째 문장입니다. 셋째 문장입니다. 넷째 문장입니다. 다섯째 문장입니다. 여섯째 문장입니다.", sanitized)
    }

    @Test
    fun `returns null for blank replies`() {
        assertNull(sanitizer.sanitize("   "))
    }
}
