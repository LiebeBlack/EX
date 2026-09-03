package com.apex.files.core

import android.content.Context
import android.content.SharedPreferences
import com.apex.files.data.model.SortOrder
import com.apex.files.data.model.ViewMode
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

    private val _sortOrder = MutableStateFlow(
        SortOrder.fromName(prefs.getString(KEY_SORT, null))
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _viewMode = MutableStateFlow(
        ViewMode.fromName(prefs.getString(KEY_VIEW_MODE, null))
    )
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    fun setAccent(accent: Accent) {
        prefs.edit().putString(KEY_ACCENT, accent.name).apply()
        _accent.value = accent
    }

    fun setShowHidden(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply()
        _showHidden.value = show
    }

    fun setSortOrder(order: SortOrder) {
        prefs.edit().putString(KEY_SORT, order.name).apply()
        _sortOrder.value = order
    }

    fun setViewMode(mode: ViewMode) {
        prefs.edit().putString(KEY_VIEW_MODE, mode.name).apply()
        _viewMode.value = mode
    }

    private companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_SHOW_HIDDEN = "show_hidden"
        const val KEY_SORT = "sort_order"
        const val KEY_VIEW_MODE = "view_mode"
    }
}