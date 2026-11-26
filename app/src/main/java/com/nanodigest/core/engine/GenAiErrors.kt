package com.nanodigest.core.engine

import com.google.mlkit.genai.common.GenAiException

/** Human-readable explanations for ML Kit GenAI / AICore failures. */
object GenAiErrors {

    data class Explanation(val title: String, val fix: String, val retryable: Boolean)

    // Codes not exposed as constants in genai-common but reported by AICore in the wild.
    private const val BINDING_FAILURE = 601
    private const val FEATURE_NOT_FOUND = 606

    fun explain(code: Int): Explanation = when (code) {
        GenAiException.ErrorCode.NOT_AVAILABLE ->
            Explanation("Gemini Nano isn't available", "This phone or its current configuration doesn't support on-device summarization.", false)
        GenAiException.ErrorCode.BUSY ->
            Explanation("AICore is busy", "Another app is using the model or you hit the rate limit. Wait a few seconds and retry.", true)
        GenAiException.ErrorCode.REQUEST_TOO_LARGE ->
            Explanation("Input is too long", "The text exceeds the model window. Chunk-and-combine normally prevents this — try a smaller 'last N messages' window.", false)
        GenAiException.ErrorCode.REQUEST_TOO_SMALL ->
            Explanation("Input is too short", "Gemini Nano needs a few hundred characters to summarize. Add more text.", false)
        GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE ->
            Explanation("Not enough storage", "Free up some space so AICore can download the model.", true)
        GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE ->
            Explanation("System update needed", "Install pending system and Google Play system updates, then retry.", false)
        GenAiException.ErrorCode.AICORE_INCOMPATIBLE ->
            Explanation("AICore is out of date", "Update 'Android AICore' from the Play Store, then reopen the app.", false)
        GenAiException.ErrorCode.CANCELLED ->
            Explanation("Cancelled", "The request was cancelled.", true)
        BINDING_FAILURE ->
            Explanation("Can't connect to AICore", "Update 'Android AICore' from the Play Store, then reinstall this app.", false)
        FEATURE_NOT_FOUND ->
            Explanation("AICore is still setting up", "New phones fetch AICore configuration in the background. Stay on Wi-Fi and try again later.", true)
        GenAiException.ErrorCode.UNKNOWN ->
            Explanation("Unknown error", "Often a network problem during model download. Check your connection and retry.", true)
        else ->
            Explanation("Inference failed (code $code)", "Retry. If it keeps failing, use the offline engine.", true)
    }

    fun describe(t: Throwable): String {
        val genAi = t as? GenAiException ?: t.cause as? GenAiException
        return if (genAi != null) {
            val e = explain(genAi.errorCode)
            "${e.title}. ${e.fix}"
        } else {
            t.message ?: t::class.java.simpleName
        }
    }

    fun codeOf(t: Throwable): Int? = (t as? GenAiException ?: t.cause as? GenAiException)?.errorCode

    /** Reference list for the troubleshooting screen. */
    val reference: List<Pair<Int, Explanation>> = listOf(
        BINDING_FAILURE, FEATURE_NOT_FOUND, GenAiException.ErrorCode.NOT_AVAILABLE,
        GenAiException.ErrorCode.BUSY, GenAiException.ErrorCode.REQUEST_TOO_SMALL,
        GenAiException.ErrorCode.REQUEST_TOO_LARGE, GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE,
        GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE, GenAiException.ErrorCode.AICORE_INCOMPATIBLE,
        GenAiException.ErrorCode.UNKNOWN,
    ).map { it to explain(it) }
}
