package com.nanodigest.core.text

/**
 * Cheap, offline text measurements used to warn the user before running inference.
 *
 * Gemini Nano's summarization feature accepts roughly 4,000 tokens. English averages
 * about 4 characters per token, so character counts are a good-enough proxy.
 */
data class TextStats(
    val chars: Int,
    val words: Int,
    val lines: Int,
    val estimatedTokens: Int,
) {
    companion object {
        const val CHARS_PER_TOKEN = 4

        val EMPTY = TextStats(0, 0, 0, 0)

        fun of(text: String): TextStats {
            if (text.isBlank()) return EMPTY
            val trimmed = text.trim()
            return TextStats(
                chars = trimmed.length,
                words = WORD.findAll(trimmed).count(),
                lines = trimmed.lineSequence().count { it.isNotBlank() },
                estimatedTokens = (trimmed.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN,
            )
        }

        private val WORD = Regex("""\S+""")
    }
}
