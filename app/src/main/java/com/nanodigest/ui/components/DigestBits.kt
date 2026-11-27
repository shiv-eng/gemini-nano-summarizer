package com.nanodigest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nanodigest.core.engine.ChatDigest
import kotlin.math.abs

enum class DigestKind(val label: String) { CHAT("Chat"), ARTICLE("Article") }

fun digestKindOf(inputKind: String): DigestKind =
    if (inputKind == "CONVERSATION") DigestKind.CHAT else DigestKind.ARTICLE

/** People who spoke in a chat digest's source, in order of appearance. */
fun participantsOf(source: String): List<String> = ChatDigest.parse(source).map { it.sender }.distinct()

/** One summary line split into its optional label ("Decision") and its text. */
data class KeyPoint(val label: String?, val text: String)

private val KNOWN_LABELS = setOf("What happened", "Decision", "Next steps")

fun keyPointsOf(summary: String): List<KeyPoint> = summary.lines()
    .map { it.trim().removePrefix("•").removePrefix("*").trim() }
    .filter { it.isNotEmpty() }
    .map { line ->
        val idx = line.indexOf(": ")
        // Only the headings the summarizer writes; "Marco: ..." stays part of the text.
        val label = line.takeIf { idx in 1..20 }?.substring(0, idx)?.takeIf { it in KNOWN_LABELS }
        if (label != null) KeyPoint(label, line.substring(idx + 2)) else KeyPoint(null, line)
    }

@Composable
fun TypeTag(kind: DigestKind, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val (fg, bg) = when (kind) {
        DigestKind.CHAT -> if (dark) Color(0xFFC4B5FD) to Color(0xFF2E2650) else Color(0xFF6D28D9) to Color(0xFFF3EEFF)
        DigestKind.ARTICLE -> if (dark) Color(0xFF6EE7B7) to Color(0xFF12352A) else Color(0xFF047857) to Color(0xFFE6F7F0)
    }
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(5.dp))
        Text(kind.label, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

private val avatarColors = listOf(
    Color(0xFFFDE68A) to Color(0xFF78350F),
    Color(0xFFBFDBFE) to Color(0xFF1E3A8A),
    Color(0xFFFBCFE8) to Color(0xFF831843),
    Color(0xFFBBF7D0) to Color(0xFF14532D),
    Color(0xFFDDD6FE) to Color(0xFF4C1D95),
    Color(0xFFFED7AA) to Color(0xFF7C2D12),
)

@Composable
fun Avatar(name: String, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val (bg, fg) = avatarColors[abs(name.hashCode()) % avatarColors.size]
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            style = if (size >= 32.dp) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

/** Overlapping avatars, like a group chat header. */
@Composable
fun AvatarStack(names: List<String>, size: Dp = 24.dp, max: Int = 4) {
    val shown = names.take(max)
    Box(Modifier.height(size).width(size + (size * 0.65f) * (shown.size - 1).coerceAtLeast(0))) {
        shown.forEachIndexed { i, n -> Avatar(n, size, Modifier.offset(x = size * 0.65f * i)) }
    }
}

/** Key points as calm, note-like blocks: a small colored label, then the text. */
@Composable
fun KeyPointBlocks(summary: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        keyPointsOf(summary).forEach { point ->
            // Accent bar stretches to the height of the text beside it.
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .padding(vertical = 3.dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    point.label?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(point.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
