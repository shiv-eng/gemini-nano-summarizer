package com.nanodigest.ui.summarize

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanodigest.core.engine.Availability
import com.nanodigest.core.engine.EngineMode
import com.nanodigest.core.engine.EngineStatus
import com.nanodigest.core.engine.InputKind
import com.nanodigest.core.engine.SummaryLanguage
import com.nanodigest.data.TextImporter
import com.nanodigest.ui.SampleTexts
import com.nanodigest.ui.components.BrandMark
import com.nanodigest.ui.components.GradientButton
import com.nanodigest.ui.components.InfoCard
import com.nanodigest.ui.components.KeyPoints
import com.nanodigest.ui.components.SoftCard
import com.nanodigest.ui.components.StatusPill
import com.nanodigest.ui.components.copyToClipboard
import com.nanodigest.ui.components.engineSummary
import com.nanodigest.ui.components.formatMs
import com.nanodigest.ui.components.readClipboard
import com.nanodigest.ui.components.rememberSpeaker
import com.nanodigest.ui.components.shareText
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummarizeScreen(vm: SummarizeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val engine by vm.engineStatus.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = rememberSpeaker()
    val numbers = remember { NumberFormat.getIntegerInstance() }
    var menuOpen by remember { mutableStateOf(false) }
    var engineInfoOpen by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { TextImporter.read(context, uri) }
                    .onSuccess { vm.setInput(it.text, it.name ?: "File") }
                    .onFailure { Toast.makeText(context, "Couldn't open that file", Toast.LENGTH_LONG).show() }
            }
        }
    }
    val openFile = { importLauncher.launch(arrayOf("text/plain", "application/zip")) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(10.dp))
            Text(
                "Nano Digest",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f),
            )
            StatusPill(engineSummary(engine, settings.engineMode), onClick = { engineInfoOpen = true })
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Try an example chat") }, onClick = {
                        menuOpen = false
                        vm.setInput(SampleTexts.teamChat, "Example chat")
                    })
                    DropdownMenuItem(text = { Text("Try an example article") }, onClick = {
                        menuOpen = false
                        vm.setInput(SampleTexts.article, "Example article")
                    })
                    DropdownMenuItem(text = { Text("Open a file") }, onClick = {
                        menuOpen = false
                        openFile()
                    })
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("What did you miss?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Paste a group chat or an article and get the key points in seconds. Nothing leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        // Input
        SoftCard {
            Column {
                TextField(
                    value = state.input,
                    onValueChange = vm::onInputChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                    placeholder = { Text("Paste messages or text here…") },
                    minLines = 5,
                    maxLines = 10,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.input.isEmpty()) {
                        SmallAction(Icons.Outlined.ContentPaste, "Paste") {
                            val clip = readClipboard(context)
                            if (clip.isNullOrBlank()) Toast.makeText(context, "Nothing copied yet", Toast.LENGTH_SHORT).show()
                            else vm.setInput(clip, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallAction(Icons.Outlined.FileOpen, "Open file", openFile)
                    } else {
                        SmallAction(Icons.Outlined.Close, "Clear", vm::clear)
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.stats.words > 0) {
                        Text(
                            "${numbers.format(state.stats.words)} words",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // What we detected, in plain words
        if (state.stats.chars > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                detectedLine(state, numbers),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        // Options as compact dropdown chips
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownChip(
                label = "${state.bullets} key point${if (state.bullets > 1) "s" else ""}",
                options = listOf("1 key point", "2 key points", "3 key points"),
                onSelect = { vm.setBullets(it + 1) },
            )
            DropdownChip(
                label = when (state.inputKind) {
                    InputKind.AUTO -> "Type: auto"
                    InputKind.ARTICLE -> "Type: article"
                    InputKind.CONVERSATION -> "Type: chat"
                },
                options = listOf("Detect automatically", "It's an article or notes", "It's a chat"),
                onSelect = { vm.setInputKind(InputKind.entries[it]) },
            )
            if (state.resolvedKind == InputKind.CONVERSATION) {
                val windows = listOf(0, 50, 100, 200)
                DropdownChip(
                    label = if (state.lastN == 0) "All messages" else "Last ${state.lastN} messages",
                    options = listOf("All messages", "Last 50 messages", "Last 100 messages", "Last 200 messages"),
                    onSelect = { vm.setLastN(windows[it]) },
                )
            }
            DropdownChip(
                label = state.language.label,
                options = SummaryLanguage.entries.map { it.label },
                onSelect = { vm.setLanguage(SummaryLanguage.entries[it]) },
            )
        }

        Spacer(Modifier.height(20.dp))
        if (state.running) {
            OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape) {
                Text("Stop")
            }
        } else {
            GradientButton("Summarize", onClick = vm::summarize, enabled = state.input.isNotBlank())
        }

        state.stage?.let { stage ->
            Spacer(Modifier.height(14.dp))
            Text(
                if (stage.totalSteps > 1) stage.label else "Reading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (stage.totalSteps > 1) {
                LinearProgressIndicator(progress = { (stage.step - 1).toFloat() / stage.totalSteps }, modifier = Modifier.fillMaxWidth())
            } else if (state.output.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        state.notice?.let {
            Spacer(Modifier.height(12.dp))
            InfoCard(it, onDismiss = vm::dismissNotice)
        }
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            InfoCard(it, isError = true, onDismiss = vm::dismissNotice)
        }

        // Result
        AnimatedVisibility(
            visible = state.output.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it / 6 },
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                SoftCard {
                    Column(Modifier.padding(20.dp)) {
                        Text("Key points", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        SelectionContainer { KeyPoints(state.output) }
                        state.result?.let { r ->
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "${r.engine.shortLabel} · ready in ${formatMs(r.totalMs)} · double-check anything important",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallAction(Icons.Outlined.ContentCopy, "Copy") {
                                    copyToClipboard(context, "Summary", state.output)
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }
                                SmallAction(Icons.Outlined.Share, "Share") { shareText(context, vm.shareText()) }
                                SmallAction(
                                    if (speaker.speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
                                    if (speaker.speaking) "Stop" else "Read aloud",
                                ) { speaker.toggle(state.output, Locale.forLanguageTag(state.language.code)) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (engineInfoOpen) {
        EngineInfoDialog(engine, settings.engineMode, onDownload = vm::downloadModel, onDismiss = { engineInfoOpen = false })
    }
}

private fun detectedLine(state: SummarizeUiState, numbers: NumberFormat): String {
    val chat = state.chat
    return when {
        chat != null -> {
            val shown = if (chat.kept.size < chat.messages.size) "last ${chat.kept.size} of ${chat.messages.size}" else numbers.format(chat.kept.size)
            "Chat with ${chat.participants.size} people · $shown messages"
        }
        state.resolvedKind == InputKind.CONVERSATION -> "Chat"
        state.chunkCount > 1 -> "Long text · read in ${state.chunkCount} parts"
        else -> "Article or notes"
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        border = null,
    )
}

@Composable
private fun DropdownChip(label: String, options: List<String>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) },
            shape = CircleShape,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    open = false
                    onSelect(i)
                })
            }
        }
    }
}

@Composable
private fun EngineInfoDialog(status: EngineStatus, mode: EngineMode, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val ready = status.availability == Availability.Ready && mode != EngineMode.EXTRACTIVE
    val downloadable = status.availability == Availability.NeedsDownload && mode != EngineMode.EXTRACTIVE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ready) "Gemini Nano AI" else "Quick summary") },
        text = {
            Text(
                when {
                    ready -> "Summaries are written by Gemini Nano, Google's AI that runs right on your phone. Your text never leaves the device."
                    downloadable -> "Your phone can use Gemini Nano, Google's on-device AI. It needs a one-time download. Until then, Quick summary picks out the key points."
                    mode == EngineMode.EXTRACTIVE -> "Quick summary finds the most important points in your text. It's instant and works without the internet."
                    else -> "This phone doesn't support Gemini Nano, Google's on-device AI. Quick summary finds the most important points instead. It's instant, private and works without the internet."
                },
            )
        },
        confirmButton = {
            if (downloadable) {
                TextButton(onClick = { onDownload(); onDismiss() }) { Text("Download") }
            } else {
                TextButton(onClick = onDismiss) { Text("Got it") }
            }
        },
        dismissButton = if (downloadable) ({ TextButton(onClick = onDismiss) { Text("Later") } }) else null,
    )
}
