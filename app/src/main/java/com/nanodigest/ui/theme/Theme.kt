package com.nanodigest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The indigo → violet gradient used by the logo and the main action. */
val BrandGradient = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFA855F7)))

val SuccessGreen = Color(0xFF22C55E)

private val Light = lightColorScheme(
    // Clean white "notes app" palette: neutral greys, indigo used sparingly as the accent.
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF0FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0F9F6E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF0FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFFD9692A),
    tertiaryContainer = Color(0xFFFFF1E6),
    onTertiaryContainer = Color(0xFF4A1F00),
    background = Color.White,
    onBackground = Color(0xFF16161D),
    surface = Color.White,
    onSurface = Color(0xFF16161D),
    surfaceVariant = Color(0xFFF2F2F5),
    onSurfaceVariant = Color(0xFF6B6B76),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFF5F5F7),
    surfaceContainerHigh = Color(0xFFEFEFF3),
    surfaceContainerHighest = Color(0xFFE9E9EE),
    outline = Color(0xFFD4D4DB),
    outlineVariant = Color(0xFFEAEAEF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB7ABFF),
    onPrimary = Color(0xFF24166B),
    primaryContainer = Color(0xFF3A2E8F),
    onPrimaryContainer = Color(0xFFE7E3FF),
    secondary = Color(0xFF5FD9C7),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF3A2E8F),
    onSecondaryContainer = Color(0xFFE7E3FF),
    tertiary = Color(0xFFFFB68A),
    tertiaryContainer = Color(0xFF5C2A08),
    onTertiaryContainer = Color(0xFFFFE8D9),
    background = Color(0xFF111018),
    onBackground = Color(0xFFE6E3F0),
    surface = Color(0xFF111018),
    onSurface = Color(0xFFE6E3F0),
    surfaceVariant = Color(0xFF2A2836),
    onSurfaceVariant = Color(0xFFA9A5BA),
    surfaceContainerLowest = Color(0xFF0C0B12),
    surfaceContainerLow = Color(0xFF16151E),
    surfaceContainer = Color(0xFF1B1A24),
    surfaceContainerHigh = Color(0xFF24232F),
    surfaceContainerHighest = Color(0xFF2E2D3A),
    outline = Color(0xFF4A4759),
    outlineVariant = Color(0xFF302E3C),
)

/**
 * Material 3's standard type scale (sizes, line heights and tracking untouched, all in sp so
 * the user's font-size setting is respected). Only weights are adjusted for hierarchy.
 */
private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun NanoDigestTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
