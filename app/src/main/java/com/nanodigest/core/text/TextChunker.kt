package com.nanodigest.core.text

/**
 * Splits long text into chunks that each fit the model's input window, preferring the
 * most natural boundary available: paragraphs, then lines (chat messages), then sentences,
 * then words, and only as a last resort a hard character cut.
 *
 * This is the "chunk" half of chunk-and-combine (map-reduce) summarization.
 */
object TextChunker {

    private data class Level(val splitter: Regex, val joiner: String)

    private val levels = listOf(
        Level(Regex("""\n\s*\n"""), "\n\n"), // paragraphs
        Level(Regex("""\n"""), "\n"), // lines / chat messages
        Level(Regex("""(?<=[.!?。！？])\s+"""), " "), // sentences
        Level(Regex("""\s+"""), " "), // words
    )

    fun split(text: String, maxChars: Int): List<String> {
        require(maxChars > 0) { "maxChars must be positive" }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= maxChars) return listOf(trimmed)
        return pack(trimmed, maxChars, 0)
    }

    /** Number of chunks [split] would produce, without allocating them all twice in UI code. */
    fun chunkCount(text: String, maxChars: Int): Int = split(text, maxChars).size

    private fun pack(text: String, maxChars: Int, levelIndex: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        if (levelIndex >= levels.size) return text.chunked(maxChars)

        val level = levels[levelIndex]
        val parts = text.split(level.splitter).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size <= 1) return pack(text, maxChars, levelIndex + 1)

        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (part in parts) {
            val pieces = if (part.length > maxChars) pack(part, maxChars, levelIndex + 1) else listOf(part)
            for (piece in pieces) {
                if (current.isNotEmpty() && current.length + level.joiner.length + piece.length > maxChars) {
                    out += current.toString()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(level.joiner)
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}
