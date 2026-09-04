package com.apex.files.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.apex.files.core.Accent

import androidx.compose.ui.graphics.Color

private fun apexColorScheme(accent: Accent) = darkColorScheme(
    primary = accentColor(accent),
    onPrimary = onAccentColor(accent),
    secondary = ApexViolet,
    onSecondary = Color.White,
    tertiary = accentColor(accent),
    onTertiary = onAccentColor(accent),
    error = ApexDanger,
    onError = Color.White,
    background = ApexBlack,
    onBackground = ApexTextPrimary,
    surface = ApexSurface1,
    onSurface = ApexTextPrimary,
    surfaceVariant = ApexContainer,
    onSurfaceVariant = ApexTextSecondary,
    outline = ApexBorder,
    outlineVariant = ApexBorderSubtle,
    surfaceContainerLowest = ApexBlack,
    surfaceContainerLow = ApexSurface1,
    surfaceContainer = ApexContainer,
    surfaceContainerHigh = ApexContainerHigh,
    surfaceContainerHighest = ApexContainerHighest,
    surfaceBright = ApexContainerHigh,
    surfaceDim = ApexBlack,
    surfaceTint = accentColor(accent),
    inverseSurface = ApexTextPrimary,
    inverseOnSurface = ApexBlack,
    inversePrimary = accentColor(accent),
)

/**
 * The single app theme: pitch-black OLED background, 1dp borders, dynamic
 * neon accent, and zero elevation everywhere.
 */
@Composable
fun ApexTheme(accent: Accent, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = apexColorScheme(accent),
        typography = ApexTypography,
        shapes = ApexShapes,
        content = content,
    )
}