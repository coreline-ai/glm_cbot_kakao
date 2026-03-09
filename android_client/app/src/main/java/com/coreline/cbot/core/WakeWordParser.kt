package com.coreline.cbot.core

class WakeWordParser(
    private val wakeWord: String = "코비서"
) {
    fun extractQuery(rawMessage: String): String? {
        val trimmed = rawMessage.trim()
        val candidates = linkedSetOf(trimmed)

        listOf(":", "：", "]").forEach { delimiter ->
            val index = trimmed.indexOf(delimiter)
            if (index in 1..40 && index + 1 < trimmed.length) {
                candidates.add(trimmed.substring(index + 1).trim())
            }
        }

        return candidates.firstNotNullOfOrNull { candidate ->
            if (!candidate.startsWith(wakeWord)) {
                return@firstNotNullOfOrNull null
            }

            candidate
                .removePrefix(wakeWord)
                .trimStart(' ', ',', '.', '!', '?', ':')
                .trim()
        }
    }
}
