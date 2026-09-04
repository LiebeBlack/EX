package com.apex.files.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.apex.files.core.Accent

import androidx.compose.ui.graphics.Color

private fun apexColorScheme(accent: Accent, customHex: Long) = darkColorScheme(
    primary = accentColor(accent, customHex),
    onPrimary = onAccentColor(accent, customHex),
    secondary = ApexViolet,
    onSecondary = Color.White,
    tertiary = accentColor(accent, customHex),
    onTertiary = onAccentColor(accent, customHex),
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
    surfaceTint = accentColor(accent, customHex),
    inverseSurface = ApexTextPrimary,
    inverseOnSurface = ApexBlack,
    inversePrimary = accentColor(accent, customHex),
)

/**
 * The single app theme: pitch-black OLED background, 1dp borders, dynamic
 * neon accent (preset or user color), and zero elevation everywhere.
 */
@Composable
fun ApexTheme(
    accent: Accent,
    customAccentHex: Long = Accent.CYAN.hex,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = apexColorScheme(accent, customAccentHex),
        typography = ApexTypography,
        shapes = ApexShapes,
        content = content,
    )
}