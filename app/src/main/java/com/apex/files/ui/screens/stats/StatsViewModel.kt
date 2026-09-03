package com.apex.files.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Per-category byte/count totals. */
data class CategoryStat(
    val category: Category,
    val count: Int,
    val bytes: Long,
)

/** Snapshot of storage statistics computed from the search index. */
data class StorageStats(
    val fileCount: Int,
    val totalBytes: Long,
    val byCategory: List<CategoryStat>,
    val topLargest: List<FileNode>,
    val topExtensions: List<Pair<String, Int>>,
)

class StatsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val computing: Boolean = true,
        val stats: StorageStats? = null,
        val empty: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(computing = true, empty = false) }
        viewModelScope.launch(Dispatchers.Default) {
            val files = container.index.allFiles()
            if (files.isEmpty()) {
                _state.update { it.copy(computing = false, empty = true, stats = null) }
                return@launch
            }
            val total = files.sumOf { it.size.coerceAtLeast(0L) }
            val perCategory = HashMap<Category, LongArray>() // [count, bytes]
            for (f in files) {
                val cell = perCategory.getOrPut(f.category) { LongArray(2) }
                cell[0]++
                cell[1] += f.size.coerceAtLeast(0L)
            }
            val byCategory = perCategory.entries
                .map { (cat, cell) -> CategoryStat(cat, cell[0].toInt(), cell[1]) }
                .sortedByDescending { it.bytes }
            val topLargest = files
                .sortedByDescending { it.size }
                .take(12)
            val extCounts = HashMap<String, Int>()
            for (f in files) {
                if (f.extension.isNotEmpty()) {
                    extCounts.merge(f.extension.lowercase(), 1, Int::plus)
                }
            }
            val topExtensions = extCounts.entries
                .sortedByDescending { it.value }
                .take(8)
                .map { it.key to it.value }
            _state.update {
                it.copy(
                    computing = false,
                    stats = StorageStats(
                        fileCount = files.size,
                        totalBytes = total,
                        byCategory = byCategory,
                        topLargest = topLargest,
                        topExtensions = topExtensions,
                    ),
                )
            }
        }
    }
}