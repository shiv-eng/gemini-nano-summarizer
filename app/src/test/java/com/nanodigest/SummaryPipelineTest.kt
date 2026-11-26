package com.nanodigest

import com.nanodigest.core.engine.Availability
import com.nanodigest.core.engine.EngineId
import com.nanodigest.core.engine.EngineMode
import com.nanodigest.core.engine.ExtractiveEngine
import com.nanodigest.core.engine.InputKind
import com.nanodigest.core.engine.SummaryConfig
import com.nanodigest.core.engine.SummaryEngine
import com.nanodigest.core.engine.SummaryEvent
import com.nanodigest.core.engine.SummaryFailedException
import com.nanodigest.core.engine.SummaryLanguage
import com.nanodigest.core.engine.SummaryPipeline
import com.nanodigest.ui.SampleTexts
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stand-in for Gemini Nano: records calls, streams its output word by word. */
private class FakeNano(
    override val maxInputChars: Int = 100,
    private val availability: Availability = Availability.Ready,
    private val failWith: Throwable? = null,
) : SummaryEngine {
    override val id = EngineId.GEMINI_NANO
    val calls = mutableListOf<Pair<String, SummaryConfig>>()

    override suspend fun availability(config: SummaryConfig) = availability

    override suspend fun summarize(text: String, config: SummaryConfig, onText: (String) -> Unit): String {
        failWith?.let { throw it }
        require(text.length <= maxInputChars) { "input too large: ${text.length}" }
        calls += text to config
        val out = "* summary ${calls.size}"
        out.split(" ").forEachIndexed { i, w -> onText(if (i == 0) w else " $w") }
        return out
    }
}

class SummaryPipelineTest {

    private val config = SummaryConfig(InputKind.CONVERSATION, 2, SummaryLanguage.ENGLISH)

    @Test
    fun singleChunkStreamsAndNormalizesBullets() = runTest {
        val nano = FakeNano()
        val events = SummaryPipeline(nano, ExtractiveEngine()).run("A: hi\nB: hello", config, EngineMode.AUTO).toList()
        val partials = events.filterIsInstance<SummaryEvent.Partial>().map { it.text }
        assertEquals(listOf("*", "* summary", "* summary 1"), partials)
        val done = events.last() as SummaryEvent.Completed
        assertEquals("• summary 1", done.summary)
        assertEquals(EngineId.GEMINI_NANO, done.engine)
        assertEquals(1, done.chunks)
        assertEquals(config, nano.calls.single().second)
    }

    @Test
    fun longInputUsesChunkAndCombine() = runTest {
        val nano = FakeNano(maxInputChars = 100)
        val text = (1..20).joinToString("\n") { "Person$it: message $it" } // ~400 chars
        val events = SummaryPipeline(nano, ExtractiveEngine()).run(text, config, EngineMode.GEMINI_NANO).toList()
        val done = events.last() as SummaryEvent.Completed
        assertTrue(done.chunks > 1)
        // Map calls use 3 bullets and the source kind; the final reduce uses the user's bullets as an ARTICLE.
        val map = nano.calls.dropLast(1)
        assertTrue(map.all { it.second.bullets == 3 && it.second.inputKind == InputKind.CONVERSATION })
        assertEquals(SummaryConfig(InputKind.ARTICLE, 2, SummaryLanguage.ENGLISH), nano.calls.last().second)
        val stages = events.filterIsInstance<SummaryEvent.Stage>().map { it.label }
        assertTrue(stages.first().startsWith("Reading part 1 of"))
        assertTrue(stages.last().startsWith("Combining"))
    }

    @Test
    fun autoModeFallsBackWhenNanoUnavailable() = runTest {
        val nano = FakeNano(availability = Availability.Unavailable("nope"))
        val events = SummaryPipeline(nano, ExtractiveEngine()).run(SampleTexts.article, config.copy(inputKind = InputKind.ARTICLE), EngineMode.AUTO).toList()
        val done = events.last() as SummaryEvent.Completed
        assertEquals(EngineId.EXTRACTIVE, done.engine)
        assertTrue(nano.calls.isEmpty())
    }

    @Test
    fun autoModeFallsBackWhenNanoThrows() = runTest {
        val nano = FakeNano(failWith = IllegalStateException("boom"))
        val events = SummaryPipeline(nano, ExtractiveEngine()).run(SampleTexts.article, config.copy(inputKind = InputKind.ARTICLE), EngineMode.AUTO).toList()
        assertTrue(events.any { it is SummaryEvent.Notice })
        assertEquals(EngineId.EXTRACTIVE, (events.last() as SummaryEvent.Completed).engine)
    }

    @Test(expected = SummaryFailedException::class)
    fun nanoOnlyModeDoesNotFallBack() = runTest {
        val nano = FakeNano(failWith = IllegalStateException("boom"))
        SummaryPipeline(nano, ExtractiveEngine()).run("some text here", config, EngineMode.GEMINI_NANO).toList()
    }

    @Test
    fun unsupportedLanguageSkipsNano() = runTest {
        val nano = FakeNano()
        val events = SummaryPipeline(nano, ExtractiveEngine())
            .run(SampleTexts.article, config, EngineMode.AUTO, languageSupportedByNano = false).toList()
        assertEquals(EngineId.EXTRACTIVE, (events.last() as SummaryEvent.Completed).engine)
        assertTrue(nano.calls.isEmpty())
    }

    @Test
    fun normalizeBulletsHandlesVariousMarkers() {
        assertEquals("• a\n• b\n• c", SummaryPipeline.normalizeBullets("* a\n\n- b\n1. c"))
    }
}
