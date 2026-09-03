package com.apex.files.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.OpProgress
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import com.apex.files.tools.DuplicateFinder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DuplicatesViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val scanning: Boolean = false,
        val currentPath: String = "",
        val hashed: Int = 0,
        val total: Int = 0,
        val groups: List<DuplicateFinder.DupGroup> = emptyList(),
        val expanded: Set<String> = emptySet(),
        val selection: Set<String> = emptySet(),
        val done: Boolean = false,
    ) {
        val reclaimable: Long
            get() = groups.sumOf { it.reclaimable }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.scanning) return
        _state.update {
            it.copy(scanning = true, done = false, groups = emptyList(), selection = emptySet(), expanded = emptySet())
        }
        viewModelScope.launch {
            val root = container.fs.rootNode(Location.Fs(Paths.internalRoot()))
            container.duplicateFinder.find(root).collect { scan ->
                _state.update {
                    it.copy(
                        currentPath = scan.currentPath,
                        hashed = scan.hashed,
                        total = scan.totalCandidates,
                        groups = if (scan.done) scan.groups else it.groups,
                        done = scan.done,
                        scanning = !scan.done,
                    )
                }
            }
        }
    }

    fun toggleExpand(group: DuplicateFinder.DupGroup) {
        _state.update { s ->
            val set = s.expanded.toMutableSet()
            val key = group.hash
            if (!set.add(key)) set.remove(key)
            s.copy(expanded = set)
        }
    }

    fun toggleSelect(path: String) {
        _state.update { s ->
            val sel = s.selection.toMutableSet()
            if (!sel.add(path)) sel.remove(path)
            s.copy(selection = sel)
        }
    }

    /** Selects every duplicate except the first (kept) in each group. */
    fun selectDuplicates() {
        _state.update { s ->
            val sel = s.groups.flatMap { it.files.drop(1).map { f -> f.path } }.toSet()
            s.copy(selection = sel)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun deleteFlow(): Flow<OpProgress> = flow {
        val targets = _state.value.groups
            .flatMap { it.files }
            .filter { it.path in _state.value.selection }
        for (node in targets) container.fs.delete(node) { emit(it) }
    }

    fun reset() {
        _state.update { it.copy(done = false, groups = emptyList(), selection = emptySet(), scanning = false) }
    }
}