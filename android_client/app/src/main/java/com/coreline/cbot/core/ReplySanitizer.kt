package com.coreline.cbot.core

class ReplySanitizer {
    fun sanitize(rawReply: String?): String? {
        if (rawReply.isNullOrBlank()) {
            return null
        }

        val cleaned = rawReply
            .replace("```", " ")
            .replace(Regex("[*_`>#-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isBlank()) {
            return null
        }

        val limitedSentences = cleaned
            .split(Regex("(?<=[.!?。！？])\\s+"))
            .filter { it.isNotBlank() }
            .take(6)
            .joinToString(" ")
            .trim()

        val capped = limitedSentences.take(480).trim()
        return capped.ifBlank { null }
    }
}
