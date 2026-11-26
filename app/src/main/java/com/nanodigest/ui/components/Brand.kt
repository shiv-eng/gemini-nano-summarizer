package com.nanodigest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nanodigest.core.engine.Availability
import com.nanodigest.core.engine.DownloadState
import com.nanodigest.core.engine.EngineMode
import com.nanodigest.core.engine.EngineStatus
import com.nanodigest.ui.theme.BrandGradient
import com.nanodigest.ui.theme.SuccessGreen

/** Small gradient tile with a sparkle, echoing the launcher icon. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** What the app will use right now, in one short phrase. */
data class EngineSummary(val label: String, val dot: Color?, val busy: Boolean)

fun engineSummary(status: EngineStatus, mode: EngineMode): EngineSummary {
    val download = status.download
    return when {
        mode == EngineMode.EXTRACTIVE -> EngineSummary("Quick summary", Color(0xFF94A3B8), false)
        download is DownloadState.InProgress -> EngineSummary(
            download.fraction?.let { "Getting AI ready ${(it * 100).toInt()}%" } ?: "Getting AI ready…",
            null,
            true,
        )
        status.availability == null -> EngineSummary("Checking…", null, true)
        status.availability == Availability.Ready -> EngineSummary("Gemini Nano AI", SuccessGreen, false)
        status.availability == Availability.Downloading -> EngineSummary("Getting AI ready…", null, true)
        else -> EngineSummary("Quick summary", Color(0xFF94A3B8), false)
    }
}

@Composable
fun StatusPill(summary: EngineSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (summary.busy) {
                CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp)
            } else if (summary.dot != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(summary.dot))
            }
            Spacer(Modifier.width(6.dp))
            Text(summary.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Large pill button filled with the brand gradient. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(BrandGradient)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Renders "• Label: text" summary lines as numbered key points, with the label in bold.
 */
@Composable
fun KeyPoints(summary: String, modifier: Modifier = Modifier) {
    val lines = summary.lines().map { it.trim().removePrefix("•").removePrefix("*").trim() }.filter { it.isNotEmpty() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        lines.forEachIndexed { i, line ->
            Row {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                val labelEnd = line.indexOf(": ").takeIf { it in 1..20 && line.substring(0, it).none { c -> c.isDigit() } }
                Text(
                    buildAnnotatedString {
                        if (labelEnd != null) {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) {
                                append(line.substring(0, labelEnd))
                            }
                            append("\n")
                            append(line.substring(labelEnd + 2))
                        } else {
                            append(line)
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(top = 1.dp),
                )
            }
        }
    }
}

/** Rounded card with a soft container color, the app's basic building block. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    if (onClick != null) {
        Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) { content() }
    } else {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) { content() }
    }
}
