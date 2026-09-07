package com.apex.files.ui.screens.explorer

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.HashAlgorithm
import com.apex.files.core.HashUtil
import com.apex.files.core.ListDensity
import com.apex.files.core.OpProgress
import com.apex.files.core.OpType
import com.apex.files.data.fs.CountResult
import com.apex.files.data.fs.FileKinds
import com.apex.files.data.fs.OpResult
import com.apex.files.data.fs.SearchFilters
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import com.apex.files.data.model.SortDirection
import com.apex.files.data.model.SortOrder
import com.apex.files.data.model.ViewMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ExplorerViewModel(
    private val container: AppContainer,
    val location: Location,
) : ViewModel() {

    /** Active directory size/count walk, cancelled on dismiss or restart. */
    private var propertiesJob: Job? = null

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
        val destMode: DestMode? = null,
        /** Row density from settings (drives list/grid sizing). */
        val density: ListDensity = ListDensity.NORMAL,
        /** Sources captured when “Copiar/Mover” was pressed, so navigating to
         *  the destination never loses the selection. */
        val pendingSources: List<FileNode> = emptyList(),
        val properties: PropertiesState? = null,
        val showHidden: Boolean = false,
        /** In-folder live filter (client-side, instant). */
        val filterQuery: String = "",
        /** One-shot toast messages (rename/folder/create failures). */
        val notice: String? = null,
        /**
         * Entries after applying the live name filter. Memoized: recomputed
         * only when the query or the folder listing changes, so the UI never
         * re-filters the whole list on every state read.
         */
        val visibleEntries: List<FileNode> = emptyList(),
    ) {
        val filteredOut: Int get() = entries.size - visibleEntries.size
    }

    private val _state = MutableStateFlow(
        UiState(
            showHidden = container.settings.showHidden.value,
            viewMode = container.settings.viewMode.value,
            sort = container.settings.sortOrder.value,
            sortDir = container.settings.sortDirection.value,
            density = container.settings.density.value,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    // ------------------------------------------------------------ selection

    /** Whether multi-select mode is active (drives the selection bar). */
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    /**
     * Per-path selection map. Snapshot reads are keyed per path, so toggling
     * one row only recomposes that row instead of the whole list.
     */
    val selection: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    private var anchor: String? = null

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
        viewModelScope.launch {
            container.settings.density.collect { d ->
                _state.update { it.copy(density = d) }
            }
        }
    }

    // ------------------------------------------------------------ browsing

    private var refreshJob: Job? = null
    private var refreshGeneration = 0

    fun refresh() {
        val cur = _state.value.current ?: return
        // Cancel the in-flight listing and bump a generation counter so a
        // slow read of a previous folder can never overwrite the entries of
        // the folder the user is currently seeing.
        refreshJob?.cancel()
        val gen = ++refreshGeneration
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val s = _state.value
            val entries = try {
                container.fs.list(cur, s.showHidden, s.sort, s.sortDir)
            } catch (e: Exception) {
                if (gen == refreshGeneration) _state.update { it.copy(error = e.message ?: "Error") }
                emptyList()
            }
            if (gen == refreshGeneration) {
                _state.update { s ->
                    val visible = if (s.filterQuery.isBlank()) entries
                    else entries.filter { SearchFilters.matchesName(it.name, s.filterQuery) }
                    s.copy(entries = entries, visibleEntries = visible, loading = false)
                }
            }
        }
    }

    fun openDir(node: FileNode) {
        val current = _state.value.current ?: return
        // Folder opens feed the Home “Recientes” quick access list.
        container.recents.record(node)
        clearSelection()
        _state.update {
            it.copy(
                ancestors = it.ancestors + current,
                current = node,
                filterQuery = "",
                visibleEntries = emptyList(),
            )
        }
        refresh()
    }

    fun goUp() {
        val s = _state.value
        if (s.ancestors.isEmpty()) return
        val parent = s.ancestors.last()
        clearSelection()
        _state.update {
            it.copy(
                ancestors = it.ancestors.dropLast(1),
                current = parent,
                filterQuery = "",
                visibleEntries = emptyList(),
            )
        }
        refresh()
    }

    fun canGoUp(): Boolean = _state.value.ancestors.isNotEmpty()

    fun navigateTo(ancestor: FileNode) {
        val idx = _state.value.ancestors.indexOfFirst { it.path == ancestor.path }
        if (idx < 0) return
        clearSelection()
        _state.update {
            it.copy(
                ancestors = it.ancestors.take(idx),
                current = ancestor,
                filterQuery = "",
                visibleEntries = emptyList(),
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
        selection.clear()
        selection[node.path] = true
        anchor = node.path
        _selectionMode.value = true
    }

    /**
     * Long-press behavior while already selecting: with an anchor set, a
     * long-press on another row selects the whole contiguous range; the
     * anchor moves to the pressed row. Without an anchor it starts a new
     * single selection.
     */
    fun longPress(node: FileNode) {
        val anchorPath = anchor
        if (_selectionMode.value && anchorPath != null && anchorPath != node.path) {
            selectRange(anchorPath, node.path)
        } else {
            enterSelection(node)
        }
    }

    fun selectRange(from: String, to: String) {
        val paths = _state.value.entries.map { it.path }
        val a = paths.indexOf(from)
        val b = paths.indexOf(to)
        if (a < 0 || b < 0) {
            selection[to] = true
        } else {
            val range = if (a <= b) paths.subList(a, b + 1) else paths.subList(b, a + 1)
            range.forEach { selection[it] = true }
        }
        anchor = to
        _selectionMode.value = true
    }

    fun toggleSelect(node: FileNode) {
        if (selection[node.path] == true) {
            selection.remove(node.path)
        } else {
            selection[node.path] = true
        }
        _selectionMode.value = selection.isNotEmpty()
        if (selection.isEmpty()) anchor = null
    }

    fun selectAll() {
        selection.clear()
        _state.value.entries.forEach { selection[it.path] = true }
        _selectionMode.value = true
    }

    fun clearSelection() {
        selection.clear()
        anchor = null
        _selectionMode.value = false
    }

    /** Currently selected nodes (from the visible entries). */
    fun selectedNodes(): List<FileNode> =
        _state.value.entries.filter { selection[it.path] == true }

    // --------------------------------------------------------- operations

    /** Captures the selected sources so paste works after navigating away. */
    fun startDestMode(mode: DestMode) {
        val sources = selectedNodes()
        if (sources.isEmpty()) return
        clearSelection()
        _state.update {
            it.copy(
                destMode = mode,
                pendingSources = sources,
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
        container.conflicts.resetApplyAll()
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
        container.conflicts.resetApplyAll()
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
        container.conflicts.resetApplyAll()
        val dest = _state.value.current ?: return@flow
        val sources = destSources()
        clearSummary()
        if (!isValidName(name)) {
            _opError.value = "Nombre de archivo no válido"
            return@flow
        }
        val acc = container.fs.compress(
            sources,
            dest,
            name,
            onProgress = { emit(it) },
            onConflict = container.conflicts::resolve,
        )
        _opSummary.value = summarize(OpType.COMPRESS, acc)
    }

    /**
     * Copies each selected local file next to itself with a unique name
     * ("x.txt" → "x (1).txt"). SAF nodes and folders are skipped.
     */
    fun duplicateFlow(): Flow<OpProgress> = flow {
        val sources = selectedNodes().filter { it.uri == null && !it.isDir }
        clearSummary()
        if (sources.isEmpty()) {
            _opError.value = "Solo se pueden duplicar archivos del almacenamiento interno"
            return@flow
        }
        var acc = OpResult()
        for (n in sources) {
            acc += container.fs.duplicateFile(n, onProgress = { emit(it) })
        }
        _opSummary.value = "Duplicados: ${acc.filesDone} elemento(s)${if (acc.errors > 0) " · ${acc.errors} errores" else ""}"
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

    /**
     * Call after an operation finishes. Successful or explicitly cancelled
     * operations leave the browser clean; a failed/interrupted one keeps the
     * selection and destination mode so the user can retry or pick another
     * destination without re-selecting everything.
     */
    fun onOperationFinished(ok: Boolean, cancelled: Boolean = false) {
        if (!ok && !cancelled) {
            // The failure message is toasted by the caller; keep the current
            // selection and destination so the user can retry the operation.
            refresh()
            return
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
        if (!isValidName(newName)) {
            _state.update { it.copy(notice = "Nombre no válido: evita «/», «\\», «.» y «..»") }
            return
        }
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
        if (!isValidName(name)) {
            _state.update { it.copy(notice = "Nombre no válido: evita «/», «\\», «.» y «..»") }
            return
        }
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
        if (!isValidName(name)) {
            _state.update { it.copy(notice = "Nombre no válido: evita «/», «\\», «.» y «..»") }
            return
        }
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
        _state.update { s ->
            val visible = if (query.isBlank()) s.entries
            else s.entries.filter { SearchFilters.matchesName(it.name, query) }
            s.copy(filterQuery = query, visibleEntries = visible)
        }
    }

    /** Filesystem-safe name check shared by create/rename/compress. */
    private fun isValidName(name: String): Boolean =
        name.isNotBlank() &&
            name != "." && name != ".." &&
            !name.contains('/') && !name.contains('\\')

    // ------------------------------------------------------- extract here

    /** Extracts the selected archive into the current folder. */
    fun extractHereFlow(): Flow<OpProgress> = flow {
        container.conflicts.resetApplyAll()
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

    // ---------------------------------------------------- advanced operations

    /** Creates a backup of the selected file with timestamp. */
    suspend fun createBackup(node: FileNode): FileNode? {
        return container.fs.createTimestampedBackup(node)
    }

    /** Batch operation: create backups for all selected files. */
    suspend fun batchBackup(nodes: List<FileNode>): OpResult {
        var acc = OpResult()
        for (node in nodes) {
            val backup = createBackup(node)
            if (backup != null) {
                acc = acc.copy(filesDone = acc.filesDone + 1)
            } else {
                acc = acc.copy(errors = acc.errors + 1)
            }
        }
        return acc
    }

    /** Merges selected text files into a single file. */
    suspend fun mergeTextFiles(nodes: List<FileNode>, destName: String): OpResult {
        if (nodes.isEmpty()) return OpResult(errors = 1, firstError = "No hay archivos seleccionados")
        
        val cur = _state.value.current ?: return OpResult(errors = 1, firstError = "No hay carpeta actual")
        val destNode = container.fs.createFile(cur, destName)
        
        if (destNode == null) {
            return OpResult(errors = 1, firstError = "No se pudo crear el archivo de destino")
        }
        
        val success = container.fs.mergeTextFiles(nodes, destNode, Charsets.UTF_8)
        return if (success) {
            OpResult(filesDone = nodes.size)
        } else {
            OpResult(errors = 1, firstError = "Error al fusionar archivos")
        }
    }

    /** Splits a selected text file into multiple parts. */
    suspend fun splitTextFile(node: FileNode, linesPerFile: Int): List<FileNode> {
        val cur = _state.value.current ?: return emptyList()
        return container.fs.splitTextFile(node, linesPerFile, cur, Charsets.UTF_8)
    }

    /** Batch rename with pattern support. */
    suspend fun batchRenameWithPattern(pattern: String, startNumber: Int): OpResult {
        val sources = selectedNodes()
        if (sources.isEmpty()) return OpResult(errors = 1, firstError = "No hay archivos seleccionados")
        
        return container.fs.batchRename(sources, pattern, startNumber)
    }

    /** Changes file permissions for selected files. */
    suspend fun changePermissions(permissions: Int): OpResult {
        val sources = selectedNodes()
        if (sources.isEmpty()) return OpResult(errors = 1, firstError = "No hay archivos seleccionados")
        
        var acc = OpResult()
        for (source in sources) {
            val success = container.fs.changePermissions(source, permissions)
            if (success) {
                acc = acc.copy(filesDone = acc.filesDone + 1)
            } else {
                acc = acc.copy(errors = acc.errors + 1)
            }
        }
        return acc
    }

    /** Changes timestamps for selected files to current time. */
    suspend fun touchFiles(): OpResult {
        val sources = selectedNodes()
        if (sources.isEmpty()) return OpResult(errors = 1, firstError = "No hay archivos seleccionados")
        
        val now = System.currentTimeMillis()
        var acc = OpResult()
        
        for (source in sources) {
            val success = container.fs.setLastModified(source, now)
            if (success) {
                acc = acc.copy(filesDone = acc.filesDone + 1)
            } else {
                acc = acc.copy(errors = acc.errors + 1)
            }
        }
        return acc
    }

    /** Copies file paths to clipboard as formatted list. */
    fun copyFormattedPaths(format: String): String {
        val nodes = selectedNodes()
        if (nodes.isEmpty()) return ""
        
        return nodes.joinToString("\n") { node ->
            format.replace("{name}", node.name)
                .replace("{path}", node.path)
                .replace("{size}", SizeFormatter.format(node.size))
        }
    }

    /** Creates symbolic links for selected files. */
    suspend fun createSymlinks(destDir: FileNode): OpResult {
        val sources = selectedNodes()
        if (sources.isEmpty()) return OpResult(errors = 1, firstError = "No hay archivos seleccionados")
        
        var acc = OpResult()
        for (source in sources) {
            val linkName = "link_${source.name}"
            val created = container.fs.createSymlink(source, linkName, destDir)
            if (created != null) {
                acc = acc.copy(filesDone = acc.filesDone + 1)
            } else {
                acc = acc.copy(errors = acc.errors + 1)
            }
        }
        return acc
    }

    /** Compares two selected files for content equality. */
    suspend fun compareFiles(node1: FileNode, node2: FileNode): Boolean {
        return container.fs.filesAreIdentical(node1, node2)
    }

    /** Finds files similar to the selected file. */
    suspend fun findSimilarFiles(reference: FileNode): List<FileNode> {
        val cur = _state.value.current ?: return emptyList()
        return container.fs.findSimilarFiles(reference, cur, similarityThreshold = 0.7f)
    }

    /** Monitors current directory for changes (returns a job that can be cancelled). */
    fun startMonitoring(onChange: (List<FileNode>) -> Unit): kotlinx.coroutines.Job {
        val cur = _state.value.current ?: return kotlinx.coroutines.Job()
        return viewModelScope.launch {
            container.fs.monitorDirectory(cur, onChange = onChange)
        }
    }

    /** Compresses selected files to GZIP format. */
    suspend fun compressGzip(destName: String): OpResult {
        val sources = selectedNodes()
        if (sources.isEmpty()) return OpResult(errors = 1, firstError = "No hay archivos seleccionados")
        if (sources.size != 1) return OpResult(errors = 1, firstError = "GZIP solo soporta un archivo a la vez")
        
        val cur = _state.value.current ?: return OpResult(errors = 1, firstError = "No hay carpeta actual")
        val destNode = container.fs.createFile(cur, "$destName.gz")
        
        if (destNode == null) {
            return OpResult(errors = 1, firstError = "No se pudo crear el archivo de destino")
        }
        
        val success = container.fs.compressGzip(sources[0], destNode)
        return if (success) {
            OpResult(filesDone = 1)
        } else {
            OpResult(errors = 1, firstError = "Error al comprimir archivo")
        }
    }

    /** Decompresses selected GZIP file. */
    suspend fun decompressGzip(): OpResult {
        val sources = selectedNodes()
        if (sources.isEmpty()) return OpResult(errors = 1, firstError = "No hay archivos seleccionados")
        if (sources.size != 1) return OpResult(errors = 1, firstError = "Solo se puede descomprimir un archivo a la vez")
        
        val source = sources[0]
        if (!source.name.endsWith(".gz")) {
            return OpResult(errors = 1, firstError = "El archivo no es un archivo GZIP")
        }
        
        val cur = _state.value.current ?: return OpResult(errors = 1, firstError = "No hay carpeta actual")
        val outName = source.name.removeSuffix(".gz")
        val destNode = container.fs.createFile(cur, outName)
        
        if (destNode == null) {
            return OpResult(errors = 1, firstError = "No se pudo crear el archivo de destino")
        }
        
        val success = container.fs.decompressGzip(source, destNode)
        return if (success) {
            OpResult(filesDone = 1)
        } else {
            OpResult(errors = 1, firstError = "Error al descomprimir archivo")
        }
    }

    /** Gets detailed storage statistics for current directory. */
    suspend fun getDirectoryStats(): Pair<Long, Int> {
        val cur = _state.value.current ?: return Pair(0L, 0)
        val size = container.fs.sizeOf(cur)
        val count = container.fs.countEntries(cur)
        return Pair(size, count.files + count.dirs)
    }

    /** Filters files by multiple criteria simultaneously. */
    fun applyAdvancedFilter(
        sizeMin: Long? = null,
        sizeMax: Long? = null,
        dateAfter: Long? = null,
        dateBefore: Long? = null,
        extensions: Set<String>? = null,
    ): List<FileNode> {
        return _state.value.entries.filter { node ->
            if (sizeMin != null && node.size < sizeMin) return@filter false
            if (sizeMax != null && node.size > sizeMax) return@filter false
            if (dateAfter != null && node.lastModified < dateAfter) return@filter false
            if (dateBefore != null && node.lastModified > dateBefore) return@filter false
            if (extensions != null && node.extension !in extensions) return@filter false
            true
        }
    }

    /** Sorts current entries by custom comparator. */
    fun sortByCustom(comparator: (FileNode, FileNode) -> Int) {
        val sorted = _state.value.entries.sortedWith(comparator)
        _state.update { it.copy(entries = sorted, visibleEntries = sorted) }
    }

    /** Groups files by category and returns the groups. */
    fun groupByCategory(): Map<String, List<FileNode>> {
        return _state.value.entries.groupBy { it.category.displayName }
    }

    /** Groups files by extension and returns the groups. */
    fun groupByExtension(): Map<String, List<FileNode>> {
        return _state.value.entries.groupBy { 
            if (it.extension.isEmpty()) "sin extensión" else it.extension 
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
        // Stop the recursive size/count walk when the sheet goes away.
        propertiesJob?.cancel()
        propertiesJob = null
        _state.update { it.copy(properties = null) }
    }

    fun refreshProperties() {
        val props = _state.value.properties ?: return
        val node = props.node
        if (node.isDir) {
            _state.update { it.copy(properties = it.properties?.copy(computingSize = true)) }
            // A directory size/count is a full recursive walk. Reopening the
            // sheet must not stack a second walk over the running one, and
            // closing it should stop the walk instead of burning I/O in the
            // background for a sheet nobody is looking at.
            propertiesJob?.cancel()
            propertiesJob = viewModelScope.launch {
                // Fallbacks: a revoked permission or I/O failure must not leave
                // the sheet spinning on "Calculando…" forever.
                val size = runCatching { container.fs.sizeOf(node) }.getOrDefault(0L)
                val count = runCatching { container.fs.countEntries(node) }.getOrNull()
                if (!isActive) return@launch
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
            // Wire real coroutine cancellation so hashing a huge file stops
            // promptly when the sheet closes instead of reading to the end.
            val hash = stream?.use { HashUtil.hash(it, algorithm) { isActive } }
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
