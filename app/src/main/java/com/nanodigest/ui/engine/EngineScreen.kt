package com.nanodigest.ui.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nanodigest.AppContainer
import com.nanodigest.core.engine.Availability
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
import com.nanodigest.ui.components.EngineStatusCard
import com.nanodigest.ui.components.SectionTitle
import com.nanodigest.ui.components.formatMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BenchmarkResult(val engine: EngineId, val firstTokenMs: Long?, val totalMs: Long, val error: String? = null)

data class BenchmarkState(val running: Boolean = false, val results: List<BenchmarkResult> = emptyList())

class EngineViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings> = c.settings.settings
    val status: StateFlow<EngineStatus> = c.modelManager.status

    private val _benchmark = MutableStateFlow(BenchmarkState())
    val benchmark: StateFlow<BenchmarkState> = _benchmark.asStateFlow()

    private val defaultConfig get() = SummaryConfig(InputKind.ARTICLE, settings.value.defaultBullets, SummaryLanguage.ENGLISH)

    fun update(transform: (Settings) -> Settings) = c.settings.update(transform)

    fun recheck() = c.modelManager.refresh(status.value.config ?: defaultConfig, autoDownload = false, warmUp = false)
    fun download() = c.modelManager.download(status.value.config ?: defaultConfig)
    fun warmUp() = c.modelManager.warmUp(status.value.config ?: defaultConfig)

    /** Times both engines on the same sample article: time to first words and total time. */
    fun runBenchmark() {
        if (_benchmark.value.running) return
        viewModelScope.launch {
            _benchmark.value = BenchmarkState(running = true)
            val config = defaultConfig
            val results = mutableListOf<BenchmarkResult>()
            val engines: List<SummaryEngine> = listOf(c.nano, c.extractive)
            for (engine in engines) {
                if (engine.id == EngineId.GEMINI_NANO && c.nano.availability(config) != Availability.Ready) {
                    results += BenchmarkResult(engine.id, null, 0, "Model not ready on this device")
                    continue
                }
                val start = System.nanoTime()
                var first: Long? = null
                try {
                    withContext(Dispatchers.Default) {
                        engine.summarize(SampleTexts.article, config) {
                            if (first == null) first = (System.nanoTime() - start) / 1_000_000
                        }
                    }
                    results += BenchmarkResult(engine.id, first, (System.nanoTime() - start) / 1_000_000)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    results += BenchmarkResult(engine.id, null, 0, GenAiErrors.describe(t))
                }
                _benchmark.update { it.copy(results = results.toList()) }
            }
            _benchmark.value = BenchmarkState(running = false, results = results)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineScreen(vm: EngineViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val bench by vm.benchmark.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Engine & settings") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EngineStatusCard(status, EngineMode.GEMINI_NANO, onDownload = vm::download)
            status.modelName?.let {
                Text("Base model: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            status.warmUpMs?.let {
                Text("Last warm-up: ${formatMs(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::recheck) { Text("Check again") }
                OutlinedButton(onClick = vm::download, enabled = status.availability == Availability.NeedsDownload) { Text("Download") }
                OutlinedButton(onClick = vm::warmUp, enabled = status.availability == Availability.Ready && !status.warming) { Text("Warm up") }
            }

            SectionTitle("Engine")
            EngineMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = settings.engineMode == mode, role = Role.RadioButton) { vm.update { it.copy(engineMode = mode) } }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.engineMode == mode, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionTitle("Benchmark")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Summarizes a ~2,000 character sample with each engine. 'First words' is what users feel; 'total' is what the model does.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    bench.results.forEach { r ->
                        Text(
                            r.engine.shortLabel + ": " + (
                                r.error ?: listOfNotNull(r.firstTokenMs?.let { "first words ${formatMs(it)}" }, "total ${formatMs(r.totalMs)}")
                                    .joinToString(" · ")
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = vm::runBenchmark, enabled = !bench.running) {
                        Text(if (bench.running) "Running…" else "Run benchmark")
                    }
                }
            }

            SectionTitle("Behaviour")
            Text("Default bullets", style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (1..3).forEach { n ->
                    SegmentedButton(
                        selected = settings.defaultBullets == n,
                        onClick = { vm.update { it.copy(defaultBullets = n) } },
                        shape = SegmentedButtonDefaults.itemShape(n - 1, 3),
                    ) { Text("$n") }
                }
            }
            SettingSwitch("Download the model automatically", "Start the one-time AICore download when the app opens", settings.autoDownloadModel) { v ->
                vm.update { it.copy(autoDownloadModel = v) }
            }
            SettingSwitch("Warm up on launch", "Load Gemini Nano before you tap Summarize, so words appear sooner", settings.warmUpOnLaunch) { v ->
                vm.update { it.copy(warmUpOnLaunch = v) }
            }
            SettingSwitch("Clean chat exports", "Strip timestamps and system lines, format as \"Name: message\"", settings.cleanChatExports) { v ->
                vm.update { it.copy(cleanChatExports = v) }
            }
            SettingSwitch("Replace links with [link]", "URLs cost many tokens and rarely matter for a summary", settings.replaceLinks) { v ->
                vm.update { it.copy(replaceLinks = v) }
            }
            SettingSwitch("Auto-summarize shared text", "Run immediately when text arrives from Share or Select → Summarize", settings.autoRunSharedText) { v ->
                vm.update { it.copy(autoRunSharedText = v) }
            }
            SettingSwitch("Save history", "Keep summaries in a local database on this phone", settings.saveHistory) { v ->
                vm.update { it.copy(saveHistory = v) }
            }

            SectionTitle("Troubleshooting")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Gemini Nano needs a supported phone with a locked bootloader, Wi-Fi for the first download, and a real device (not an emulator).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider()
                    GenAiErrors.reference.forEach { (code, e) ->
                        Column {
                            Text("$code · ${e.title}", style = MaterialTheme.typography.labelLarge)
                            Text(e.fix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            SectionTitle("Privacy")
            Text(
                "Your text never leaves this phone. It is processed by Gemini Nano inside Android's AICore or by the built-in " +
                    "offline engine, and history is stored only on this phone and excluded from backups. " +
                    "Works in airplane mode once the model is downloaded.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = checked, role = Role.Switch) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
