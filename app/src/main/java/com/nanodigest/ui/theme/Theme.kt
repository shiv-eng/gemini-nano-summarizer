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
import androidx.compose.ui.unit.sp

/** The indigo → violet gradient used by the logo and the main action. */
val BrandGradient = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFA855F7)))

val SuccessGreen = Color(0xFF22C55E)

private val Light = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E3FF),
    onPrimaryContainer = Color(0xFF1C1356),
    secondary = Color(0xFF0E9F8E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F5EF),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFD9692A),
    tertiaryContainer = Color(0xFFFFE8D9),
    onTertiaryContainer = Color(0xFF4A1F00),
    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF1B1A23),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF1B1A23),
    surfaceVariant = Color(0xFFECE9F7),
    onSurfaceVariant = Color(0xFF5F5C6E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F4FD),
    surfaceContainer = Color(0xFFF1EFFA),
    surfaceContainerHigh = Color(0xFFEBE8F6),
    surfaceContainerHighest = Color(0xFFE5E2F2),
    outline = Color(0xFFC9C5D8),
    outlineVariant = Color(0xFFE3E0EE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB7ABFF),
    onPrimary = Color(0xFF24166B),
    primaryContainer = Color(0xFF3A2E8F),
    onPrimaryContainer = Color(0xFFE7E3FF),
    secondary = Color(0xFF5FD9C7),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0F4A43),
    onSecondaryContainer = Color(0xFFD2F5EF),
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

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 26.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
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
