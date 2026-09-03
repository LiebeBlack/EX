package com.apex.files.ui.theme

import androidx.compose.ui.graphics.Color
import com.apex.files.core.Accent

// ---- Exact OLED palette (spec) ----
val ApexBlack = Color(0xFF000000)          // Background principal — píxeles apagados
val ApexSurface1 = Color(0xFF070709)       // Superficie 1
val ApexContainer = Color(0xFF0F0F14)      // Contenedores
val ApexBorder = Color(0xFF1E1E28)         // Borde ultrafino 1dp
val ApexCyan = Color(0xFF00E5FF)           // Acento primario
val ApexViolet = Color(0xFF7C4DFF)         // Acento secundario
val ApexEmerald = Color(0xFF00E676)        // Acento alternativo
val ApexAmber = Color(0xFFFFAB00)          // Acento alternativo
val ApexDanger = Color(0xFFFF2A6D)         // Alerta / peligro
val ApexTextPrimary = Color(0xFFFFFFFF)
val ApexTextSecondary = Color(0xFF8E8E93)
val ApexTextMuted = Color(0xFF75757C)

/** Maps the selected accent preset to its Color. */
fun accentColor(accent: Accent): Color = when (accent) {
    Accent.CYAN -> ApexCyan
    Accent.VIOLET -> ApexViolet
    Accent.EMERALD -> ApexEmerald
    Accent.AMBER -> ApexAmber
}