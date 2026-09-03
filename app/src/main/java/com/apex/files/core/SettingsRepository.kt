package com.apex.files.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Accent presets selectable in Settings. */
enum class Accent(val hex: Long) {
    CYAN(0xFF00E5FF),
    VIOLET(0xFF7C4DFF),
    EMERALD(0xFF00E676),
    AMBER(0xFFFFAB00);

    companion object {
        fun fromName(name: String?): Accent =
            entries.firstOrNull { it.name == name } ?: CYAN
    }
}

/**
 * Thin SharedPreferences wrapper exposing settings as [StateFlow] so the
 * theme and the file browser react instantly. Zero extra dependencies.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("apex_settings", Context.MODE_PRIVATE)

    private val _accent = MutableStateFlow(
        Accent.fromName(prefs.getString(KEY_ACCENT, null))
    )
    val accent: StateFlow<Accent> = _accent.asStateFlow()

    private val _showHidden = MutableStateFlow(prefs.getBoolean(KEY_SHOW_HIDDEN, false))
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    fun setAccent(accent: Accent) {
        prefs.edit().putString(KEY_ACCENT, accent.name).apply()
        _accent.value = accent
    }

    fun setShowHidden(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply()
        _showHidden.value = show
    }

    private companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_SHOW_HIDDEN = "show_hidden"
    }
}