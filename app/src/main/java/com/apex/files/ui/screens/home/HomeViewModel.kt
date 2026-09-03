package com.apex.files.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.model.Category
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
        val indexing: Boolean = true,
        val indexingPath: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
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

        // MediaStore counts (fast) + docs/archives from the index.
        viewModelScope.launch {
            val counts = HashMap<Category, Int>()
            counts[Category.IMAGE] = container.mediaStore.count(Category.IMAGE)
            counts[Category.VIDEO] = container.mediaStore.count(Category.VIDEO)
            counts[Category.AUDIO] = container.mediaStore.count(Category.AUDIO)
            container.index.countByCategory().forEach { (cat, n) ->
                counts.merge(cat, n, Int::plus)
            }
            _state.update { it.copy(categoryCounts = counts, indexing = false) }
        }

        // Rebuild the in-memory index asynchronously after the first frame.
        viewModelScope.launch {
            _state.update { it.copy(indexing = true) }
            withContext(Dispatchers.IO) {
                container.index.rebuild(container.settings.showHidden.value)
            }
            val counts = container.index.countByCategory()
            _state.update {
                it.copy(
                    categoryCounts = it.categoryCounts + counts,
                    indexing = false,
                )
            }
        }
    }
}