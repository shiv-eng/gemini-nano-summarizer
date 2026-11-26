package com.nanodigest.core.engine

import com.nanodigest.core.text.TextChunker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * Orchestrates a summary job:
 * 1. picks an engine for the [EngineMode] (and falls back to the offline engine in AUTO mode)
 * 2. if the input is longer than the engine's window, runs chunk-and-combine (map-reduce):
 *    summarize each chunk, join those notes, and summarize the notes — recursively if needed
 * 3. streams the final pass so words appear as soon as the model emits them
 */
class SummaryPipeline(
    private val nano: SummaryEngine,
    private val extractive: SummaryEngine,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {

    fun run(text: String, config: SummaryConfig, mode: EngineMode, languageSupportedByNano: Boolean = true): Flow<SummaryEvent> =
        channelFlow {
            val start = clock()
            var firstTokenAt: Long? = null

            suspend fun execute(engine: SummaryEngine): Pair<String, Int> {
                val chunks = TextChunker.split(text, engine.maxInputChars)
                if (chunks.isEmpty()) throw SummaryFailedException("Nothing to summarize.")
                val final = StringBuilder()
                val onFinal: (String) -> Unit = { piece ->
                    if (firstTokenAt == null) firstTokenAt = clock()
                    final.append(piece)
                    trySend(SummaryEvent.Partial(final.toString()))
                }

                if (chunks.size == 1) {
                    send(SummaryEvent.Stage("Summarizing", 1, 1))
                    val result = engine.summarize(chunks.single(), config, onFinal)
                    return result.ifBlank { final.toString() } to 1
                }

                // Map: each chunk to three bullet notes, same input kind as the source.
                val noteConfig = config.copy(bullets = 3)
                val totalSteps = chunks.size + 1
                var notes = chunks.mapIndexed { i, chunk ->
                    send(SummaryEvent.Stage("Reading part ${i + 1} of ${chunks.size}", i + 1, totalSteps))
                    engine.summarize(chunk, noteConfig)
                }

                // Reduce: notes are prose bullets now, so summarize them as an article. If the
                // joined notes still overflow the window, collapse them group by group first.
                val reduceConfig = config.copy(inputKind = InputKind.ARTICLE)
                var joined = notes.joinToString("\n")
                var round = 1
                while (joined.length > engine.maxInputChars && round < 4) {
                    val groups = TextChunker.split(joined, engine.maxInputChars)
                    send(SummaryEvent.Stage("Condensing notes (round $round)", chunks.size, totalSteps))
                    notes = groups.map { engine.summarize(it, reduceConfig.copy(bullets = 3)) }
                    joined = notes.joinToString("\n")
                    round++
                }
                send(SummaryEvent.Stage("Combining ${chunks.size} parts", totalSteps, totalSteps))
                val result = engine.summarize(joined.take(engine.maxInputChars), reduceConfig, onFinal)
                return result.ifBlank { final.toString() } to chunks.size
            }

            val useNanoFirst = when (mode) {
                EngineMode.EXTRACTIVE -> false
                EngineMode.GEMINI_NANO -> true
                EngineMode.AUTO -> languageSupportedByNano && nano.availability(config).canRun
            }
            // Only worth mentioning when Gemini Nano would otherwise have been used.
            if (mode == EngineMode.AUTO && !languageSupportedByNano && nano.availability(config).canRun) {
                send(SummaryEvent.Notice("Gemini Nano only reads English, Japanese and Korean, so Quick summary was used."))
            }

            val (summary, chunks, engineUsed) = if (useNanoFirst) {
                try {
                    val (s, c) = execute(nano)
                    Triple(s, c, nano.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    if (mode != EngineMode.AUTO) throw SummaryFailedException(GenAiErrors.describe(t), t)
                    send(SummaryEvent.Notice("Gemini Nano couldn't finish, so Quick summary was used instead."))
                    send(SummaryEvent.Partial(""))
                    firstTokenAt = null
                    val (s, c) = execute(extractive)
                    Triple(s, c, extractive.id)
                }
            } else {
                val (s, c) = execute(extractive)
                Triple(s, c, extractive.id)
            }

            send(
                SummaryEvent.Completed(
                    summary = normalizeBullets(summary),
                    engine = engineUsed,
                    chunks = chunks,
                    firstTokenMs = firstTokenAt?.let { it - start },
                    totalMs = clock() - start,
                ),
            )
        }

    companion object {
        private val BULLET_PREFIX = Regex("""^\s*(?:[*\-•·]|\d+[.)])\s+""")

        /** Makes model output consistent: one "• " bullet per line, no blank lines. */
        fun normalizeBullets(text: String): String = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { line -> "• " + line.replace(BULLET_PREFIX, "").trim() }
    }
}
