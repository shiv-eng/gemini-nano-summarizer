package com.nanodigest.core.lang

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Detects the input language fully on-device (the model is bundled in the APK), so the app can
 * pick the right Gemini Nano language or fall back to the offline engine for unsupported ones.
 */
class LanguageDetector {

    private val client = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.5f).build(),
    )

    /** BCP-47 code such as "en", "ja", "hi", or null when undetermined. */
    suspend fun detect(text: String): String? {
        if (text.isBlank()) return null
        return try {
            // Strip "Name:" prefixes from chat lines; they skew detection.
            val sample = text.lineSequence()
                .map { it.substringAfter(": ", it) }
                .joinToString(" ")
                .take(SAMPLE_CHARS)
            client.identifyLanguage(sample).await()
                .takeIf { it != UNDETERMINED }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun close() = client.close()

    private companion object {
        const val SAMPLE_CHARS = 2_000
        const val UNDETERMINED = "und"
    }
}
