package com.apex.files.ui.screens.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.OpProgress
import com.apex.files.data.fs.OpResult
import com.apex.files.tools.JunkAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Limpieza Inteligente: scans for orphan app folders, caches and temp files
 * ([JunkAnalyzer]) and deletes only what the user selects. Deletions respect
 * the Papelera setting exactly like the explorer (File-backed nodes go to the
 * trash when enabled).
 */
class CleanupViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val scanning: Boolean = false,
        val currentPath: String = "",
        val items: List<JunkAnalyzer.JunkItem> = emptyList(),
        val selection: Set<String> = emptySet(),
        val done: Boolean = false,
    ) {
        val selectedBytes: Long
            get() = items.filter { it.path in selection }.sumOf { it.bytes }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _summary = MutableStateFlow<String?>(null)
    val summary: StateFlow<String?> = _summary.asStateFlow()

    fun scan() {
        if (_state.value.scanning) return
        _state.update {
            it.copy(scanning = true, done = false, items = emptyList(), selection = emptySet(), currentPath = "")
        }
        viewModelScope.launch {
            container.junkAnalyzer.scan().collect { scan ->
                _state.update {
                    it.copy(
                        currentPath = scan.currentPath,
                        items = if (scan.done) scan.found else it.items,
                        done = scan.done,
                        scanning = !scan.done,
                    )
                }
            }
        }
    }

    fun selectAll() {
        _state.update { it.copy(selection = it.items.map { i -> i.path }.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun toggleSelect(item: JunkAnalyzer.JunkItem) {
        _state.update { s ->
            val sel = s.selection.toMutableSet()
            if (!sel.add(item.path)) sel.remove(item.path)
            s.copy(selection = sel)
        }
    }

    fun deleteFlow(): Flow<OpProgress> = flow {
        val targets = _state.value.items.filter { it.path in _state.value.selection }
        val trashEnabled = container.settings.trashEnabled.value
        val (toTrash, toDelete) = targets.partition { trashEnabled && it.node.uri == null }
        var acc = OpResult()
        for (item in toTrash) {
            acc += container.trash.trash(item.node)
        }
        for (item in toDelete) {
            acc += container.fs.delete(item.node) { emit(it) }
        }
        val parts = buildList {
            add("Eliminados: ${acc.filesDone}")
            if (acc.errors > 0) add("${acc.errors} errores")
            if (acc.skipped > 0) add("${acc.skipped} omitidos")
        }
        _summary.value = parts.joinToString(" · ")
    }

    fun consumeSummary(): String? {
        val v = _summary.value
        _summary.value = null
        return v
    }

    fun reset() {
        _state.update { it.copy(done = false, items = emptyList(), selection = emptySet()) }
    }
}