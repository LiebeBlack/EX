package com.apex.files.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.OpProgress
import com.apex.files.data.fs.OpResult
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import com.apex.files.data.storage.DrivesRepository
import com.apex.files.data.storage.ToolRoots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CleanerViewModel(private val container: AppContainer) : ViewModel() {

    private val toolRoots = ToolRoots(container.appContext)

    data class UiState(
        val scanning: Boolean = false,
        val currentPath: String = "",
        val found: List<FileNode> = emptyList(),
        val selection: Set<String> = emptySet(),
        val done: Boolean = false,
        val volumes: List<DrivesRepository.Volume> = emptyList(),
        val rootKey: String = "",
    ) {
        val rootName: String
            get() = volumes.firstOrNull { it.key == rootKey }?.name ?: "Almacenamiento interno"
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadVolumes()
    }

    private fun loadVolumes() {
        viewModelScope.launch {
            val vols = withContext(Dispatchers.IO) { container.drives.volumes() }
            val defaultKey = vols.firstOrNull { !it.removable }?.key ?: "fs:${Paths.internalRoot().absolutePath}"
            _state.update {
                it.copy(volumes = vols, rootKey = toolRoots.get("cleaner") ?: defaultKey)
            }
        }
    }

    /** Persists the chosen root and returns the tool to its idle state. */
    fun setRoot(key: String) {
        toolRoots.set("cleaner", key)
        _state.update {
            it.copy(rootKey = key, done = false, found = emptyList(), selection = emptySet(), scanning = false)
        }
    }

    private fun scanRoot(): Location {
        val s = _state.value
        return s.volumes.firstOrNull { it.key == s.rootKey }?.location
            ?: Location.Fs(Paths.internalRoot())
    }

    fun scan() {
        if (_state.value.scanning) return
        _state.update {
            it.copy(scanning = true, done = false, found = emptyList(), selection = emptySet(), currentPath = "")
        }
        viewModelScope.launch {
            val root = container.fs.rootNode(scanRoot())
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
            s.copy(selection = sel)
        }
    }

    private val _deleteSummary = MutableStateFlow<String?>(null)
    val deleteSummary: StateFlow<String?> = _deleteSummary.asStateFlow()

    fun deleteFlow(): Flow<OpProgress> = flow {
        val targets = _state.value.found.filter { it.path in _state.value.selection }
        var acc = OpResult()
        for (node in targets) {
            acc += container.fs.delete(node) { emit(it) }
        }
        val parts = buildList {
            add("Eliminadas: ${acc.filesDone} carpeta(s)")
            if (acc.errors > 0) add("${acc.errors} errores")
            if (acc.skipped > 0) add("${acc.skipped} omitidas")
        }
        _deleteSummary.value = parts.joinToString(" · ")
    }

    fun consumeDeleteSummary(): String? {
        val v = _deleteSummary.value
        _deleteSummary.value = null
        return v
    }

    fun reset() {
        _state.update { it.copy(done = false, found = emptyList(), selection = emptySet()) }
    }
}
