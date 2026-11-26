package com.nanodigest

import com.nanodigest.core.text.TextChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    @Test
    fun shortTextIsOneChunk() {
        assertEquals(listOf("hello world"), TextChunker.split("  hello world \n", 100))
    }

    @Test
    fun blankTextHasNoChunks() {
        assertTrue(TextChunker.split("   \n\n ", 100).isEmpty())
    }

    @Test
    fun splitsOnParagraphsFirst() {
        val p1 = "a".repeat(60)
        val p2 = "b".repeat(60)
        val chunks = TextChunker.split("$p1\n\n$p2", 100)
        assertEquals(listOf(p1, p2), chunks)
    }

    @Test
    fun packsSmallParagraphsTogether() {
        val text = (1..10).joinToString("\n\n") { "para $it" }
        val chunks = TextChunker.split(text, 30)
        assertTrue(chunks.all { it.length <= 30 })
        assertEquals(text.replace("\n\n", ""), chunks.joinToString("").replace("\n\n", ""))
    }

    @Test
    fun fallsBackToLinesThenSentencesThenWords() {
        val longParagraph = (1..50).joinToString(" ") { "Sentence number $it is here." }
        val chunks = TextChunker.split(longParagraph, 120)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 120 })
        // Sentence boundaries are preserved.
        assertTrue(chunks.all { it.endsWith(".") })
    }

    @Test
    fun hardCutsUnbreakableText() {
        val chunks = TextChunker.split("x".repeat(250), 100)
        assertEquals(listOf(100, 100, 50), chunks.map { it.length })
    }

    @Test
    fun chatLinesStayIntact() {
        val chat = (1..200).joinToString("\n") { "Person$it: message number $it" }
        val chunks = TextChunker.split(chat, 1_000)
        assertTrue(chunks.size > 1)
        chunks.flatMap { it.lines() }.forEach { assertTrue(it.matches(Regex("""Person\d+: message number \d+"""))) }
    }
}
