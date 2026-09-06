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

/** Row density for the explorer list and grid. */
enum class ListDensity {
    COMPACT,
    NORMAL,
    COMFORTABLE;

    companion object {
        fun fromName(name: String?): ListDensity = entries.firstOrNull { it.name == name } ?: NORMAL
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

    /** Whether deletions are sent to the Papelera instead of deleted forever. */
    private val _trashEnabled = MutableStateFlow(prefs.getBoolean(KEY_TRASH_ENABLED, true))
    val trashEnabled: StateFlow<Boolean> = _trashEnabled.asStateFlow()

    /** Explorer row density (compact / normal / comfortable). */
    private val _density = MutableStateFlow(
        ListDensity.fromName(prefs.getString(KEY_DENSITY, null))
    )
    val density: StateFlow<ListDensity> = _density.asStateFlow()

    /** Ask for confirmation before permanent deletes (incl. emptying trash). */
    private val _confirmPermanentDelete = MutableStateFlow(prefs.getBoolean(KEY_CONFIRM_PERMANENT_DELETE, true))
    val confirmPermanentDelete: StateFlow<Boolean> = _confirmPermanentDelete.asStateFlow()

    /**
     * Master switch of the optional semantic module: on-device OCR indexing,
     * natural-language search, smart groups and the junk analyzer. OFF by
     * default so the extra APK weight and background indexing stay opt-in.
     */
    private val _semanticSearchEnabled = MutableStateFlow(prefs.getBoolean(KEY_SEMANTIC_SEARCH, false))
    val semanticSearchEnabled: StateFlow<Boolean> = _semanticSearchEnabled.asStateFlow()

    /** Sub-switch: index image/PDF text (OCR) when the master is enabled. */
    private val _ocrEnabled = MutableStateFlow(prefs.getBoolean(KEY_OCR_ENABLED, true))
    val ocrEnabled: StateFlow<Boolean> = _ocrEnabled.asStateFlow()

    /** Sub-switch: "Carpetas inteligentes" row and screen on Home. */
    private val _smartGroupsEnabled = MutableStateFlow(prefs.getBoolean(KEY_SMART_GROUPS, true))
    val smartGroupsEnabled: StateFlow<Boolean> = _smartGroupsEnabled.asStateFlow()

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

    fun setTrashEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRASH_ENABLED, enabled).apply()
        _trashEnabled.value = enabled
    }

    fun setDensity(density: ListDensity) {
        prefs.edit().putString(KEY_DENSITY, density.name).apply()
        _density.value = density
    }

    fun setConfirmPermanentDelete(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_PERMANENT_DELETE, enabled).apply()
        _confirmPermanentDelete.value = enabled
    }

    fun setSemanticSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEMANTIC_SEARCH, enabled).apply()
        _semanticSearchEnabled.value = enabled
    }

    fun setOcrEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OCR_ENABLED, enabled).apply()
        _ocrEnabled.value = enabled
    }

    fun setSmartGroupsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMART_GROUPS, enabled).apply()
        _smartGroupsEnabled.value = enabled
    }

    /** Restores every setting to its default value. */
    fun resetAll() {
        val edit = prefs.edit()
        edit.remove(KEY_ACCENT)
        edit.remove(KEY_CUSTOM_ACCENT)
        edit.remove(KEY_SHOW_HIDDEN)
        edit.remove(KEY_SORT)
        edit.remove(KEY_SORT_DIRECTION)
        edit.remove(KEY_VIEW_MODE)
        edit.remove(KEY_TRASH_ENABLED)
        edit.remove(KEY_DENSITY)
        edit.remove(KEY_CONFIRM_PERMANENT_DELETE)
        edit.remove(KEY_SEMANTIC_SEARCH)
        edit.remove(KEY_OCR_ENABLED)
        edit.remove(KEY_SMART_GROUPS)
        edit.apply()
        // Reflect the defaults in memory immediately.
        _accent.value = Accent.fromName(prefs.getString(KEY_ACCENT, null))
        _customAccent.value = prefs.getLong(KEY_CUSTOM_ACCENT, Accent.CYAN.hex)
        _showHidden.value = prefs.getBoolean(KEY_SHOW_HIDDEN, false)
        _sortOrder.value = SortOrder.fromName(prefs.getString(KEY_SORT, null))
        _sortDirection.value = SortDirection.fromName(prefs.getString(KEY_SORT_DIRECTION, null))
        _viewMode.value = ViewMode.fromName(prefs.getString(KEY_VIEW_MODE, null))
        _trashEnabled.value = prefs.getBoolean(KEY_TRASH_ENABLED, true)
        _density.value = ListDensity.fromName(prefs.getString(KEY_DENSITY, null))
        _confirmPermanentDelete.value = prefs.getBoolean(KEY_CONFIRM_PERMANENT_DELETE, true)
        _semanticSearchEnabled.value = prefs.getBoolean(KEY_SEMANTIC_SEARCH, false)
        _ocrEnabled.value = prefs.getBoolean(KEY_OCR_ENABLED, true)
        _smartGroupsEnabled.value = prefs.getBoolean(KEY_SMART_GROUPS, true)
    }

    private companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_CUSTOM_ACCENT = "accent_custom"
        const val KEY_SHOW_HIDDEN = "show_hidden"
        const val KEY_SORT = "sort_order"
        const val KEY_SORT_DIRECTION = "sort_direction"
        const val KEY_VIEW_MODE = "view_mode"
        const val KEY_TRASH_ENABLED = "trash_enabled"
        const val KEY_DENSITY = "list_density"
        const val KEY_CONFIRM_PERMANENT_DELETE = "confirm_permanent_delete"
        const val KEY_SEMANTIC_SEARCH = "semantic_search_enabled"
        const val KEY_OCR_ENABLED = "ocr_enabled"
        const val KEY_SMART_GROUPS = "smart_groups_enabled"
    }
}