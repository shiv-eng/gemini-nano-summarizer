package com.nanodigest.ui.history

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nanodigest.AppContainer
import com.nanodigest.core.engine.EngineId
import com.nanodigest.data.IncomingText
import com.nanodigest.data.SummaryEntity
import com.nanodigest.ui.components.copyToClipboard
import com.nanodigest.ui.components.formatMs
import com.nanodigest.ui.components.rememberSpeaker
import com.nanodigest.ui.components.shareText
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val items: StateFlow<List<SummaryEntity>?> = query.debounce(200)
        .flatMapLatest { c.repository.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun item(id: Long) = c.repository.observe(id)

    fun togglePin(e: SummaryEntity) = viewModelScope.launch { c.repository.setPinned(e.id, !e.pinned) }
    fun delete(id: Long) = viewModelScope.launch { c.repository.delete(id) }
    fun clearUnpinned() = viewModelScope.launch { c.repository.clearUnpinned() }

    /** Sends the source text back to the Summarize tab, e.g. to try other options. */
    fun reopen(e: SummaryEntity) = c.sharedText.post(IncomingText(e.sourceText, "From history", autoRun = false))
}

private fun engineLabel(name: String) = runCatching { EngineId.valueOf(name).shortLabel }.getOrDefault(name)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel, onOpen: (Long) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    IconButton(onClick = { confirmClear = true }, enabled = !items.isNullOrEmpty()) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear unpinned")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Search summaries and sources") },
                singleLine = true,
            )
            val list = items
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyHistory(query.isNotBlank())
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { e -> HistoryRow(e, onClick = { onOpen(e.id) }, onPin = { vm.togglePin(e) }) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("Deletes every summary that isn't pinned. This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.clearUnpinned(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyHistory(searching: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.History, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            Text(
                if (searching) "No matches" else "Your summaries will appear here.\nThey're stored only on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(e: SummaryEntity, onClick: () -> Unit, onPin: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onPin) {
                    Icon(
                        if (e.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (e.pinned) "Unpin" else "Pin",
                        tint = if (e.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Text(e.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 12.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                "${engineLabel(e.engine)} · ${DateUtils.getRelativeTimeSpanString(e.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
                title = { Text(entity?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    entity?.let { e ->
                        IconButton(onClick = { vm.togglePin(e) }) {
                            Icon(if (e.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (e.pinned) "Unpin" else "Pin")
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                    }
                },
            )
        },
    ) { padding ->
        val e = entity ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SelectionContainer { Text(e.summary, style = MaterialTheme.typography.bodyLarge) }
                    Spacer(Modifier.height(8.dp))
                    val meta = buildList {
                        add(engineLabel(e.engine))
                        add("${e.bullets} bullet${if (e.bullets > 1) "s" else ""}")
                        add(e.inputKind.lowercase().replaceFirstChar { it.uppercase() })
                        add(Locale.forLanguageTag(e.language).displayLanguage)
                        e.firstTokenMs?.let { add("first words ${formatMs(it)}") }
                        add("total ${formatMs(e.totalMs)}")
                        if (e.chunkCount > 1) add("${e.chunkCount} parts")
                        add("${e.sourceChars} chars")
                    }
                    Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    copyToClipboard(context, "Summary", e.summary)
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copy") }
                TextButton(onClick = { shareText(context, "${e.title}\n${e.summary}") }) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share")
                }
                TextButton(onClick = { speaker.toggle(e.summary, Locale.forLanguageTag(e.language)) }) {
                    Icon(if (speaker.speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (speaker.speaking) "Stop" else "Listen")
                }
                TextButton(onClick = { vm.reopen(e); onReopen() }) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Redo")
                }
            }
            TextButton(onClick = { showSource = !showSource }) { Text(if (showSource) "Hide source text" else "Show source text") }
            if (showSource) {
                SelectionContainer {
                    Text(e.sourceText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this summary?") },
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
