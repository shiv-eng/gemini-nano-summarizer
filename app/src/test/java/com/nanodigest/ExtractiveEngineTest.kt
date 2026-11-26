package com.nanodigest

import com.nanodigest.core.engine.ExtractiveEngine
import com.nanodigest.core.text.ChatFormatter
import com.nanodigest.ui.SampleTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractiveEngineTest {

    private val engine = ExtractiveEngine()

    @Test
    fun returnsRequestedCountInOriginalOrder() {
        val picks = engine.rank(SampleTexts.article, 3, conversation = false)
        assertEquals(3, picks.size)
        val positions = picks.map { SampleTexts.article.indexOf(it.removeSuffix("…").take(40)) }
        assertTrue(positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun chatSummaryFavoursTheDecision() {
        val chat = ChatFormatter.parse(SampleTexts.teamChat).formatted
        val picks = engine.keyPoints(chat, 3, conversation = true)
        assertEquals(3, picks.size)
        assertTrue("expected the decision line in $picks", picks.any { it.startsWith("Decision:") })
    }

    @Test
    fun shortInputReturnsEverything() {
        assertEquals(listOf("Only one sentence here."), engine.rank("Only one sentence here.", 3, conversation = false))
    }

    @Test
    fun emptyInputReturnsNothing() {
        assertTrue(engine.rank("   ", 3, conversation = false).isEmpty())
    }
}
