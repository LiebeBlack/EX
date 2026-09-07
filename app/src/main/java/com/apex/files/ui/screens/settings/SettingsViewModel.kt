package com.apex.files.ui.screens.settings

import androidx.lifecycle.ViewModel
import coil.annotation.ExperimentalCoilApi
import com.apex.files.core.Accent
import com.apex.files.core.AppContainer
import com.apex.files.core.ListDensity
import com.apex.files.data.model.SortDirection
import com.apex.files.data.model.SortOrder
import com.apex.files.data.model.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoilApi::class)
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
    val semanticSearchEnabled: StateFlow<Boolean> = container.settings.semanticSearchEnabled
    val ocrEnabled: StateFlow<Boolean> = container.settings.ocrEnabled
    val smartGroupsEnabled: StateFlow<Boolean> = container.settings.smartGroupsEnabled
    val darkTheme: StateFlow<Boolean> = container.settings.darkTheme
    val fontSize: StateFlow<Int> = container.settings.fontSize
    val showFileSize: StateFlow<Boolean> = container.settings.showFileSize
    val showModifiedDate: StateFlow<Boolean> = container.settings.showModifiedDate
    val defaultSortOrder: StateFlow<SortOrder> = container.settings.defaultSortOrder
    val confirmOverwrite: StateFlow<Boolean> = container.settings.confirmOverwrite
    val showThumbnails: StateFlow<Boolean> = container.settings.showThumbnails

    fun setAccent(accent: Accent) = container.settings.setAccent(accent)

    fun setCustomAccent(hex: Long) = container.settings.setCustomAccent(hex)

    fun setShowHidden(show: Boolean) = container.settings.setShowHidden(show)

    fun setSortOrder(order: SortOrder) = container.settings.setSortOrder(order)

    fun setSortDirection(direction: SortDirection) = container.settings.setSortDirection(direction)

    fun setTrashEnabled(enabled: Boolean) = container.settings.setTrashEnabled(enabled)

    fun setViewMode(mode: ViewMode) = container.settings.setViewMode(mode)

    fun setDensity(density: ListDensity) = container.settings.setDensity(density)

    fun setConfirmPermanentDelete(enabled: Boolean) = container.settings.setConfirmPermanentDelete(enabled)

    fun setSemanticSearchEnabled(enabled: Boolean) = container.settings.setSemanticSearchEnabled(enabled)

    fun setOcrEnabled(enabled: Boolean) = container.settings.setOcrEnabled(enabled)

    fun setSmartGroupsEnabled(enabled: Boolean) = container.settings.setSmartGroupsEnabled(enabled)

    fun setDarkTheme(enabled: Boolean) = container.settings.setDarkTheme(enabled)

    fun setFontSize(size: Int) = container.settings.setFontSize(size)

    fun setShowFileSize(show: Boolean) = container.settings.setShowFileSize(show)

    fun setShowModifiedDate(show: Boolean) = container.settings.setShowModifiedDate(show)

    fun setDefaultSortOrder(order: SortOrder) = container.settings.setDefaultSortOrder(order)

    fun setConfirmOverwrite(enabled: Boolean) = container.settings.setConfirmOverwrite(enabled)

    fun setShowThumbnails(show: Boolean) = container.settings.setShowThumbnails(show)

    fun cycleDefaultSortOrder() {
        val current = defaultSortOrder.value
        val orders = SortOrder.entries
        val currentIndex = orders.indexOf(current)
        val nextIndex = (currentIndex + 1) % orders.size
        setDefaultSortOrder(orders[nextIndex])
    }

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
