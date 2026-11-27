package com.nanodigest.ui.history

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Button
import com.nanodigest.ui.components.Avatar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nanodigest.AppContainer
import com.nanodigest.core.engine.EngineId
import com.nanodigest.data.IncomingText
import com.nanodigest.data.SummaryEntity
import com.nanodigest.ui.SampleTexts
import com.nanodigest.ui.components.AvatarStack
import com.nanodigest.ui.components.CalendarRow
import com.nanodigest.ui.components.addToCalendar
import com.nanodigest.core.text.DateFinder
import com.nanodigest.ui.components.DigestKind
import com.nanodigest.ui.components.KeyPointBlocks
import com.nanodigest.ui.components.TypeTag
import com.nanodigest.ui.components.copyToClipboard
import com.nanodigest.ui.components.digestKindOf
import com.nanodigest.ui.components.keyPointsOf
import com.nanodigest.ui.components.participantsOf
import com.nanodigest.ui.components.rememberSpeaker
import com.nanodigest.ui.components.shareText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val items: StateFlow<List<SummaryEntity>?> = query.debounce(150)
        .flatMapLatest { c.repository.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun item(id: Long) = c.repository.observe(id)

    fun togglePin(e: SummaryEntity) = viewModelScope.launch { c.repository.setPinned(e.id, !e.pinned) }
    fun delete(id: Long) = viewModelScope.launch { c.repository.delete(id) }

    fun tryExample() = c.sharedText.post(IncomingText(SampleTexts.teamChat, "Example chat", autoRun = true))

    /** Sends the source text back to the editor, e.g. to try a different number of points. */
    fun reopen(e: SummaryEntity) = c.sharedText.post(IncomingText(e.sourceText, "Edit digest", autoRun = false))
}

private fun engineLabel(name: String) = runCatching { EngineId.valueOf(name).shortLabel }.getOrDefault(name)

private fun shareBody(e: SummaryEntity) =
    e.title + "\n\n" + keyPointsOf(e.summary).joinToString("\n") { p -> "• " + (p.label?.let { "$it: " } ?: "") + p.text }

// ---------------------------------------------------------------- Home (inbox list)

/** "9:30 AM" today, "Yesterday", "Tue" this week, otherwise "Nov 3". */
internal fun shortTime(millis: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val then = java.time.Instant.ofEpochMilli(millis).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val day = then.toLocalDate()
    return when {
        day == today -> then.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        day == today.minusDays(1) -> "Yesterday"
        day.isAfter(today.minusDays(7)) -> then.format(java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        else -> then.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
}

/** The single most useful line to show in the list: the decision if there is one. */
internal fun previewLine(summary: String): String {
    val points = keyPointsOf(summary)
    return (points.firstOrNull { it.label == "Decision" } ?: points.firstOrNull())?.text.orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HistoryViewModel,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onSummarizeClipboard: () -> Unit,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { vm.query.value = it },
                            placeholder = { Text("Search digests") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searching = false; vm.query.value = "" }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close search")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Digests", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, "Search") }
                        IconButton(onClick = onNew) { Icon(Icons.Outlined.Edit, "Paste or type text") }
                        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                    },
                )
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = onSummarizeClipboard,
                    shape = CircleShape,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                ) {
                    Icon(Icons.Outlined.ContentPaste, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Summarize copied text", style = MaterialTheme.typography.titleSmall)
                }
            }
        },
    ) { padding ->
        val list = items
        when {
            list == null -> Unit
            list.isEmpty() && query.isNotBlank() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("No digests match \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            list.isEmpty() -> EmptyHome(Modifier.padding(padding), onExample = vm::tryExample)
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(list, key = { it.id }) { e ->
                    DigestRow(e, onClick = { onOpen(e.id) }, onPin = { vm.togglePin(e) }, onDelete = { vm.delete(e.id) })
                    HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(modifier: Modifier, onExample: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Forum,
            null,
            Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("Your digests show up here", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Copy a group chat or an article, then tap Summarize copied text.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onExample) { Text("See an example") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DigestRow(e: SummaryEntity, onClick: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val preview = remember(e.summary) { previewLine(e.summary) }
    var menu by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(e.title, size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(shortTime(e.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (e.pinned) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.PushPin, "Pinned", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (e.pinned) "Unpin" else "Pin to top") }, onClick = { menu = false; onPin() })
            DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; shareText(context, shareBody(e)) })
            DropdownMenuItem(text = { Text("Copy") }, onClick = {
                menu = false
                copyToClipboard(context, "Digest", shareBody(e))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
        }
    }
}

// ---------------------------------------------------------------- Digest page

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(vm: HistoryViewModel, id: Long, onBack: () -> Unit, onReopen: () -> Unit) {
    val entity by remember(id) { vm.item(id) }.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val speaker = rememberSpeaker()
    var showSource by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    entity?.let { e ->
                        IconButton(onClick = { vm.togglePin(e) }) {
                            Icon(if (e.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (e.pinned) "Unpin" else "Pin")
                        }
                        IconButton(onClick = { shareText(context, shareBody(e)) }) { Icon(Icons.Outlined.Share, "Share") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                    }
                },
            )
        },
    ) { padding ->
        val e = entity ?: return@Scaffold
        val kind = digestKindOf(e.inputKind)
        val people = remember(e.id) { if (kind == DigestKind.CHAT) participantsOf(e.sourceText) else emptyList() }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            // Header matches the list row: avatar, title, who/when.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(e.title, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            if (people.isNotEmpty()) people.joinToString(", ") else kind.label,
                            shortTime(e.createdAt),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            SelectionContainer { KeyPointBlocks(e.summary) }
            val events = remember(e.id, e.summary) {
                val created = java.time.Instant.ofEpochMilli(e.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                DateFinder.find(e.summary, created)
            }
            if (events.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text("Dates mentioned", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                events.forEach { ev -> CalendarRow(ev, onAdd = { addToCalendar(context, ev) }) }
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { speaker.toggle(e.summary, Locale.forLanguageTag(e.language)) }, shape = CircleShape) {
                    Icon(if (speaker.speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (speaker.speaking) "Stop" else "Listen")
                }
                FilledTonalButton(onClick = {
                    copyToClipboard(context, "Digest", shareBody(e))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }, shape = CircleShape) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
            }
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showSource = !showSource }) {
                    Text(if (showSource) "Hide original text" else "Show original text")
                    Spacer(Modifier.width(4.dp))
                    Icon(if (showSource) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.reopen(e); onReopen() }) { Text("Redo") }
            }
            if (showSource) {
                SelectionContainer {
                    Text(e.sourceText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Made with ${engineLabel(e.engine)} on this phone · double-check anything important",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this digest?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(id)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
