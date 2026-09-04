package com.apex.files.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.OpProgress
import com.apex.files.data.fs.ArchiveEntry
import com.apex.files.data.fs.ArchiveRepository
import com.apex.files.data.fs.OpResult
import com.apex.files.data.model.FileNode
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
 * lazily by prefix; extraction streams only the requested entries and pauses
 * on name collisions for a per-file decision.
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

    private val _opSummary = MutableStateFlow<String?>(null)
    val opSummary: StateFlow<String?> = _opSummary.asStateFlow()

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
        _opSummary.value = null
        val acc = container.archive.extract(
            entry,
            node,
            destDir,
            onProgress = { emit(it) },
            onConflict = container.conflicts::resolve,
        )
        _opSummary.value = summarize(acc)
    }

    /** Extracts the entire archive into [destDir]. */
    fun extractAllFlow(destDir: FileNode): Flow<OpProgress> = flow {
        _opSummary.value = null
        val acc = container.archive.extractAll(
            node,
            destDir,
            onProgress = { emit(it) },
            onConflict = container.conflicts::resolve,
        )
        _opSummary.value = summarize(acc)
    }

    private fun summarize(acc: OpResult): String {
        val parts = buildList {
            add("Extraídos: ${acc.filesDone} archivo(s)")
            if (acc.skipped > 0) add("${acc.skipped} omitidos")
            if (acc.errors > 0) add("${acc.errors} errores")
        }
        return parts.joinToString(" · ")
    }

    fun consumeSummary(): String? {
        val v = _opSummary.value
        _opSummary.value = null
        return v
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
