package com.nanodigest.core.engine

import android.content.Context
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gemini Nano through ML Kit's GenAI Summarization API.
 *
 * The model is not shipped in the APK: it lives in the AICore system app, is shared by every
 * app on the phone, and is downloaded on demand. This class:
 * - keeps one [Summarizer] per configuration and reuses it (creating clients per tap is wasteful)
 * - exposes download progress as a [Flow]
 * - can warm the inference engine up ahead of the first request ([warmUp]) — combined with
 *   streaming this is what makes the app *feel* several times faster without the model
 *   getting any faster
 */
class GeminiNanoEngine(context: Context) : SummaryEngine {

    private val appContext = context.applicationContext
    private val clients = mutableMapOf<SummaryConfig, Summarizer>()
    private val lock = Mutex()
    private val warmed: MutableSet<SummaryConfig> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    override val id = EngineId.GEMINI_NANO

    /** ~2,500 tokens at 4 chars/token: comfortably under the ~4,000 token window. */
    override val maxInputChars = MAX_CHARS_PER_CHUNK

    private suspend fun client(config: SummaryConfig): Summarizer = lock.withLock {
        clients.getOrPut(config) {
            val options = SummarizerOptions.builder(appContext)
                .setInputType(
                    when (config.inputKind) {
                        InputKind.CONVERSATION -> SummarizerOptions.InputType.CONVERSATION
                        else -> SummarizerOptions.InputType.ARTICLE
                    },
                )
                .setOutputType(
                    when (config.bullets) {
                        1 -> SummarizerOptions.OutputType.ONE_BULLET
                        2 -> SummarizerOptions.OutputType.TWO_BULLETS
                        else -> SummarizerOptions.OutputType.THREE_BULLETS
                    },
                )
                .setLanguage(
                    when (config.language) {
                        SummaryLanguage.ENGLISH -> SummarizerOptions.Language.ENGLISH
                        SummaryLanguage.JAPANESE -> SummarizerOptions.Language.JAPANESE
                        SummaryLanguage.KOREAN -> SummarizerOptions.Language.KOREAN
                    },
                )
                // Safety net only: we chunk ourselves so nothing is silently dropped.
                .setLongInputAutoTruncationEnabled(true)
                .build()
            Summarization.getClient(options)
        }
    }

    override suspend fun availability(config: SummaryConfig): Availability = try {
        when (client(config).checkFeatureStatus().await()) {
            FeatureStatus.AVAILABLE -> Availability.Ready
            FeatureStatus.DOWNLOADABLE -> Availability.NeedsDownload
            FeatureStatus.DOWNLOADING -> Availability.Downloading
            else -> Availability.Unavailable("Gemini Nano isn't supported on this device or configuration.")
        }
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        Availability.Unavailable(GenAiErrors.describe(t))
    }

    /** Downloads the model (or attaches to an in-flight download) and reports progress. */
    fun download(config: SummaryConfig): Flow<DownloadState> = callbackFlow {
        var total = 0L
        trySend(DownloadState.InProgress(0, 0))
        val future = client(config).downloadFeature(object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) {
                total = bytesToDownload
                trySend(DownloadState.InProgress(0, total))
            }

            override fun onDownloadProgress(totalBytesDownloaded: Long) {
                trySend(DownloadState.InProgress(totalBytesDownloaded, total))
            }

            override fun onDownloadCompleted() {
                trySend(DownloadState.Completed)
                close()
            }

            override fun onDownloadFailed(e: GenAiException) {
                trySend(DownloadState.Failed(GenAiErrors.describe(e)))
                close()
            }
        })
        // If the model was already present the future completes without callbacks.
        try {
            future.await()
            trySend(DownloadState.Completed)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            trySend(DownloadState.Failed(GenAiErrors.describe(t)))
        }
        close()
        awaitClose { }
    }

    /**
     * Loads the model into memory ahead of the first request, so tapping "Summarize" starts
     * streaming immediately. Returns the time it took, or null if it failed.
     */
    suspend fun warmUp(config: SummaryConfig): Long? {
        if (config in warmed) return 0
        val start = System.nanoTime()
        return try {
            client(config).prepareInferenceEngine().await()
            warmed += config
            (System.nanoTime() - start) / 1_000_000
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            null
        }
    }

    suspend fun baseModelName(config: SummaryConfig): String? = try {
        client(config).getBaseModelName().await()
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        null
    }

    override suspend fun summarize(text: String, config: SummaryConfig, onText: (String) -> Unit): String {
        val request = SummarizationRequest.builder(text).build()
        val result = client(config).runInference(request) { newText -> onText(newText) }.await()
        warmed += config
        return result.summary
    }

    override fun close() {
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
        warmed.clear()
    }

    companion object {
        const val MAX_CHARS_PER_CHUNK = 10_000
    }
}
