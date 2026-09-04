package com.apex.files.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.IndexStore
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.storage.DrivesRepository
import com.apex.files.data.storage.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val totalBytes: Long = 0L,
        val usedBytes: Long = 0L,
        val drives: List<DrivesRepository.Volume> = emptyList(),
        val categoryCounts: Map<Category, Int> = emptyMap(),
        /** Smart suggestion: the largest files found in the local index. */
        val largest: List<FileNode> = emptyList(),
        val indexing: Boolean = true,
        val indexingPath: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh(force = false)
        // Drop Favoritos / Recientes entries that point to deleted files.
        viewModelScope.launch {
            container.recents.prune()
            container.favorites.prune()
        }
    }

    /**
     * @param force when true the search index is always re-walked and its
     * snapshot rewritten. Otherwise the persisted snapshot is restored when
     * fresh enough, so opening Home never re-scans the whole storage.
     */
    fun refresh(force: Boolean = true) {
        // Volumes + StatFs usage.
        viewModelScope.launch {
            val (volumes, total, used) = withContext(Dispatchers.IO) {
                val vols = container.drives.volumes()
                var t = 0L
                var u = 0L
                for (v in vols) {
                    v.path?.let { path ->
                        StorageStats.usageOf(path)?.let {
                            t += it.totalBytes
                            u += it.usedBytes
                        }
                    }
                }
                Triple(vols, t, u)
            }
            _state.update { it.copy(totalBytes = total, usedBytes = used, drives = volumes) }
        }

        // Category counts + search index, both served from the disk snapshot
        // when possible instead of re-scanning every volume root.
        viewModelScope.launch {
            _state.update { it.copy(indexing = true) }
            val counts = HashMap<Category, Int>()
            var topLarge: List<FileNode> = emptyList()
            withContext(Dispatchers.IO) {
                ensureIndexLoaded(force)
                // Media categories come from MediaStore (same source as the
                // category screens); docs/archives come from the search index.
                // Never merge both for the same category — that double-counts.
                counts[Category.IMAGE] = container.mediaStore.count(Category.IMAGE)
                counts[Category.VIDEO] = container.mediaStore.count(Category.VIDEO)
                counts[Category.AUDIO] = container.mediaStore.count(Category.AUDIO)
                container.index.countByCategory().forEach { (cat, n) ->
                    if (cat == Category.DOCUMENT || cat == Category.ARCHIVE) {
                        counts.merge(cat, n, Int::plus)
                    }
                }
                // Top-3 largest files as instant “limpieza” suggestions.
                topLarge = container.index.largestFiles(3, minBytes = 0L)
            }
            _state.update {
                it.copy(categoryCounts = counts, largest = topLarge, indexing = false)
            }
        }
    }

    /**
     * Restores the persisted index when present (fast cold start). A full
     * directory walk only happens when: forced by the user, no snapshot
     * exists yet, or the snapshot is older than
     * [IndexStore.AUTO_REINDEX_AFTER_MS].
     */
    private suspend fun ensureIndexLoaded(force: Boolean) {
        val showHidden = container.settings.showHidden.value
        withContext(Dispatchers.IO) {
            val cached = container.indexStore.load()
            when {
                force || cached == null -> {
                    container.index.rebuild(showHidden)
                    container.indexStore.save(container.index.allFiles())
                }
                container.index.size == 0 -> container.index.restore(cached)
                System.currentTimeMillis() - container.indexStore.lastSavedAtMillis() >
                    IndexStore.AUTO_REINDEX_AFTER_MS -> {
                    container.index.rebuild(showHidden)
                    container.indexStore.save(container.index.allFiles())
                }
                else -> Unit
            }
        }
    }
}