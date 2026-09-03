package com.apex.files.ui.screens.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.HashAlgorithm
import com.apex.files.core.HashUtil
import com.apex.files.core.OpProgress
import com.apex.files.data.fs.CountResult
import com.apex.files.data.fs.FileKinds
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import com.apex.files.data.model.SortOrder
import com.apex.files.data.model.ViewMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExplorerViewModel(
    private val container: AppContainer,
    val location: Location,
) : ViewModel() {

    enum class DestMode { COPY, MOVE }

    data class PropertiesState(
        val node: FileNode,
        val mime: String,
        val size: Long? = null,
        val count: CountResult? = null,
        val canRead: Boolean = false,
        val canWrite: Boolean = false,
        val canExecute: Boolean = false,
        val sha256: String? = null,
        val md5: String? = null,
        val computingSize: Boolean = false,
        val computingHash: Boolean = false,
    )

    data class UiState(
        val current: FileNode? = null,
        val ancestors: List<FileNode> = emptyList(),
        val entries: List<FileNode> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val viewMode: ViewMode = ViewMode.LIST,
        val sort: SortOrder = SortOrder.NAME,
        val selectionMode: Boolean = false,
        val selection: Set<String> = emptySet(),
        val destMode: DestMode? = null,
        val properties: PropertiesState? = null,
        val showHidden: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState(showHidden = container.settings.showHidden.value))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(current = container.fs.rootNode(location)) }
        refresh()
        viewModelScope.launch {
            container.settings.showHidden.collect { hidden ->
                _state.update { it.copy(showHidden = hidden) }
                refresh()
            }
        }
    }

    // ------------------------------------------------------------ browsing

    fun refresh() {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val entries = try {
                container.fs.list(cur, _state.value.showHidden, _state.value.sort)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Error") }
                emptyList()
            }
            _state.update { it.copy(entries = entries, loading = false) }
        }
    }

    fun openDir(node: FileNode) {
        val current = _state.value.current ?: return
        _state.update {
            it.copy(
                ancestors = it.ancestors + current,
                current = node,
                selectionMode = false,
                selection = emptySet(),
            )
        }
        refresh()
    }

    fun goUp() {
        val s = _state.value
        if (s.ancestors.isEmpty()) return
        val parent = s.ancestors.last()
        _state.update {
            it.copy(
                ancestors = it.ancestors.dropLast(1),
                current = parent,
                selectionMode = false,
                selection = emptySet(),
            )
        }
        refresh()
    }

    fun canGoUp(): Boolean = _state.value.ancestors.isNotEmpty()

    fun navigateTo(ancestor: FileNode) {
        val idx = _state.value.ancestors.indexOfFirst { it.path == ancestor.path }
        if (idx < 0) return
        _state.update {
            it.copy(
                ancestors = it.ancestors.take(idx),
                current = ancestor,
                selectionMode = false,
                selection = emptySet(),
            )
        }
        refresh()
    }

    fun toggleViewMode() {
        _state.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
        }
    }

    fun setSort(sort: SortOrder) {
        _state.update { it.copy(sort = sort) }
        refresh()
    }

    // ---------------------------------------------------------- selection

    fun enterSelection(node: FileNode) {
        _state.update { it.copy(selectionMode = true, selection = setOf(node.path)) }
    }

    fun toggleSelect(node: FileNode) {
        _state.update { s ->
            val sel = s.selection.toMutableSet()
            if (!sel.add(node.path)) sel.remove(node.path)
            s.copy(selection = sel, selectionMode = sel.isNotEmpty())
        }
    }

    fun selectAll() {
        _state.update { it.copy(selection = it.entries.map { n -> n.path }.toSet(), selectionMode = true) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectionMode = false, selection = emptySet()) }
    }

    /** Currently selected nodes (from the visible entries). */
    fun selectedNodes(): List<FileNode> {
        val sel = _state.value.selection
        return _state.value.entries.filter { it.path in sel }
    }

    // --------------------------------------------------------- operations

    fun startDestMode(mode: DestMode) {
        _state.update { it.copy(destMode = mode) }
    }

    fun cancelDestMode() {
        _state.update { it.copy(destMode = null) }
    }

    fun copyFlow(): Flow<OpProgress> = flow {
        val dest = _state.value.current ?: return@flow
        for (n in selectedNodes()) container.fs.copy(n, dest) { emit(it) }
    }

    fun moveFlow(): Flow<OpProgress> = flow {
        val dest = _state.value.current ?: return@flow
        for (n in selectedNodes()) container.fs.move(n, dest) { emit(it) }
    }

    fun deleteFlow(): Flow<OpProgress> = flow {
        for (n in selectedNodes()) container.fs.delete(n) { emit(it) }
    }

    fun compressFlow(name: String): Flow<OpProgress> = flow {
        val dest = _state.value.current ?: return@flow
        container.fs.compress(selectedNodes(), dest, name) { emit(it) }
    }

    /** Call after an operation finishes (ok = completed, false = cancelled/failed). */
    fun onOperationFinished(ok: Boolean) {
        clearSelection()
        cancelDestMode()
        refresh()
    }

    fun renameSelected(newName: String) {
        val node = selectedNodes().firstOrNull() ?: return
        viewModelScope.launch {
            container.fs.rename(node, newName)
            clearSelection()
            refresh()
        }
    }

    fun createFolder(name: String) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            container.fs.createDirectory(cur, name)
            refresh()
        }
    }

    // --------------------------------------------------------- properties

    fun showProperties(node: FileNode) {
        val (r, w, x) = permissionsOf(node)
        _state.update {
            it.copy(
                properties = PropertiesState(
                    node = node,
                    mime = FileKinds.mimeOf(node),
                    canRead = r,
                    canWrite = w,
                    canExecute = x,
                )
            )
        }
        refreshProperties()
    }

    fun dismissProperties() {
        _state.update { it.copy(properties = null) }
    }

    fun refreshProperties() {
        val props = _state.value.properties ?: return
        val node = props.node
        if (node.isDir) {
            _state.update { it.copy(properties = it.properties?.copy(computingSize = true)) }
            viewModelScope.launch {
                val size = container.fs.sizeOf(node)
                val count = container.fs.countEntries(node)
                _state.update {
                    it.copy(
                        properties = it.properties?.copy(
                            size = size,
                            count = count,
                            computingSize = false,
                        )
                    )
                }
            }
        } else {
            _state.update {
                it.copy(properties = it.properties?.copy(size = node.size, count = CountResult(1, 0)))
            }
        }
    }

    fun computeHash(algorithm: HashAlgorithm) {
        val node = _state.value.properties?.node ?: return
        if (node.isDir) return
        _state.update { it.copy(properties = it.properties?.copy(computingHash = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            val stream = container.fs.openInputStream(node)
            val hash = stream?.use { HashUtil.hash(it, algorithm) { true } }
            _state.update {
                it.copy(
                    properties = it.properties?.copy(
                        sha256 = if (algorithm == HashAlgorithm.SHA256) hash else it.properties?.sha256,
                        md5 = if (algorithm == HashAlgorithm.MD5) hash else it.properties?.md5,
                        computingHash = false,
                    )
                )
            }
        }
    }

    private fun permissionsOf(node: FileNode): Triple<Boolean, Boolean, Boolean> {
        if (node.uri != null) return Triple(true, true, false)
        val file = File(node.path)
        return Triple(file.canRead(), file.canWrite(), file.canExecute())
    }
}