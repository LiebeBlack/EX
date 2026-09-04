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
import com.apex.files.tools.DuplicateAlgorithm
import com.apex.files.tools.DuplicateFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DuplicatesViewModel(private val container: AppContainer) : ViewModel() {

    private val toolRoots = ToolRoots(container.appContext)

    data class UiState(
        val scanning: Boolean = false,
        val currentPath: String = "",
        val hashed: Int = 0,
        val total: Int = 0,
        val groups: List<DuplicateFinder.DupGroup> = emptyList(),
        val expanded: Set<String> = emptySet(),
        val selection: Set<String> = emptySet(),
        val done: Boolean = false,
        val volumes: List<DrivesRepository.Volume> = emptyList(),
        val rootKey: String = "",
    ) {
        val reclaimable: Long
            get() = groups.sumOf { it.reclaimable }

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
                it.copy(volumes = vols, rootKey = toolRoots.get("duplicates") ?: defaultKey)
            }
        }
    }

    fun setRoot(key: String) {
        toolRoots.set("duplicates", key)
        _state.update {
            it.copy(rootKey = key, done = false, groups = emptyList(), selection = emptySet(), scanning = false)
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
            it.copy(scanning = true, done = false, groups = emptyList(), selection = emptySet(), expanded = emptySet())
        }
        viewModelScope.launch {
            val root = container.fs.rootNode(scanRoot())
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

    /**
     * Selects every duplicate in each group except the newest copy (the
     * typical “keep the newest” intent).
     */
    fun selectDuplicates() {
        _state.update { s ->
            val sel = s.groups
                .flatMap { group -> DuplicateAlgorithm.filesToDelete(group.files).map { it.path } }
                .toSet()
            s.copy(selection = sel)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    private val _deleteSummary = MutableStateFlow<String?>(null)
    val deleteSummary: StateFlow<String?> = _deleteSummary.asStateFlow()

    fun deleteFlow(): Flow<OpProgress> = flow {
        val targets = _state.value.groups
            .flatMap { it.files }
            .filter { it.path in _state.value.selection }
        var acc = OpResult()
        for (node in targets) {
            acc += container.fs.delete(node) { emit(it) }
        }
        val parts = buildList {
            add("Eliminados: ${acc.filesDone} archivo(s)")
            if (acc.errors > 0) add("${acc.errors} errores")
            if (acc.skipped > 0) add("${acc.skipped} omitidos")
        }
        _deleteSummary.value = parts.joinToString(" · ")
    }

    fun consumeDeleteSummary(): String? {
        val v = _deleteSummary.value
        _deleteSummary.value = null
        return v
    }

    fun reset() {
        _state.update { it.copy(done = false, groups = emptyList(), selection = emptySet(), scanning = false) }
    }
}
