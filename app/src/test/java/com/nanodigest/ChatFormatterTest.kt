package com.nanodigest

import com.nanodigest.core.text.ChatFormatter
import com.nanodigest.ui.SampleTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFormatterTest {

    @Test
    fun parsesWhatsAppAndroidExport() {
        val text = """
            11/25/26, 9:02 AM - Messages and calls are end-to-end encrypted.
            11/25/26, 9:04 AM - Priya: Morning all
            11/25/26, 9:05 AM - Marco: <Media omitted>
            11/25/26, 9:06 AM - Marco: see https://example.com/x
            and this continues
            11/25/26, 9:07 AM - Dev: This message was deleted
        """.trimIndent()
        val r = ChatFormatter.parse(text)
        assertEquals(
            "Priya: Morning all\nMarco: [shared media]\nMarco: see [link] and this continues",
            r.formatted,
        )
        assertEquals(1, r.droppedSystemLines)
        assertEquals(1, r.mediaCount)
        assertEquals(1, r.linksReplaced)
        assertEquals(listOf("Priya", "Marco"), r.participants)
    }

    @Test
    fun parsesWhatsAppIosExportWithInvisibleCharacters() {
        val text = "[25/11/2026, 21:41:05] Aisha: Ship Thursday?\n" +
            "[25/11/2026, 21:42:10] Marco: ‎image omitted\n" +
            "[25/11/2026, 9:43:00 PM] Dev: Yes"
        val r = ChatFormatter.parse(text)
        assertEquals("Aisha: Ship Thursday?\nMarco: [shared a photo]\nDev: Yes", r.formatted)
    }

    @Test
    fun parsesTelegramCopy() {
        val text = "Alice, [25.11.26 10:00]\nHello team\nBob, [25.11.26 10:01]\nHi Alice"
        assertEquals("Alice: Hello team\nBob: Hi Alice", ChatFormatter.parse(text).formatted)
    }

    @Test
    fun keepsPlainNameMessageLines() {
        val text = "Alice: one\nBob: two\nAlice: three"
        assertTrue(ChatFormatter.looksLikeChat(text))
        assertEquals(text, ChatFormatter.parse(text).formatted)
    }

    @Test
    fun lastNKeepsMostRecentMessages() {
        val text = (1..10).joinToString("\n") { "P$it: m$it" }
        val r = ChatFormatter.parse(text, lastN = 3)
        assertEquals(10, r.messages.size)
        assertEquals("P8: m8\nP9: m9\nP10: m10", r.formatted)
    }

    @Test
    fun linksKeptWhenDisabled() {
        val r = ChatFormatter.parse("A: https://x.y/z\nB: ok\nA: fine", replaceLinks = false)
        assertTrue(r.formatted.contains("https://x.y/z"))
    }

    @Test
    fun articlesAreNotChats() {
        assertFalse(ChatFormatter.looksLikeChat(SampleTexts.article))
        assertTrue(ChatFormatter.looksLikeChat(SampleTexts.teamChat))
    }

    @Test
    fun sampleChatCleansUp() {
        val r = ChatFormatter.parse(SampleTexts.teamChat)
        assertEquals(listOf("Priya", "Marco", "Dev", "Aisha"), r.participants)
        assertTrue(r.formatted.lines().none { it.contains("AM -") })
        assertTrue(r.formatted.length < SampleTexts.teamChat.length)
    }
}
