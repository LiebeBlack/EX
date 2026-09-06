package com.apex.files.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.apex.files.core.Accent
import com.apex.files.core.AppContainer
import com.apex.files.core.ListDensity
import com.apex.files.data.model.SortDirection
import com.apex.files.data.model.SortOrder
import com.apex.files.data.model.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val accent: StateFlow<Accent> = container.settings.accent
    val customAccent: StateFlow<Long> = container.settings.customAccent
    val showHidden: StateFlow<Boolean> = container.settings.showHidden
    val sortOrder: StateFlow<SortOrder> = container.settings.sortOrder
    val sortDirection: StateFlow<SortDirection> = container.settings.sortDirection
    val trashEnabled: StateFlow<Boolean> = container.settings.trashEnabled
    val viewMode: StateFlow<ViewMode> = container.settings.viewMode
    val density: StateFlow<ListDensity> = container.settings.density
    val confirmPermanentDelete: StateFlow<Boolean> = container.settings.confirmPermanentDelete

    fun setAccent(accent: Accent) = container.settings.setAccent(accent)

    fun setCustomAccent(hex: Long) = container.settings.setCustomAccent(hex)

    fun setShowHidden(show: Boolean) = container.settings.setShowHidden(show)

    fun setSortOrder(order: SortOrder) = container.settings.setSortOrder(order)

    fun setSortDirection(direction: SortDirection) = container.settings.setSortDirection(direction)

    fun setTrashEnabled(enabled: Boolean) = container.settings.setTrashEnabled(enabled)

    fun setViewMode(mode: ViewMode) = container.settings.setViewMode(mode)

    fun setDensity(density: ListDensity) = container.settings.setDensity(density)

    fun setConfirmPermanentDelete(enabled: Boolean) = container.settings.setConfirmPermanentDelete(enabled)

    fun resetSettings() = container.settings.resetAll()

    // --------------------------------------------------------- cache manager

    /** Coil thumbnail cache size in bytes (0 when unavailable or on failure). */
    suspend fun thumbnailCacheBytes(): Long = withContext(Dispatchers.IO) {
        runCatching { container.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L)
    }

    /** Wipes the thumbnail caches (disk + memory). */
    suspend fun clearThumbnailCache() = withContext(Dispatchers.IO) {
        runCatching { container.imageLoader.diskCache?.clear() }
        runCatching { container.imageLoader.memoryCache?.clear() }
    }
}