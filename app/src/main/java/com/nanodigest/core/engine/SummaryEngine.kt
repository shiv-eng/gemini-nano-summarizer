package com.nanodigest.core.engine

/**
 * Our own abstraction over summarization backends. ML Kit GenAI is still beta, so the rest of
 * the app talks to this interface only — swapping in a cloud fallback or a fake for tests is
 * a one-class change.
 */
interface SummaryEngine {
    val id: EngineId

    /** Largest input (in characters) this engine should be given in a single call. */
    val maxInputChars: Int

    suspend fun availability(config: SummaryConfig): Availability

    /**
     * Summarizes [text] (which must fit in [maxInputChars]). [onText] receives incremental
     * chunks of output as they are generated; the return value is the complete summary.
     */
    suspend fun summarize(text: String, config: SummaryConfig, onText: (String) -> Unit = {}): String

    fun close() {}
}
