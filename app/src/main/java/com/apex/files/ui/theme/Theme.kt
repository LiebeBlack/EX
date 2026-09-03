package com.apex.files.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.apex.files.core.Accent

private fun apexColorScheme(accent: Accent) = darkColorScheme(
    primary = accentColor(accent),
    onPrimary = ApexBlack,
    secondary = ApexViolet,
    onSecondary = ApexBlack,
    tertiary = accentColor(accent),
    onTertiary = ApexBlack,
    error = ApexDanger,
    onError = ApexTextPrimary,
    background = ApexBlack,
    onBackground = ApexTextPrimary,
    surface = ApexSurface1,
    onSurface = ApexTextPrimary,
    surfaceVariant = ApexContainer,
    onSurfaceVariant = ApexTextSecondary,
    outline = ApexBorder,
    outlineVariant = ApexBorder,
    surfaceContainerLowest = ApexBlack,
    surfaceContainerLow = ApexSurface1,
    surfaceContainer = ApexContainer,
    surfaceContainerHigh = ApexContainer,
    surfaceContainerHighest = ApexContainer,
    surfaceBright = ApexContainer,
    surfaceDim = ApexBlack,
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