package com.apex.files.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.OpProgress
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import com.apex.files.tools.ApkScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ApkViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val scanning: Boolean = false,
        val currentPath: String = "",
        val apks: List<ApkScanner.ApkInfo> = emptyList(),
        val selection: Set<String> = emptySet(),
        val done: Boolean = false,
    ) {
        val notInstalled: List<ApkScanner.ApkInfo>
            get() = apks.filter { it.installed == false }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, done = false, apks = emptyList(), selection = emptySet()) }
        viewModelScope.launch {
            val root = container.fs.rootNode(Location.Fs(Paths.internalRoot()))
            container.apkScanner.scan(root).collect { scan ->
                _state.update {
                    it.copy(
                        currentPath = scan.currentPath,
                        apks = if (scan.done) scan.apks else it.apks,
                        done = scan.done,
                        scanning = !scan.done,
                    )
                }
            }
        }
    }

    fun toggleSelect(path: String) {
        _state.update { s ->
            val sel = s.selection.toMutableSet()
            if (!sel.add(path)) sel.remove(path)
            s.copy(selection = sel)
        }
    }

    fun selectNotInstalled() {
        _state.update {
            it.copy(selection = it.notInstalled.map { a -> a.node.path }.toSet())
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun deleteFlow(): Flow<OpProgress> = flow {
        val targets = _state.value.apks
            .map { it.node }
            .filter { it.path in _state.value.selection }
        for (node in targets) container.fs.delete(node) { emit(it) }
    }

    fun reset() {
        _state.update { it.copy(done = false, apks = emptyList(), selection = emptySet(), scanning = false) }
    }
}