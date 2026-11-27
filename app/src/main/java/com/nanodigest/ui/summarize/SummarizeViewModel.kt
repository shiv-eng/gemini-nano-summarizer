package com.nanodigest.ui.summarize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nanodigest.AppContainer
import com.nanodigest.core.engine.EngineId
import com.nanodigest.core.engine.EngineMode
import com.nanodigest.core.engine.EngineStatus
import com.nanodigest.core.engine.GeminiNanoEngine
import com.nanodigest.core.engine.InputKind
import com.nanodigest.core.engine.SummaryConfig
import com.nanodigest.core.engine.SummaryEvent
import com.nanodigest.core.engine.SummaryLanguage
import com.nanodigest.core.text.ChatFormatter
import com.nanodigest.core.text.ChatParseResult
import com.nanodigest.core.text.TextChunker
import com.nanodigest.core.text.TextStats
import com.nanodigest.data.Settings
import com.nanodigest.data.SummaryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SummarizeUiState(
    val input: String = "",
    val sourceLabel: String? = null,
    val inputKind: InputKind = InputKind.AUTO,
    val resolvedKind: InputKind = InputKind.ARTICLE,
    val bullets: Int = 3,
    val language: SummaryLanguage = SummaryLanguage.ENGLISH,
    val detectedLanguage: String? = null,
    val lastN: Int = 0,
    val chat: ChatParseResult? = null,
    val preparedText: String = "",
    val stats: TextStats = TextStats.EMPTY,
    val chunkCount: Int = 0,
    val running: Boolean = false,
    val stage: SummaryEvent.Stage? = null,
    val output: String = "",
    val result: SummaryEvent.Completed? = null,
    val notice: String? = null,
    val error: String? = null,
    /** Set once the finished digest is saved, so the UI can open it. */
    val savedId: Long? = null,
) {
    /** True unless we positively detected a language Gemini Nano can't summarize. */
    val languageSupported: Boolean
        get() = detectedLanguage == null || SummaryLanguage.fromCode(detectedLanguage) != null

    val config: SummaryConfig get() = SummaryConfig(resolvedKind, bullets, language)

    val tooShortForNano: Boolean get() = stats.chars in 1 until MIN_RECOMMENDED_CHARS

    companion object {
        const val MIN_RECOMMENDED_CHARS = 400
    }
}

