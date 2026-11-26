package com.nanodigest.core.engine

/** What the user picked. AUTO is resolved to ARTICLE or CONVERSATION before inference. */
enum class InputKind(val label: String) {
    AUTO("Auto"),
    ARTICLE("Article"),
    CONVERSATION("Chat"),
}

enum class SummaryLanguage(val code: String, val label: String) {
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어");

    companion object {
        fun fromCode(code: String?): SummaryLanguage? = entries.firstOrNull { it.code == code }
    }
}

/** A fully resolved request configuration. [inputKind] is never AUTO here. */
data class SummaryConfig(
    val inputKind: InputKind,
    val bullets: Int,
    val language: SummaryLanguage,
) {
    init {
        require(inputKind != InputKind.AUTO) { "Resolve AUTO before building a SummaryConfig" }
        require(bullets in 1..3) { "Gemini Nano supports 1 to 3 bullets" }
    }
}

enum class EngineId(val label: String, val shortLabel: String) {
    GEMINI_NANO("Gemini Nano · on-device", "Gemini Nano"),
    EXTRACTIVE("Offline extractive · any phone", "Extractive"),
}

/** Which engine(s) the user allows. */
enum class EngineMode(val label: String, val description: String) {
    AUTO("Auto", "Gemini Nano when supported, otherwise the offline extractive engine"),
    GEMINI_NANO("Gemini Nano only", "Fail instead of falling back"),
    EXTRACTIVE("Offline extractive only", "Fast, works on every phone, picks key sentences verbatim"),
}

sealed interface Availability {
    data object Ready : Availability
    data object NeedsDownload : Availability
    data object Downloading : Availability
    data class Unavailable(val reason: String) : Availability

    val canRun: Boolean get() = this !is Unavailable
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class InProgress(val downloadedBytes: Long, val totalBytes: Long) : DownloadState {
        val fraction: Float? get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }
    data object Completed : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** Events emitted while a summary is being produced. */
sealed interface SummaryEvent {
    /** A step in a multi-step job, e.g. "Reading part 2 of 4". */
    data class Stage(val label: String, val step: Int, val totalSteps: Int) : SummaryEvent

    /** The final summary text produced so far (streams in as the model generates it). */
    data class Partial(val text: String) : SummaryEvent

    /** Informational, e.g. "Gemini Nano is busy, switched to the offline engine". */
    data class Notice(val message: String) : SummaryEvent

    data class Completed(
        val summary: String,
        val engine: EngineId,
        val chunks: Int,
        val firstTokenMs: Long?,
        val totalMs: Long,
    ) : SummaryEvent
}

class SummaryFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
