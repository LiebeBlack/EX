package com.apex.files.ui.screens.smart

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.OpProgress
import com.apex.files.data.fs.OpResult
import com.apex.files.data.model.FileNode
import com.apex.files.data.search.SmartClassifier
import com.apex.files.data.search.SmartGroup
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dynamic (virtual) folders: files are grouped by content (extension + name +
 * indexed OCR/PDF text) but never moved automatically. The user can opt into
 * physically moving a whole group to Descargas/Apex-Organizadas/<Grupo>.
 */
class SmartGroupsViewModel(private val container: AppContainer) : ViewModel() {

    data class GroupInfo(
        val group: SmartGroup,
        val nodes: List<FileNode>,
        val totalBytes: Long,
    )

    data class UiState(
        val loading: Boolean = true,
        val groups: List<GroupInfo> = emptyList(),
        val openGroup: SmartGroup? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _summary = MutableStateFlow<String?>(null)
    val summary: StateFlow<String?> = _summary.asStateFlow()

    init {
        refresh()
    }

    /** Rebuilds the groups from the search index + content index. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            withContext(Dispatchers.IO) {
                // Same lazy-load strategy as Home: snapshot first, walk only
                // when missing.
                if (container.index.size == 0) {
                    val cached = container.indexStore.load()
                    if (cached != null) {
                        container.index.restore(cached)
                    } else {
                        container.index.rebuild(container.settings.showHidden.value)
                        container.indexStore.save(container.index.allFiles())
                    }
                }
                container.contentIndex.load()
            }
            val infos = SmartGroup.entries.mapNotNull { group ->
                val nodes = ArrayList<FileNode>()
                var bytes = 0L
                for (node in container.index.allFiles()) {
                    if (SmartClassifier.classify(node, container.contentIndex.textOf(node.path)) == group) {
                        nodes.add(node)
                        bytes += node.size
                    }
                }
                if (nodes.isEmpty()) null
                else GroupInfo(group, nodes.sortedByDescending { it.size }, bytes)
            }
            _state.update { it.copy(loading = false, groups = infos) }
        }
    }

    fun openGroup(group: SmartGroup) {
        _state.update { it.copy(openGroup = group) }
    }

    fun backToGroups() {
        _state.update { it.copy(openGroup = null) }
    }

    /** Destination folder path for a physical move of [group] (pure). */
    fun smartDestPath(group: SmartGroup): String {
        val base = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Apex-Organizadas",
        )
        return File(base, group.label).absolutePath
    }

    /** Moves every file of [group] into its smart folder (user-confirmed). */
    fun moveFlow(group: SmartGroup): Flow<OpProgress> = flow {
        val info = _state.value.groups.firstOrNull { it.group == group } ?: return@flow
        val destDir = File(smartDestPath(group))
        withContext(Dispatchers.IO) {
            runCatching { destDir.mkdirs() }
        }
        val dest = FileNode.forDirectory(destDir.name, destDir.absolutePath)
        var acc = OpResult()
        for (node in info.nodes) {
            acc += container.fs.move(
                node,
                dest,
                onProgress = { emit(it) },
                onConflict = container.conflicts::resolve,
            )
        }
        val parts = buildList {
            add("Movidos: ${acc.filesDone}")
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
}