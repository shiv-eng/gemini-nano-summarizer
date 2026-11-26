package com.nanodigest

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
            NanoDigestTheme {
                NanoDigestRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Accepts text from the Share sheet, the text-selection "Summarize" action, and shared files. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
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

    private fun streamUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
