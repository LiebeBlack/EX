package com.apex.files.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.OpProgress
import com.apex.files.core.OpType
import com.apex.files.data.fs.ArchiveEntry
import com.apex.files.data.fs.ArchiveRepository
import com.apex.files.data.model.FileNode
import com.apex.files.ui.components.OperationCenterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Virtual folder navigation inside a compressed archive. Entries are listed
 * lazily by prefix; extraction streams only the requested entries.
 */
class ArchiveViewerViewModel(
    private val container: AppContainer,
    val node: FileNode,
) : ViewModel() {

    data class UiState(
        val entries: List<ArchiveEntry> = emptyList(),
        val currentPath: String = "",
        val currentEntries: List<ArchiveEntry> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var handle: ArchiveRepository.Handle? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val h = container.archive.open(node)
                handle = h
                val entries = h.entries()
                _state.update { it.copy(entries = entries, loading = false) }
                recomputeCurrent()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Error", loading = false) }
            }
        }
    }

    fun openDir(entry: ArchiveEntry) {
        _state.update { it.copy(currentPath = entry.name + "/") }
        recomputeCurrent()
    }

    fun goUp() {
        val path = _state.value.currentPath
        if (path.isEmpty()) return
        val parent = path.trimEnd('/').substringBeforeLast('/', "")
        _state.update {
            it.copy(currentPath = if (parent.isEmpty()) "" else "$parent/")
        }
        recomputeCurrent()
    }

    fun canGoUp(): Boolean = _state.value.currentPath.isNotEmpty()

    fun openRoot() {
        _state.update { it.copy(currentPath = "") }
        recomputeCurrent()
    }

    fun extractFlow(entry: ArchiveEntry, destDir: FileNode): Flow<OpProgress> = flow {
        container.archive.extract(entry, node, destDir) { emit(it) }
    }

    private fun recomputeCurrent() {
        val s = _state.value
        val prefix = s.currentPath
        val children = s.entries.filter { e ->
            val name = e.name.trimEnd('/')
            if (prefix.isEmpty()) {
                !name.contains('/')
            } else {
                name.startsWith(prefix) &&
                    name.drop(prefix.length).isNotEmpty() &&
                    !name.drop(prefix.length).contains('/')
            }
        }
        _state.update { it.copy(currentEntries = children) }
    }

    override fun onCleared() {
        handle?.close()
        handle = null
    }
}