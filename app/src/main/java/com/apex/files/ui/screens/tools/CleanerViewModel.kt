package com.apex.files.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.OpProgress
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CleanerViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val scanning: Boolean = false,
        val currentPath: String = "",
        val found: List<FileNode> = emptyList(),
        val selection: Set<String> = emptySet(),
        val done: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.scanning) return
        _state.update {
            it.copy(scanning = true, done = false, found = emptyList(), selection = emptySet(), currentPath = "")
        }
        viewModelScope.launch {
            val root = container.fs.rootNode(Location.Fs(Paths.internalRoot()))
            container.cleaner.scan(root).collect { scan ->
                _state.update {
                    it.copy(
                        currentPath = scan.currentPath,
                        found = if (scan.done) scan.results else it.found,
                        done = scan.done,
                        scanning = !scan.done,
                    )
                }
            }
        }
    }

    fun selectAll() {
        _state.update { it.copy(selection = it.found.map { f -> f.path }.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun toggleSelect(node: FileNode) {
        _state.update { s ->
            val sel = s.selection.toMutableSet()
            if (!sel.add(node.path)) sel.remove(node.path)
            it.copy(selection = sel)
        }
    }

    fun deleteFlow(): Flow<OpProgress> = flow {
        val targets = _state.value.found.filter { it.path in _state.value.selection }
        for (node in targets) container.fs.delete(node) { emit(it) }
    }

    fun reset() {
        _state.update { it.copy(done = false, found = emptyList(), selection = emptySet()) }
    }
}