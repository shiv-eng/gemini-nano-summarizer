package com.nanodigest.ui.summarize

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanodigest.core.engine.EngineMode
import com.nanodigest.core.engine.InputKind
import com.nanodigest.core.engine.SummaryLanguage
import com.nanodigest.data.TextImporter
import com.nanodigest.ui.SampleTexts
import com.nanodigest.ui.components.EngineStatusCard
import com.nanodigest.ui.components.InfoCard
import com.nanodigest.ui.components.SectionTitle
import com.nanodigest.ui.components.copyToClipboard
import com.nanodigest.ui.components.formatMs
import com.nanodigest.ui.components.readClipboard
import com.nanodigest.ui.components.rememberSpeaker
import com.nanodigest.ui.components.shareText
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SummarizeScreen(vm: SummarizeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val engine by vm.engineStatus.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = rememberSpeaker()
    var menuOpen by remember { mutableStateOf(false) }
    var previewOpen by rememberSaveable { mutableStateOf(false) }
    val numbers = remember { NumberFormat.getIntegerInstance() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { TextImporter.read(context, uri) }
                    .onSuccess { vm.setInput(it.text, it.name ?: "Imported file") }
                    .onFailure { Toast.makeText(context, it.message ?: "Import failed", Toast.LENGTH_LONG).show() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nano Digest") },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("text/plain", "application/zip")) }) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = "Import .txt or WhatsApp export")
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Try a sample team chat") }, onClick = {
                            menuOpen = false
                            vm.setInput(SampleTexts.teamChat, "Sample chat")
                        })
                        DropdownMenuItem(text = { Text("Try a sample article") }, onClick = {
                            menuOpen = false
                            vm.setInput(SampleTexts.article, "Sample article")
                        })
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EngineStatusCard(engine, settings.engineMode, onDownload = vm::downloadModel)

            OutlinedTextField(
                value = state.input,
                onValueChange = vm::onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                label = { Text(state.sourceLabel ?: "Paste a chat, notes or an article") },
                placeholder = { Text("Or share text / a WhatsApp export to Nano Digest, or select text anywhere → Summarize") },
                minLines = 6,
                maxLines = 12,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(
                    onClick = {
                        val clip = readClipboard(context)
                        if (clip.isNullOrBlank()) Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        else vm.setInput(clip, "From clipboard")
                    },
                    label = { Text("Paste") },
                    leadingIcon = { Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp)) },
                )
                if (state.input.isNotEmpty()) {
                    AssistChip(
                        onClick = vm::clear,
                        label = { Text("Clear") },
                        leadingIcon = { Icon(Icons.Outlined.Clear, null, Modifier.size(18.dp)) },
                    )
                }
                if (state.chat != null || (state.preparedText.isNotEmpty() && state.preparedText != state.input.trim())) {
                    AssistChip(
                        onClick = { previewOpen = true },
                        label = { Text("Preview cleaned input") },
                        leadingIcon = { Icon(Icons.Outlined.Visibility, null, Modifier.size(18.dp)) },
                    )
                }
            }

            if (state.stats.chars > 0) {
                InputSummaryLine(state, numbers, settings.engineMode)
            }

            SectionTitle("Input type")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                InputKind.entries.forEachIndexed { i, kind ->
                    SegmentedButton(
                        selected = state.inputKind == kind,
                        onClick = { vm.setInputKind(kind) },
                        shape = SegmentedButtonDefaults.itemShape(i, InputKind.entries.size),
                    ) {
                        Text(
                            if (kind == InputKind.AUTO && state.input.isNotBlank()) "Auto · ${state.resolvedKind.label}" else kind.label,
                        )
                    }
                }
            }

            SectionTitle("Bullets")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (1..3).forEach { n ->
                    SegmentedButton(
                        selected = state.bullets == n,
                        onClick = { vm.setBullets(n) },
                        shape = SegmentedButtonDefaults.itemShape(n - 1, 3),
                    ) { Text("$n") }
                }
            }

            if (state.resolvedKind == InputKind.CONVERSATION) {
                SectionTitle("Catch up on")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "All messages", 50 to "Last 50", 100 to "Last 100", 200 to "Last 200").forEach { (n, label) ->
                        FilterChip(selected = state.lastN == n, onClick = { vm.setLastN(n) }, label = { Text(label) })
                    }
                }
            }

            SectionTitle("Output language")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryLanguage.entries.forEach { lang ->
                    FilterChip(selected = state.language == lang, onClick = { vm.setLanguage(lang) }, label = { Text(lang.label) })
                }
            }
            if (!state.languageSupported && settings.engineMode != EngineMode.EXTRACTIVE) {
                InfoCard(
                    "Detected language: ${Locale.forLanguageTag(state.detectedLanguage ?: "").displayLanguage}. " +
                        "Gemini Nano summarizes English, Japanese and Korean, so the offline engine will be used.",
                )
            }
            if (state.tooShortForNano && settings.engineMode != EngineMode.EXTRACTIVE) {
                InfoCard("Short text (${state.stats.chars} chars). Gemini Nano works best with 400+ characters.")
            }

            Spacer(Modifier.height(4.dp))
            if (state.running) {
                FilledTonalButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }
            } else {
                Button(
                    onClick = vm::summarize,
                    enabled = state.input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Summarize")
                }
            }

            state.stage?.let { stage ->
                Column {
                    Text(stage.label, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    if (stage.totalSteps > 1) {
                        LinearProgressIndicator(
                            progress = { (stage.step - 1).toFloat() / stage.totalSteps },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (state.output.isEmpty()) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }

            state.notice?.let { InfoCard(it, onDismiss = vm::dismissNotice) }
            state.error?.let { InfoCard(it, isError = true, onDismiss = vm::dismissNotice) }

            AnimatedVisibility(visible = state.output.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Summary", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            state.result?.let { r ->
                                Text(r.engine.shortLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(state.output, style = MaterialTheme.typography.bodyLarge)
                        }
                        state.result?.let { r ->
                            Spacer(Modifier.height(8.dp))
                            val parts = buildList {
                                r.firstTokenMs?.let { add("first words in ${formatMs(it)}") }
                                add("done in ${formatMs(r.totalMs)}")
                                if (r.chunks > 1) add("${r.chunks} parts combined")
                            }
                            Text(
                                parts.joinToString(" · ").replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "AI-generated on your device — check important details.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row {
                                TextButton(onClick = {
                                    copyToClipboard(context, "Summary", state.output)
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy")
                                }
                                TextButton(onClick = { shareText(context, vm.shareText()) }) {
                                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share")
                                }
                                TextButton(onClick = { speaker.toggle(state.output, Locale.forLanguageTag(state.language.code)) }) {
                                    Icon(
                                        if (speaker.speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
                                        null,
                                        Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (speaker.speaking) "Stop" else "Listen")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (previewOpen) {
        AlertDialog(
            onDismissRequest = { previewOpen = false },
            confirmButton = { TextButton(onClick = { previewOpen = false }) { Text("Close") } },
            title = { Text("What the model will read") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.chat?.let { chat ->
                        Text(
                            "Removed timestamps, ${chat.droppedSystemLines} system notices; " +
                                "${chat.mediaCount} media → placeholders; ${chat.linksReplaced} links → [link].",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(state.preparedText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}

@Composable
private fun InputSummaryLine(state: SummarizeUiState, numbers: NumberFormat, mode: EngineMode) {
    val parts = buildList {
        add("${numbers.format(state.stats.chars)} chars")
        add("~${numbers.format(state.stats.estimatedTokens)} tokens")
        if (state.chunkCount > 1 && mode != EngineMode.EXTRACTIVE) add("${state.chunkCount} parts (chunk & combine)")
    }
    Column {
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.chat?.let { chat ->
            val windowNote = if (chat.kept.size < chat.messages.size) " (last ${chat.kept.size} of ${chat.messages.size})" else ""
            Text(
                "Chat · ${chat.participants.size} people · ${numbers.format(chat.kept.size)} messages$windowNote" +
                    if (chat.mediaCount > 0) " · ${chat.mediaCount} media" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
