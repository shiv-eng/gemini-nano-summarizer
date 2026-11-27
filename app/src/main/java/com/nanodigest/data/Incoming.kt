package com.nanodigest.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Text handed to the app from outside: Share sheet, the "Summarize" text-selection action, files. */
data class IncomingText(
    val text: String,
    val source: String,
    val autoRun: Boolean,
    /** The user explicitly asked for a summary (button or shortcut), so run regardless of settings. */
    val userInitiated: Boolean = false,
    val nonce: Long = System.nanoTime(),
)

class SharedTextBus {
    private val _incoming = MutableStateFlow<IncomingText?>(null)
    val incoming: StateFlow<IncomingText?> = _incoming.asStateFlow()

    /** Nonce of the most recent post; never cleared, so UI can react (e.g. switch tabs) even after consumption. */
    private val _lastPosted = MutableStateFlow(0L)
    val lastPosted: StateFlow<Long> = _lastPosted.asStateFlow()

    /** The post the UI has already navigated for (survives activity recreation). */
    var navigatedFor: Long = 0L

    fun post(text: IncomingText) {
        _incoming.value = text
        _lastPosted.value = text.nonce
    }

    fun consume(nonce: Long) {
        if (_incoming.value?.nonce == nonce) _incoming.value = null
    }
}

/**
 * Reads text from a content URI: plain .txt files, and WhatsApp "Export chat" .zip archives
 * (the transcript is the .txt entry inside; media files are ignored).
 */
object TextImporter {

    private const val MAX_BYTES = 4 * 1024 * 1024

    data class Imported(val text: String, val name: String?)

    suspend fun read(context: Context, uri: Uri): Imported = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        val mime = resolver.getType(uri).orEmpty()
        val isZip = mime.contains("zip") || name?.endsWith(".zip", ignoreCase = true) == true

        val text = resolver.openInputStream(uri)?.use { input ->
            if (isZip) {
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null && (entry.isDirectory || !entry.name.endsWith(".txt", ignoreCase = true))) {
                        entry = zip.nextEntry
                    }
                    if (entry == null) throw IllegalArgumentException("No chat .txt file found inside the archive")
                    zip.readCapped()
                }
            } else {
                input.readCapped()
            }
        } ?: throw IllegalArgumentException("Couldn't open the file")
        Imported(text, name)
    }

    private fun InputStream.readCapped(): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < MAX_BYTES) {
            val n = read(buf, 0, minOf(buf.size, MAX_BYTES - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
