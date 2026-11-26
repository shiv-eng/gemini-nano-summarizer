package com.nanodigest

import com.nanodigest.core.engine.ChatDigest
import com.nanodigest.core.engine.ExtractiveEngine
import com.nanodigest.core.text.ChatFormatter
import com.nanodigest.ui.SampleTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDigestTest {

    private val engine = ExtractiveEngine()
    private val chat = ChatFormatter.parse(SampleTexts.teamChat).formatted

    @Test
    fun sampleChatGivesWhatHappenedDecisionAndNextSteps() {
        val points = engine.keyPoints(chat, 3, conversation = true)
        println(points.joinToString("\n"))
        assertEquals(3, points.size)
        assertTrue(points[0], points[0].startsWith("What happened: Dev just saw a new crash"))
        assertTrue(points[1], points[1].startsWith("Decision: RC 4.3 with the camera fix"))
        assertTrue(points[2], points[2].startsWith("Next steps:"))
        assertTrue(points[2], points[2].contains("Aisha will update the release notes"))
        // No raw chat syntax or links leak through.
        points.forEach { assertFalse(it, it.contains("[link]")) }
    }

    @Test
    fun onePointIsTheDecision() {
        val points = engine.keyPoints(chat, 1, conversation = true)
        assertEquals(1, points.size)
        assertTrue(points[0], points[0].startsWith("Decision:"))
    }

    @Test
    fun rewritesFirstPersonToThirdPerson() {
        val text = ChatDigest.rewrite(ChatDigest.Message(0, "Marco", "Ok, I'll let the partner know we are on track."))
        assertEquals("Marco will let the partner know we are on track.", text)
    }

    @Test
    fun attributesMessagesWithoutAName() {
        val text = ChatDigest.rewrite(ChatDigest.Message(0, "Priya", "Decision: ship on Thursday"))
        assertEquals("Ship on Thursday (Priya).", text)
    }
}
