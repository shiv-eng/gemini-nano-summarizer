package com.nanodigest.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nanodigest.core.engine.Availability
import com.nanodigest.core.engine.DownloadState
import com.nanodigest.core.engine.EngineMode
import com.nanodigest.core.engine.EngineStatus
import java.util.Locale

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

fun formatMs(ms: Long): String = if (ms < 1000) "$ms ms" else String.format(Locale.US, "%.1f s", ms / 1000.0)

@Composable
fun InfoCard(text: String, modifier: Modifier = Modifier, isError: Boolean = false, onDismiss: (() -> Unit)? = null) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun ActionsRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun readClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share summary"))
}

/** On-device text-to-speech for reading summaries aloud. */
class Speaker(context: Context) {
    private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }
    var speaking by mutableStateOf(false)
        private set

    fun toggle(text: String, locale: Locale) {
        if (!ready) return
        if (speaking) {
            tts.stop()
            speaking = false
            return
        }
        tts.language = locale
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { speaking = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { speaking = false }
        })
        tts.speak(text.replace("•", ""), TextToSpeech.QUEUE_FLUSH, null, "summary")
        speaking = true
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}