class SummarizeViewModel(private val c: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SummarizeUiState(bullets = c.settings.settings.value.defaultBullets))
    val state: StateFlow<SummarizeUiState> = _state.asStateFlow()

    val engineStatus: StateFlow<EngineStatus> = c.modelManager.status
    val settings: StateFlow<Settings> = c.settings.settings

    private var prepareJob: Job? = null
    private var runJob: Job? = null

    init {
        refreshEngine()
        viewModelScope.launch {
            c.sharedText.incoming.filterNotNull().collect { incoming ->
                c.sharedText.consume(incoming.nonce)
                _state.update { it.copy(input = incoming.text, sourceLabel = incoming.source, result = null, output = "", error = null) }
                prepareNow()
                if (incoming.autoRun && (incoming.userInitiated || settings.value.autoRunSharedText)) summarize()
            }
        }
        // Re-check Gemini Nano when the engine mode or download preference changes.
        viewModelScope.launch {
            c.settings.settings.map { it.engineMode to it.autoDownloadModel }.distinctUntilChanged().collect { refreshEngine() }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text, sourceLabel = if (text.isEmpty()) null else it.sourceLabel) }
        schedulePrepare()
    }

    fun setInput(text: String, source: String?) {
        _state.update { it.copy(input = text, sourceLabel = source, result = null, output = "", error = null, notice = null) }
        schedulePrepare(immediate = true)
    }

    /** Fresh, empty editor (used each time "New digest" opens). */
    fun reset() {
        cancel()
        _state.value = SummarizeUiState(bullets = settings.value.defaultBullets)
        refreshEngine()
    }

    fun consumeSaved() = _state.update { it.copy(savedId = null) }

    fun clear() {
        cancel()
        _state.update { SummarizeUiState(bullets = it.bullets, inputKind = it.inputKind) }
        refreshEngine()
    }

    fun setInputKind(kind: InputKind) {
        _state.update { it.copy(inputKind = kind) }
        schedulePrepare(immediate = true)
    }

    fun setBullets(n: Int) {
        _state.update { it.copy(bullets = n.coerceIn(1, 3)) }
        refreshEngine()
    }

    fun setLanguage(language: SummaryLanguage) {
        _state.update { it.copy(language = language) }
        refreshEngine()
    }

    fun setLastN(n: Int) {
        _state.update { it.copy(lastN = n) }
        schedulePrepare(immediate = true)
    }

    fun dismissNotice() = _state.update { it.copy(notice = null, error = null) }

    fun downloadModel() = c.modelManager.download(_state.value.config)

    fun refreshEngine() {
        val s = settings.value
        if (s.engineMode == EngineMode.EXTRACTIVE) return
        c.modelManager.refresh(_state.value.config, autoDownload = s.autoDownloadModel, warmUp = s.warmUpOnLaunch)
    }

    private fun schedulePrepare(immediate: Boolean = false) {
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            if (!immediate) delay(300)
            prepareNow()
        }
    }

    /** Detects chat vs article, cleans chat exports, measures the text and detects its language. */
    private suspend fun prepareNow() {
        val snapshot = _state.value
        val s = settings.value
        val prepared = withContext(Dispatchers.Default) {
            val resolved = when (snapshot.inputKind) {
                InputKind.AUTO -> if (ChatFormatter.looksLikeChat(snapshot.input)) InputKind.CONVERSATION else InputKind.ARTICLE
                else -> snapshot.inputKind
            }
            val chat = if (resolved == InputKind.CONVERSATION && s.cleanChatExports && snapshot.input.isNotBlank()) {
                ChatFormatter.parse(snapshot.input, snapshot.lastN, s.replaceLinks).takeIf { it.kept.isNotEmpty() }
            } else {
                null
            }
            val text = chat?.formatted ?: snapshot.input.trim()
            val maxChars = if (s.engineMode == EngineMode.EXTRACTIVE) Int.MAX_VALUE else GeminiNanoEngine.MAX_CHARS_PER_CHUNK
            Prepared(resolved, chat, text, TextStats.of(text), TextChunker.chunkCount(text, maxChars))
        }
        val detected = c.languageDetector.detect(prepared.text)
        val oldConfig = _state.value.config
        _state.update {
            it.copy(
                resolvedKind = prepared.kind,
                chat = prepared.chat,
                preparedText = prepared.text,
                stats = prepared.stats,
                chunkCount = prepared.chunks,
                detectedLanguage = detected,
                language = SummaryLanguage.fromCode(detected) ?: it.language,
            )
        }
        if (_state.value.config != oldConfig) refreshEngine()
    }

    private data class Prepared(
        val kind: InputKind,
        val chat: ChatParseResult?,
        val text: String,
        val stats: TextStats,
        val chunks: Int,
    )

    fun summarize() {
        runJob?.cancel()
        runJob = viewModelScope.launch {
            prepareJob?.join()
            if (_state.value.preparedText.isBlank()) {
                prepareNow()
            }
            val snapshot = _state.value
            if (snapshot.preparedText.isBlank()) {
                _state.update { it.copy(error = "Paste or share some text first.") }
                return@launch
            }
            _state.update { it.copy(running = true, output = "", result = null, error = null, notice = null, stage = null) }
            try {
                c.pipeline.run(
                    text = snapshot.preparedText,
                    config = snapshot.config,
                    mode = settings.value.engineMode,
                    languageSupportedByNano = snapshot.languageSupported,
                ).collect { event ->
                    when (event) {
                        is SummaryEvent.Stage -> _state.update { it.copy(stage = event) }
                        is SummaryEvent.Partial -> _state.update { it.copy(output = event.text) }
                        is SummaryEvent.Notice -> _state.update { it.copy(notice = event.message) }
                        is SummaryEvent.Completed -> {
                            _state.update { it.copy(result = event, output = event.summary, stage = null) }
                            // Digests are the app's notes: always kept, and opened once saved.
                            val id = save(snapshot, event)
                            _state.update { it.copy(savedId = id) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message ?: "Summarization failed") }
            } finally {
                _state.update { it.copy(running = false, stage = null) }
            }
        }
    }

    fun cancel() {
        runJob?.cancel()
        _state.update { it.copy(running = false, stage = null) }
    }

    private suspend fun save(snapshot: SummarizeUiState, result: SummaryEvent.Completed): Long {
        val title = digestTitle(snapshot)
        return c.repository.save(
            SummaryEntity(
                title = title.ifBlank { "Untitled" },
                sourceText = snapshot.preparedText,
                summary = result.summary,
                inputKind = snapshot.resolvedKind.name,
                bullets = snapshot.bullets,
                language = snapshot.language.code,
                engine = result.engine.name,
                chunkCount = result.chunks,
                sourceChars = snapshot.stats.chars,
                firstTokenMs = result.firstTokenMs,
                totalMs = result.totalMs,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * A short, human title: the group name from a WhatsApp export file ("WhatsApp Chat with
     * Team.txt" → "Team"), the people in a chat, or the first line of an article.
     */
    private fun digestTitle(snapshot: SummarizeUiState): String {
        snapshot.sourceLabel?.let { label ->
            Regex("""(?i)^whatsapp chat (?:with|-)\s*(.+?)(?:\.(?:txt|zip))?$""").find(label)?.let { return it.groupValues[1].trim() }
        }
        snapshot.chat?.let { chat ->
            val names = chat.participants.take(3).joinToString(", ")
            val more = (chat.participants.size - 3).takeIf { it > 0 }?.let { " +$it" }.orEmpty()
            return (names + more).ifBlank { "Chat" }
        }
        val first = snapshot.preparedText.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
        val title = if (first.length > 60) first.take(60).substringBeforeLast(' ') + "…" else first
        return title.ifBlank { "Untitled" }
    }

    /** Markdown version of the current summary, for copy/share. */
    fun shareText(): String {
        val s = _state.value
        val engine = s.result?.engine ?: EngineId.GEMINI_NANO
        return buildString {
            appendLine("Summary (${engine.shortLabel}, on-device):")
            appendLine(s.output)
        }.trim()
    }
}
