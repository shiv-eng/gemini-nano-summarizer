package com.nanodigest.ui.engine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nanodigest.AppContainer
import com.nanodigest.core.engine.Availability
import com.nanodigest.core.engine.DownloadState
import com.nanodigest.core.engine.EngineId
import com.nanodigest.core.engine.EngineMode
import com.nanodigest.core.engine.EngineStatus
import com.nanodigest.core.engine.GenAiErrors
import com.nanodigest.core.engine.InputKind
import com.nanodigest.core.engine.SummaryConfig
import com.nanodigest.core.engine.SummaryEngine
import com.nanodigest.core.engine.SummaryLanguage
import com.nanodigest.data.Settings
import com.nanodigest.ui.SampleTexts
import com.nanodigest.ui.components.BrandMark
import com.nanodigest.ui.components.SoftCard
import com.nanodigest.ui.components.formatMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BenchmarkResult(val engine: EngineId, val totalMs: Long, val error: String? = null)

data class BenchmarkState(val running: Boolean = false, val results: List<BenchmarkResult> = emptyList())

class EngineViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings> = c.settings.settings
    val status: StateFlow<EngineStatus> = c.modelManager.status

    private val _benchmark = MutableStateFlow(BenchmarkState())
    val benchmark: StateFlow<BenchmarkState> = _benchmark.asStateFlow()

    private val defaultConfig get() = SummaryConfig(InputKind.ARTICLE, settings.value.defaultBullets, SummaryLanguage.ENGLISH)

    fun update(transform: (Settings) -> Settings) = c.settings.update(transform)

    fun download() = c.modelManager.download(status.value.config ?: defaultConfig)

    /** Times each available engine on the same example article. */
    fun runSpeedTest() {
        if (_benchmark.value.running) return
        viewModelScope.launch {
            _benchmark.value = BenchmarkState(running = true)
            val config = defaultConfig
            val results = mutableListOf<BenchmarkResult>()
            val engines: List<SummaryEngine> = listOf(c.nano, c.extractive)
            for (engine in engines) {
                if (engine.id == EngineId.GEMINI_NANO && c.nano.availability(config) != Availability.Ready) {
                    results += BenchmarkResult(engine.id, 0, "Not available on this phone")
                    continue
                }
                val start = System.nanoTime()
                try {
                    withContext(Dispatchers.Default) { engine.summarize(SampleTexts.article, config) }
                    results += BenchmarkResult(engine.id, (System.nanoTime() - start) / 1_000_000)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    results += BenchmarkResult(engine.id, 0, GenAiErrors.describe(t))
                }
                _benchmark.update { it.copy(results = results.toList()) }
            }
            _benchmark.value = BenchmarkState(running = false, results = results)
        }
    }
}

@Composable
fun EngineScreen(vm: EngineViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val bench by vm.benchmark.collectAsStateWithLifecycle()
    var advanced by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        SummaryMethodCard(status, settings.engineMode, onDownload = vm::download)

        Spacer(Modifier.height(28.dp))
        Text("Summaries", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text("Number of key points", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            (1..3).forEach { n ->
                SegmentedButton(
                    selected = settings.defaultBullets == n,
                    onClick = { vm.update { it.copy(defaultBullets = n) } },
                    shape = SegmentedButtonDefaults.itemShape(n - 1, 3),
                ) { Text("$n") }
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingSwitch("Summarize shared text right away", "When you share text to Nano Digest", settings.autoRunSharedText) { v ->
            vm.update { it.copy(autoRunSharedText = v) }
        }
        SettingSwitch("Tidy up chat exports", "Remove dates, times and system messages", settings.cleanChatExports) { v ->
            vm.update { it.copy(cleanChatExports = v) }
        }
        SettingSwitch("Keep history", "Save summaries on this phone", settings.saveHistory) { v ->
            vm.update { it.copy(saveHistory = v) }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = advanced, role = Role.Button) { advanced = !advanced }
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Advanced", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }

        AnimatedVisibility(advanced) {
            Column {
                Text("Summary method", style = MaterialTheme.typography.bodyLarge)
                EngineMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = settings.engineMode == mode, role = Role.RadioButton) { vm.update { it.copy(engineMode = mode) } }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.engineMode == mode, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                SettingSwitch("Download Gemini Nano automatically", "On phones that support it", settings.autoDownloadModel) { v ->
                    vm.update { it.copy(autoDownloadModel = v) }
                }
                SettingSwitch("Get the AI ready when the app opens", "First summary starts faster", settings.warmUpOnLaunch) { v ->
                    vm.update { it.copy(warmUpOnLaunch = v) }
                }
                SettingSwitch("Hide web links", "Links rarely help a summary", settings.replaceLinks) { v ->
                    vm.update { it.copy(replaceLinks = v) }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::runSpeedTest, enabled = !bench.running, shape = CircleShape) {
                        Text(if (bench.running) "Testing…" else "Speed test")
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        bench.results.forEach { r ->
                            Text(
                                "${r.engine.shortLabel}: ${r.error ?: formatMs(r.totalMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(
                "Private by design. Your text is summarized on this phone and is never uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SummaryMethodCard(status: EngineStatus, mode: EngineMode, onDownload: () -> Unit) {
    val download = status.download
    val nanoMode = mode != EngineMode.EXTRACTIVE
    val (title, body) = when {
        !nanoMode -> "Quick summary" to "Finds the most important points in your text. Instant and works without the internet."
        download is DownloadState.InProgress -> "Getting Gemini Nano ready" to "Google's on-device AI is downloading. You can keep using Quick summary meanwhile."
        status.availability == null -> "Checking your phone…" to "Seeing whether this phone supports Gemini Nano."
        status.availability == Availability.Ready -> "Gemini Nano AI is on" to "Google's AI writes your summaries right on this phone."
        status.availability == Availability.NeedsDownload -> "Gemini Nano is available" to "Your phone supports Google's on-device AI. It needs a one-time download."
        status.availability == Availability.Downloading -> "Getting Gemini Nano ready" to "Your phone is downloading Google's on-device AI."
        else -> "Using Quick summary" to "This phone doesn't support Gemini Nano, Google's on-device AI. Quick summary picks out the key points instead. It's instant, private and works offline."
    }
    SoftCard {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (nanoMode && download is DownloadState.InProgress) {
                Spacer(Modifier.height(12.dp))
                download.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    ?: LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (nanoMode && status.availability == Availability.NeedsDownload && download !is DownloadState.InProgress) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onDownload, shape = CircleShape) { Text("Download (one time)") }
            }
            if (nanoMode && download is DownloadState.Failed) {
                Spacer(Modifier.height(8.dp))
                Text("Download didn't finish. Check your Wi-Fi and try again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = checked, role = Role.Switch) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
