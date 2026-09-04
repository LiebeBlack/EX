package com.apex.files.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.apex.files.core.Accent

// ---- Exact OLED palette (spec) ----
val ApexBlack = Color(0xFF000000)          // Background principal — píxeles apagados
val ApexSurface1 = Color(0xFF08080D)       // Superficie 1
val ApexContainer = Color(0xFF101018)      // Contenedores estándar
val ApexContainerHigh = Color(0xFF161622)  // Contenedores elevados (diálogos, hojas)
val ApexContainerHighest = Color(0xFF1D1D2C) // Contenedores superiores / badges
val ApexBorder = Color(0xFF222230)         // Borde ultrafino 1dp
val ApexBorderSubtle = Color(0xFF161622)   // Borde muy sutil para separadores
val ApexBorderFocused = Color(0xFF38384E)  // Borde para elementos enfocados

// ---- Accent colors ----
val ApexCyan = Color(0xFF00E5FF)           // Acento primario (cian neón)
val ApexViolet = Color(0xFF7C4DFF)         // Acento secundario (violeta profundo)
val ApexEmerald = Color(0xFF00E676)        // Acento alternativo (esmeralda neón)
val ApexAmber = Color(0xFFFFAB00)          // Acento alternativo (ámbar neón)
val ApexDanger = Color(0xFFFF2A6D)         // Alerta / peligro
val ApexSuccess = Color(0xFF00E676)        // Éxito / completado
val ApexWarning = Color(0xFFFFAB00)        // Advertencia
val ApexInfo = Color(0xFF00E5FF)           // Información

// ---- Text colors ----
val ApexTextPrimary = Color(0xFFFFFFFF)    // Blanco puro para títulos y texto principal
val ApexTextSecondary = Color(0xFFA6A6B2)  // Gris claro con alto contraste para subtítulos
val ApexTextMuted = Color(0xFF7E7E8C)      // Gris medio para pistas y metadatos secundarios
val ApexTextDisabled = Color(0xFF52525E)   // Gris oscuro para estados deshabilitados

/** Extra curated colors for the “Personalizado” accent picker. */
val ApexCustomAccentPalette: List<Long> = listOf(
    0xFF00E5FF, // cian
    0xFF00B8FF, // azul neón
    0xFF2979FF, // azul eléctrico
    0xFF7C4DFF, // violeta
    0xFFB388FF, // lavanda
    0xFFE040FB, // magenta
    0xFFFF2A6D, // rosa neón
    0xFFFF3D00, // naranja
    0xFFFFAB00, // ámbar
    0xFF00E676, // esmeralda
    0xFF00C853, // verde
    0xFFFFF176, // limón
)

/** Effective ARGB hex for the active accent. */
fun accentHex(accent: Accent, customHex: Long = Accent.CYAN.hex): Long =
    if (accent == Accent.CUSTOM) customHex else accent.hex

/** Maps the selected accent (preset or custom) to its Color. */
fun accentColor(accent: Accent, customHex: Long = Accent.CYAN.hex): Color =
    Color(accentHex(accent, customHex))

/**
 * Provides a high-contrast foreground for content placed directly on the
 * accent color. Presets keep their curated pairing; custom colors pick
 * white/black by luminance.
 */
fun onAccentColor(accent: Accent, customHex: Long = Accent.CYAN.hex): Color {
    if (accent == Accent.CUSTOM) {
        return if (accentColor(accent, customHex).luminance() > 0.55f) ApexBlack else Color.White
    }
    return when (accent) {
        Accent.VIOLET -> Color.White
        Accent.CYAN -> ApexBlack
        Accent.EMERALD -> ApexBlack
        Accent.AMBER -> ApexBlack
        Accent.CUSTOM -> Color.White
    }
}