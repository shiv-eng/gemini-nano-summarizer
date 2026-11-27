package com.nanodigest.ui.summarize

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanodigest.core.engine.InputKind
import com.nanodigest.data.TextImporter
import com.nanodigest.ui.SampleTexts
import com.nanodigest.ui.components.AvatarStack
import com.nanodigest.ui.components.InfoCard
import com.nanodigest.ui.components.KeyPointBlocks
import com.nanodigest.ui.components.engineSummary
import com.nanodigest.ui.components.readClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * The "new note" page. When opened from the + button it starts with whatever the user copied,
 * so the everyday flow is: copy a chat → open app → + → Summarize.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarizeScreen(vm: SummarizeViewModel, fresh: Boolean, onClose: () -> Unit, onSaved: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val engine by vm.engineStatus.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val numbers = remember { NumberFormat.getIntegerInstance() }
    var menuOpen by remember { mutableStateOf(false) }
    var pastedFromClipboard by rememberSaveable { mutableStateOf(false) }
    var prepared by rememberSaveable { mutableStateOf(false) }

    // Start clean, then pre-fill from the clipboard (only on first open of this page).
    LaunchedEffect(Unit) {
        if (!prepared) {
            prepared = true
            if (fresh) {
                vm.reset()
                delay(150) // clipboard is readable once the window has focus
                val clip = readClipboard(context)
                if (!clip.isNullOrBlank() && clip.trim().length >= 40) {
                    vm.setInput(clip, null)
                    pastedFromClipboard = true
                }
            }
        }
    }
    // A finished digest opens as its own page.
    LaunchedEffect(state.savedId) {
        state.savedId?.let { id ->
            vm.consumeSaved()
            onSaved(id)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { TextImporter.read(context, uri) }
                    .onSuccess { vm.setInput(it.text, it.name); pastedFromClipboard = false }
                    .onFailure { Toast.makeText(context, "Couldn't open that file", Toast.LENGTH_LONG).show() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New digest") },
                navigationIcon = { IconButton(onClick = { vm.cancel(); onClose() }) { Icon(Icons.Outlined.Close, "Close") } },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Open a file") }, onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf("text/plain", "application/zip"))
                            })
                            DropdownMenuItem(text = { Text("Try an example chat") }, onClick = {
                                menuOpen = false
                                vm.setInput(SampleTexts.teamChat, null); pastedFromClipboard = false
                            })
                            DropdownMenuItem(text = { Text("Try an example article") }, onClick = {
                                menuOpen = false
                                vm.setInput(SampleTexts.article, null); pastedFromClipboard = false
                            })
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.input.isNotBlank() && !state.running) {
                            PointsPicker(state.bullets, onPick = vm::setBullets)
                            Spacer(Modifier.width(8.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        if (state.running) {
                            OutlinedButton(onClick = vm::cancel, shape = CircleShape) { Text("Stop") }
                        } else {
                            Button(
                                onClick = vm::summarize,
                                enabled = state.input.isNotBlank(),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Summarize")
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // What we understood about the text, in plain words.
            if (state.stats.chars > 0) {
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val chat = state.chat
                    if (chat != null) {
                        AvatarStack(chat.participants, size = 22.dp, max = 4)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        when {
                            chat != null -> "Chat · ${chat.participants.size} people · ${numbers.format(chat.kept.size)} messages"
                            state.resolvedKind == InputKind.CONVERSATION -> "Chat"
                            else -> "Article · ${numbers.format(state.stats.words)} words"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (chat != null && chat.messages.size > 50) {
                        Spacer(Modifier.width(4.dp))
                        LastNPicker(state.lastN, vm::setLastN)
                    }
                }
            }
            if (pastedFromClipboard && state.input.isNotBlank()) {
                AssistChip(
                    onClick = { vm.clear(); pastedFromClipboard = false },
                    label = { Text("Pasted what you copied · Clear") },
                    leadingIcon = { Icon(Icons.Outlined.ContentPaste, null, Modifier.size(16.dp)) },
                    shape = CircleShape,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                )
            }

            TextField(
                value = state.input,
                onValueChange = { vm.onInputChange(it); if (it.isEmpty()) pastedFromClipboard = false },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                placeholder = {
                    Text("Paste a group chat, an email or an article…\n\nTip: copy a chat first and it will be pasted here automatically.")
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 8,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            if (state.running) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        state.stage?.takeIf { it.totalSteps > 1 }?.label ?: "Reading…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.output.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        KeyPointBlocks(state.output)
                    }
                }
            }
            state.notice?.let { InfoCard(it, Modifier.padding(16.dp), onDismiss = vm::dismissNotice) }
            state.error?.let { InfoCard(it, Modifier.padding(16.dp), isError = true, onDismiss = vm::dismissNotice) }

            Text(
                "${engineSummary(engine, settings.engineMode).label} · runs on this phone",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun PointsPicker(bullets: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("$bullets point${if (bullets > 1) "s" else ""}")
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (1..3).forEach { n ->
                DropdownMenuItem(text = { Text("$n key point${if (n > 1) "s" else ""}") }, onClick = { open = false; onPick(n) })
            }
        }
    }
}

@Composable
private fun LastNPicker(lastN: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = listOf(0 to "All messages", 50 to "Last 50", 100 to "Last 100", 200 to "Last 200")
    Box {
        TextButton(onClick = { open = true }) {
            Text(options.first { it.first == lastN }.second)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (n, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(n) }) }
        }
    }
}
