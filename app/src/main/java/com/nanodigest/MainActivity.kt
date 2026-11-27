package com.nanodigest

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanodigest.data.ThemeMode
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.nanodigest.data.IncomingText
import com.nanodigest.data.TextImporter
import com.nanodigest.ui.NanoDigestRoot
import com.nanodigest.ui.theme.NanoDigestTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as NanoDigestApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Status/navigation bar icons must contrast with the app's theme, not the phone's.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            NanoDigestTheme(dark = dark) {
                NanoDigestRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Set by the "Summarize copied text" app shortcut; the clipboard is readable once we have focus. */
    private var pendingClipboard = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingClipboard) {
            pendingClipboard = false
            val clip = com.nanodigest.ui.components.readClipboard(this)
            if (clip.isNullOrBlank()) {
                Toast.makeText(this, "Copy a chat or article first", Toast.LENGTH_LONG).show()
            } else {
                container.sharedText.post(IncomingText(clip, "Copied text", autoRun = true, userInitiated = true))
            }
        }
    }

    /** Accepts text from the Share sheet, the text-selection "Summarize" action, shared files and the app shortcut. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_SUMMARIZE_CLIPBOARD -> pendingClipboard = true
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (!text.isNullOrBlank()) container.sharedText.post(IncomingText(text, "Selected text", autoRun = true))
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val uri = streamUri(intent)
                when {
                    uri != null -> lifecycleScope.launch {
                        runCatching { TextImporter.read(this@MainActivity, uri) }
                            .onSuccess { container.sharedText.post(IncomingText(it.text, it.name ?: "Shared file", autoRun = true)) }
                            .onFailure { Toast.makeText(this@MainActivity, it.message ?: "Couldn't read the shared file", Toast.LENGTH_LONG).show() }
                    }
                    !text.isNullOrBlank() -> container.sharedText.post(
                        IncomingText(text, intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "Shared text", autoRun = true),
                    )
                }
            }
        }
    }

    private companion object {
        const val ACTION_SUMMARIZE_CLIPBOARD = "com.nanodigest.action.SUMMARIZE_CLIPBOARD"
    }

    private fun streamUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
