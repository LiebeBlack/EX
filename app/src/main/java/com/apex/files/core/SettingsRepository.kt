package com.apex.files.core

import android.content.Context
import android.content.SharedPreferences
import com.apex.files.data.model.SortDirection
import com.apex.files.data.model.SortOrder
import com.apex.files.data.model.ViewMode
import com.apex.files.ui.screens.home.HomeConfig
import com.apex.files.ui.screens.home.HomeSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

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
 * Now uses EncryptedSharedPreferences for secure storage of sensitive data.
 */
class SettingsRepository(
    context: Context,
    encryptedPrefs: SharedPreferences
) {

    private val prefs: SharedPreferences = encryptedPrefs

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

    /** Theme preference: Dark/Light (affects OLED black background). */
    private val _darkTheme = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME, true))
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    /** Font size preference for text viewers. */
    private val _fontSize = MutableStateFlow(prefs.getInt(KEY_FONT_SIZE, 14))
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    /** Show file size in directory listing. */
    private val _showFileSize = MutableStateFlow(prefs.getBoolean(KEY_SHOW_FILE_SIZE, true))
    val showFileSize: StateFlow<Boolean> = _showFileSize.asStateFlow()

    /** Show last modified date in directory listing. */
    private val _showModifiedDate = MutableStateFlow(prefs.getBoolean(KEY_SHOW_MODIFIED_DATE, false))
    val showModifiedDate: StateFlow<Boolean> = _showModifiedDate.asStateFlow()

    /** Default sort order for new directories. */
    private val _defaultSortOrder = MutableStateFlow(
        SortOrder.fromName(prefs.getString(KEY_DEFAULT_SORT, null))
    )
    val defaultSortOrder: StateFlow<SortOrder> = _defaultSortOrder.asStateFlow()

    /** Whether to confirm before overwriting files. */
    private val _confirmOverwrite = MutableStateFlow(prefs.getBoolean(KEY_CONFIRM_OVERWRITE, true))
    val confirmOverwrite: StateFlow<Boolean> = _confirmOverwrite.asStateFlow()

    /** Show thumbnails in grid view. */
    private val _showThumbnails = MutableStateFlow(prefs.getBoolean(KEY_SHOW_THUMBNAILS, true))
    val showThumbnails: StateFlow<Boolean> = _showThumbnails.asStateFlow()

    /** Cache size limit for thumbnails (in MB). */
    private val _thumbnailCacheSize = MutableStateFlow(prefs.getInt(KEY_THUMBNAIL_CACHE_SIZE, 64))
    val thumbnailCacheSize: StateFlow<Int> = _thumbnailCacheSize.asStateFlow()

    /** Enable logging for debugging. */
    private val _debugLogging = MutableStateFlow(prefs.getBoolean(KEY_DEBUG_LOGGING, false))
    val debugLogging: StateFlow<Boolean> = _debugLogging.asStateFlow()

    /** Home screen section order (comma-separated section IDs). */
    private val _homeSectionOrder = MutableStateFlow(
        prefs.getString(KEY_HOME_SECTION_ORDER, DEFAULT_HOME_SECTION_ORDER) ?: DEFAULT_HOME_SECTION_ORDER
    )
    val homeSectionOrder: StateFlow<String> = _homeSectionOrder.asStateFlow()

    /** Home screen section visibility (comma-separated section IDs). */
    private val _homeSectionVisibility = MutableStateFlow(
        prefs.getString(KEY_HOME_SECTION_VISIBILITY, HomeSection.entries.joinToString(",") { it.id }) ?: 
        HomeSection.entries.joinToString(",") { it.id }
    )
    val homeSectionVisibility: StateFlow<String> = _homeSectionVisibility.asStateFlow()

    /** Home configuration object combining order and visibility. */
    val homeConfig: StateFlow<HomeConfig> = 
        kotlinx.coroutines.flow.combine(_homeSectionOrder, _homeSectionVisibility) { order, visibility ->
            HomeConfig.fromString(order, visibility)
        }.asStateFlow()

    /** Whether to show each home section (legacy boolean properties for compatibility). */
    private val _showStorageHero = MutableStateFlow(prefs.getBoolean(KEY_SHOW_STORAGE_HERO, true))
    val showStorageHero: StateFlow<Boolean> = _showStorageHero.asStateFlow()

    private val _showQuickTools = MutableStateFlow(prefs.getBoolean(KEY_SHOW_QUICK_TOOLS, true))
    val showQuickTools: StateFlow<Boolean> = _showQuickTools.asStateFlow()

    private val _showSuggestions = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SUGGESTIONS, true))
    val showSuggestions: StateFlow<Boolean> = _showSuggestions.asStateFlow()

    private val _showCategories = MutableStateFlow(prefs.getBoolean(KEY_SHOW_CATEGORIES, true))
    val showCategories: StateFlow<Boolean> = _showCategories.asStateFlow()

    private val _showFavorites = MutableStateFlow(prefs.getBoolean(KEY_SHOW_FAVORITES, true))
    val showFavorites: StateFlow<Boolean> = _showFavorites.asStateFlow()

    private val _showRecents = MutableStateFlow(prefs.getBoolean(KEY_SHOW_RECENTS, true))
    val showRecents: StateFlow<Boolean> = _showRecents.asStateFlow()

    private val _showDrives = MutableStateFlow(prefs.getBoolean(KEY_SHOW_DRIVES, true))
    val showDrives: StateFlow<Boolean> = _showDrives.asStateFlow()

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

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
        _darkTheme.value = enabled
    }

    fun setFontSize(size: Int) {
        prefs.edit().putInt(KEY_FONT_SIZE, size).apply()
        _fontSize.value = size
    }

    fun setShowFileSize(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FILE_SIZE, show).apply()
        _showFileSize.value = show
    }

    fun setShowModifiedDate(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_MODIFIED_DATE, show).apply()
        _showModifiedDate.value = show
    }

    fun setDefaultSortOrder(order: SortOrder) {
        prefs.edit().putString(KEY_DEFAULT_SORT, order.name).apply()
        _defaultSortOrder.value = order
    }

    fun setConfirmOverwrite(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_OVERWRITE, enabled).apply()
        _confirmOverwrite.value = enabled
    }

    fun setShowThumbnails(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_THUMBNAILS, show).apply()
        _showThumbnails.value = show
    }

    fun setThumbnailCacheSize(sizeMb: Int) {
        prefs.edit().putInt(KEY_THUMBNAIL_CACHE_SIZE, sizeMb).apply()
        _thumbnailCacheSize.value = sizeMb
    }

    fun setDebugLogging(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_LOGGING, enabled).apply()
        _debugLogging.value = enabled
    }

    fun setHomeSectionOrder(order: String) {
        prefs.edit().putString(KEY_HOME_SECTION_ORDER, order).apply()
        _homeSectionOrder.value = order
    }

    fun setShowStorageHero(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_STORAGE_HERO, show).apply()
        _showStorageHero.value = show
    }

    fun setShowQuickTools(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_QUICK_TOOLS, show).apply()
        _showQuickTools.value = show
    }

    fun setShowSuggestions(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SUGGESTIONS, show).apply()
        _showSuggestions.value = show
    }

    fun setShowCategories(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CATEGORIES, show).apply()
        _showCategories.value = show
    }

    fun setShowFavorites(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FAVORITES, show).apply()
        _showFavorites.value = show
    }

    fun setShowRecents(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_RECENTS, show).apply()
        _showRecents.value = show
    }

    fun setShowDrives(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DRIVES, show).apply()
        _showDrives.value = show
    }

    fun setHomeConfig(config: HomeConfig) {
        prefs.edit().putString(KEY_HOME_SECTION_ORDER, config.toOrderString()).apply()
        prefs.edit().putString(KEY_HOME_SECTION_VISIBILITY, config.toVisibilityString()).apply()
        _homeSectionOrder.value = config.toOrderString()
        _homeSectionVisibility.value = config.toVisibilityString()
        
        // Update legacy boolean properties for compatibility
        _showStorageHero.value = config.isSectionVisible(HomeSection.STORAGE_HERO)
        _showQuickTools.value = config.isSectionVisible(HomeSection.QUICK_TOOLS)
        _showSuggestions.value = config.isSectionVisible(HomeSection.SUGGESTIONS)
        _showCategories.value = config.isSectionVisible(HomeSection.CATEGORIES)
        _showFavorites.value = config.isSectionVisible(HomeSection.FAVORITES)
        _showRecents.value = config.isSectionVisible(HomeSection.RECENTS)
        _showDrives.value = config.isSectionVisible(HomeSection.DRIVES)
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
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_SHOW_FILE_SIZE = "show_file_size"
        const val KEY_SHOW_MODIFIED_DATE = "show_modified_date"
        const val KEY_DEFAULT_SORT = "default_sort"
        const val KEY_CONFIRM_OVERWRITE = "confirm_overwrite"
        const val KEY_SHOW_THUMBNAILS = "show_thumbnails"
        const val KEY_THUMBNAIL_CACHE_SIZE = "thumbnail_cache_size"
        const val KEY_DEBUG_LOGGING = "debug_logging"
        const val KEY_HOME_SECTION_ORDER = "home_section_order"
        const val KEY_HOME_SECTION_VISIBILITY = "home_section_visibility"
        const val KEY_SHOW_STORAGE_HERO = "show_storage_hero"
        const val KEY_SHOW_QUICK_TOOLS = "show_quick_tools"
        const val KEY_SHOW_SUGGESTIONS = "show_suggestions"
        const val KEY_SHOW_CATEGORIES = "show_categories"
        const val KEY_SHOW_FAVORITES = "show_favorites"
        const val KEY_SHOW_RECENTS = "show_recents"
        const val KEY_SHOW_DRIVES = "show_drives"
        
        const val DEFAULT_HOME_SECTION_ORDER = "storage_hero,quick_tools,suggestions,categories,favorites,recents,drives"
    }
}