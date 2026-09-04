package com.apex.files.core

import android.content.Context
import android.content.SharedPreferences
import com.apex.files.data.model.SortDirection
import com.apex.files.data.model.SortOrder
import com.apex.files.data.model.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Accent presets selectable in Settings, plus a user-defined color. */
enum class Accent(val hex: Long) {
    CYAN(0xFF00E5FF),
    VIOLET(0xFF7C4DFF),
    EMERALD(0xFF00E676),
    AMBER(0xFFFFAB00),
    /** User color, stored separately in [SettingsRepository.customAccent]. */
    CUSTOM(0xFF00E5FF);

    companion object {
        /** Fixed presets offered by the picker (CUSTOM has its own entry). */
        val PRESETS: List<Accent> = entries.filterNot { it == CUSTOM }

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

    private val _sortDirection = MutableStateFlow(
        SortDirection.fromName(prefs.getString(KEY_SORT_DIRECTION, null))
    )
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    /** Custom accent hex (ARGB, 0xFFRRGGBB) used when [Accent.CUSTOM] is active. */
    private val _customAccent = MutableStateFlow(
        prefs.getLong(KEY_CUSTOM_ACCENT, Accent.CYAN.hex)
    )
    val customAccent: StateFlow<Long> = _customAccent.asStateFlow()

    fun setAccent(accent: Accent) {
        prefs.edit().putString(KEY_ACCENT, accent.name).apply()
        _accent.value = accent
    }

    fun setCustomAccent(hex: Long) {
        prefs.edit().putLong(KEY_CUSTOM_ACCENT, hex).apply()
        _customAccent.value = hex
        prefs.edit().putString(KEY_ACCENT, Accent.CUSTOM.name).apply()
        _accent.value = Accent.CUSTOM
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

    fun setSortDirection(direction: SortDirection) {
        prefs.edit().putString(KEY_SORT_DIRECTION, direction.name).apply()
        _sortDirection.value = direction
    }

    private companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_CUSTOM_ACCENT = "accent_custom"
        const val KEY_SHOW_HIDDEN = "show_hidden"
        const val KEY_SORT = "sort_order"
        const val KEY_SORT_DIRECTION = "sort_direction"
        const val KEY_VIEW_MODE = "view_mode"
    }
}