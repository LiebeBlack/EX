package com.apex.files.ui.screens.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.HashAlgorithm
import com.apex.files.core.HashUtil
import com.apex.files.core.OpProgress
import com.apex.files.core.OpType
import com.apex.files.data.fs.CountResult
import com.apex.files.data.fs.FileKinds
import com.apex.files.data.fs.OpResult
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import com.apex.files.data.model.SortDirection
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
        val sortDir: SortDirection = SortDirection.ASC,
        val selectionMode: Boolean = false,
        val selection: Set<String> = emptySet(),
        /** Anchor path for long-press range selection. */
        val anchor: String? = null,
        val destMode: DestMode? = null,
        /** Sources captured when “Copiar/Mover” was pressed, so navigating to
         *  the destination never loses the selection. */
        val pendingSources: List<FileNode> = emptyList(),
        val properties: PropertiesState? = null,
        val showHidden: Boolean = false,
        /** In-folder live filter (client-side, instant). */
        val filterQuery: String = "",
        /** One-shot toast messages (rename/folder/create failures). */
        val notice: String? = null,
    ) {
        /** Entries after applying the live name filter. */
        val visibleEntries: List<FileNode>
            get() = if (filterQuery.isBlank()) entries
            else entries.filter { com.apex.files.data.fs.SearchFilters.matchesName(it.name, filterQuery) }

        val filteredOut: Int get() = entries.size - visibleEntries.size
    }

    private val _state = MutableStateFlow(
        UiState(
            showHidden = container.settings.showHidden.value,
            viewMode = container.settings.viewMode.value,
            sort = container.settings.sortOrder.value,
            sortDir = container.settings.sortDirection.value,
        )
    )
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
        viewModelScope.launch {
            container.settings.sortDirection.collect { dir ->
                _state.update { it.copy(sortDir = dir) }
                refresh()
            }
        }
    }

    // ------------------------------------------------------------ browsing

    fun refresh() {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val s = _state.value
            val entries = try {
                container.fs.list(cur, s.showHidden, s.sort, s.sortDir)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Error") }
                emptyList()
            }
            _state.update { it.copy(entries = entries, loading = false) }
        }
    }

    fun openDir(node: FileNode) {
        val current = _state.value.current ?: return
        // Folder opens feed the Home “Recientes” quick access list.
        container.recents.record(node)
        _state.update {
            it.copy(
                ancestors = it.ancestors + current,
                current = node,
                selectionMode = false,
                selection = emptySet(),
                anchor = null,
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
                anchor = null,
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
                anchor = null,
            )
        }
        refresh()
    }

    fun toggleViewMode() {
        val next = if (_state.value.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
        container.settings.setViewMode(next)
        _state.update { it.copy(viewMode = next) }
    }

    fun setSort(sort: SortOrder) {
        container.settings.setSortOrder(sort)
        _state.update { it.copy(sort = sort) }
        refresh()
    }

    fun setSortDirection(direction: SortDirection) {
        container.settings.setSortDirection(direction)
        _state.update { it.copy(sortDir = direction) }
        refresh()
    }

    fun setShowHidden(show: Boolean) {
        container.settings.setShowHidden(show)
        _state.update { it.copy(showHidden = show) }
        refresh()
    }

    // ---------------------------------------------------------- selection

    fun enterSelection(node: FileNode) {
        _state.update { it.copy(selectionMode = true, selection = setOf(node.path), anchor = node.path) }
    }

    /**
     * Long-press behavior while already selecting: with an anchor set, a
     * long-press on another row selects the whole contiguous range; the
     * anchor moves to the pressed row. Without an anchor it starts a new
     * single selection.
     */
    fun longPress(node: FileNode) {
        val s = _state.value
        val anchorPath = s.anchor
        if (s.selectionMode && anchorPath != null && anchorPath != node.path) {
            selectRange(anchorPath, node.path)
        } else {
            enterSelection(node)
        }
    }

    fun selectRange(from: String, to: String) {
        _state.update { s ->
            val paths = s.entries.map { it.path }
            val a = paths.indexOf(from)
            val b = paths.indexOf(to)
            if (a < 0 || b < 0) {
                s.copy(selection = s.selection + to, anchor = to)
            } else {
                val range = if (a <= b) paths.subList(a, b + 1) else paths.subList(b, a + 1)
                s.copy(selection = s.selection + range.toSet(), selectionMode = true, anchor = to)
            }
        }
    }

    fun toggleSelect(node: FileNode) {
        _state.update { s ->
            val sel = s.selection.toMutableSet()
            if (!sel.add(node.path)) sel.remove(node.path)
            s.copy(selection = sel, selectionMode = sel.isNotEmpty(), anchor = if (sel.isNotEmpty()) s.anchor else null)
        }
    }

    fun selectAll() {
        _state.update { it.copy(selection = it.entries.map { n -> n.path }.toSet(), selectionMode = true) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectionMode = false, selection = emptySet(), anchor = null) }
    }

    /** Currently selected nodes (from the visible entries). */
    fun selectedNodes(): List<FileNode> {
        val sel = _state.value.selection
        return _state.value.entries.filter { it.path in sel }
    }

    // --------------------------------------------------------- operations

    /** Captures the selected sources so paste works after navigating away. */
    fun startDestMode(mode: DestMode) {
        val sources = selectedNodes()
        if (sources.isEmpty()) return
        _state.update {
            it.copy(
                destMode = mode,
                pendingSources = sources,
                selectionMode = false,
                selection = emptySet(),
                anchor = null,
            )
        }
    }

    fun cancelDestMode() {
        _state.update { it.copy(destMode = null, pendingSources = emptyList()) }
    }

    private fun destSources(): List<FileNode> {
        val pending = _state.value.pendingSources
        return if (pending.isNotEmpty()) pending else selectedNodes()
    }

    private fun parentOf(node: FileNode): String {
        val idx = node.path.lastIndexOf('/')
        return if (idx <= 0) "/" else node.path.substring(0, idx)
    }

    fun copyFlow(): Flow<OpProgress> = flow {
        val dest = _state.value.current ?: return@flow
        val sources = destSources()
        clearSummary()
        var acc = OpResult()
        for (n in sources) {
            acc += container.fs.copy(n, dest, onProgress = { emit(it) }, onConflict = container.conflicts::resolve)
        }
        _opSummary.value = summarize(OpType.COPY, acc)
    }

    fun moveFlow(): Flow<OpProgress> = flow {
        val dest = _state.value.current ?: return@flow
        val sources = destSources()
        clearSummary()
        // Sources already living in the destination are no-ops for a move.
        val destPath = dest.path
        val (local, others) = sources.partition { parentOf(it) == destPath }
        var acc = OpResult(skipped = local.size)
        for (n in others) {
            acc += container.fs.move(n, dest, onProgress = { emit(it) }, onConflict = container.conflicts::resolve)
        }
        if (local.isNotEmpty()) {
            acc = acc.copy(firstError = acc.firstError ?: "Algunos elementos ya estaban en la carpeta de destino")
        }
        _opSummary.value = summarize(OpType.MOVE, acc)
    }

    fun deleteFlow(): Flow<OpProgress> = flow {
        val sources = selectedNodes()
        clearSummary()
        val trashEnabled = container.settings.trashEnabled.value
        val (toTrash, toDelete) = sources.partition { trashEnabled && it.uri == null }
        var acc = OpResult()
        for (n in toDelete) {
            acc += container.fs.delete(n) { emit(it) }
        }
        for (n in toTrash) {
            acc += container.trash.trash(n)
        }
        _opSummary.value = when {
            toTrash.isNotEmpty() && toDelete.isEmpty() -> {
                val suffix = if (acc.errors > 0) " · ${acc.errors} errores" else ""
                "Enviados a la papelera: ${toTrash.size}$suffix"
            }
            toTrash.isNotEmpty() -> {
                "Enviados a la papelera: ${toTrash.size} · ${summarize(OpType.DELETE, acc)}"
            }
            else -> summarize(OpType.DELETE, acc)
        }
    }

    fun compressFlow(name: String): Flow<OpProgress> = flow {
        val dest = _state.value.current ?: return@flow
        val sources = destSources()
        clearSummary()
        val acc = container.fs.compress(
            sources,
            dest,
            name,
            onProgress = { emit(it) },
            onConflict = container.conflicts::resolve,
        )
        _opSummary.value = summarize(OpType.COMPRESS, acc)
    }

    private val _opSummary = MutableStateFlow<String?>(null)
    val opSummary: StateFlow<String?> = _opSummary.asStateFlow()

    private val _opError = MutableStateFlow<String?>(null)
    val opError: StateFlow<String?> = _opError.asStateFlow()

    private fun clearSummary() {
        _opSummary.value = null
        _opError.value = null
    }

    private fun summarize(type: OpType, acc: OpResult): String {
        val verb = when (type) {
            OpType.COPY -> "Copiados"
            OpType.MOVE -> "Movidos"
            OpType.DELETE -> "Eliminados"
            OpType.COMPRESS -> "Comprimidos"
            OpType.EXTRACT -> "Extraídos"
            OpType.BENCHMARK -> "Probados"
        }
        val base = "$verb: ${acc.filesDone} elemento(s)"
        val extras = buildList {
            if (acc.skipped > 0) add("${acc.skipped} omitidos")
            if (acc.errors > 0) add("${acc.errors} errores")
        }
        val suffix = if (extras.isNotEmpty()) " · " + extras.joinToString(", ") else ""
        return base + suffix
    }

    /** Call after an operation finishes (ok = completed, false = cancelled/failed). */
    fun onOperationFinished(ok: Boolean) {
        if (!ok) {
            _opError.value = "Operación no completada"
        }
        clearSelection()
        cancelDestMode()
        refresh()
    }

    fun consumeSummary(): String? {
        val v = _opSummary.value
        _opSummary.value = null
        return v
    }

    fun consumeError(): String? {
        val v = _opError.value
        _opError.value = null
        return v
    }

    fun renameSelected(newName: String) {
        val node = selectedNodes().firstOrNull() ?: return
        viewModelScope.launch {
            val renamed = container.fs.rename(node, newName)
            if (renamed == null) {
                _state.update { it.copy(notice = "No se pudo renombrar: el nombre ya existe o no es válido") }
            } else {
                clearSelection()
                refresh()
            }
        }
    }

    fun createFolder(name: String) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            val created = container.fs.createDirectory(cur, name)
            if (created == null) {
                _state.update { it.copy(notice = "No se pudo crear la carpeta: el nombre ya existe o no es válido") }
            } else {
                refresh()
            }
        }
    }

    fun createFile(name: String) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            val created = container.fs.createFile(cur, name)
            if (created == null) {
                _state.update { it.copy(notice = "No se pudo crear el archivo: el nombre ya existe o no es válido") }
            } else {
                refresh()
            }
        }
    }

    // -------------------------------------------------------- live filter

    fun setFilterQuery(query: String) {
        _state.update { it.copy(filterQuery = query) }
    }

    // ------------------------------------------------------- extract here

    /** Extracts the selected archive into the current folder. */
    fun extractHereFlow(): Flow<OpProgress> = flow {
        val dest = _state.value.current ?: return@flow
        val source = selectedNodes().firstOrNull() ?: return@flow
        clearSummary()
        val acc = container.archive.extractAll(
            source,
            dest,
            onProgress = { emit(it) },
            onConflict = container.conflicts::resolve,
        )
        _opSummary.value = summarize(OpType.EXTRACT, acc)
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
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
