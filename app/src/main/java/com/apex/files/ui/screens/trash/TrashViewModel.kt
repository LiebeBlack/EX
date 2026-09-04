package com.apex.files.ui.screens.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.OpResult
import com.apex.files.data.fs.TrashManager
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrashViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val entries: List<TrashManager.TrashEntry> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        /** One-shot toast messages (restore / delete results). */
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val entries = try {
                container.trash.list()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Error al leer la papelera") }
                emptyList()
            }
            _state.update { it.copy(entries = entries, loading = false) }
        }
    }

    fun restore(entry: TrashManager.TrashEntry) {
        viewModelScope.launch {
            val result = container.trash.restore(entry)
            refresh()
            _state.update { it.copy(notice = summarize("Restaurado", result)) }
        }
    }

    fun deletePermanently(entry: TrashManager.TrashEntry) {
        viewModelScope.launch {
            val result = container.trash.deletePermanently(entry)
            refresh()
            _state.update { it.copy(notice = summarize("Eliminado", result)) }
        }
    }

    fun empty() {
        viewModelScope.launch {
            var result = OpResult()
            // Empty every volume's trash.
            for (root in trashRoots()) {
                result += container.trash.empty(root)
            }
            refresh()
            _state.update {
                it.copy(notice = "Papelera vaciada: ${result.filesDone} elemento(s)" +
                    if (result.errors > 0) " · ${result.errors} errores" else "")
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun trashRoots(): List<File> = buildList {
        val internal = com.apex.files.data.fs.Paths.internalRoot()
        if (internal.exists()) add(internal)
        addAll(com.apex.files.data.fs.Paths.removableRoots())
    }

    private fun summarize(verb: String, result: OpResult): String = buildString {
        append("$verb: ${result.filesDone} elemento(s)")
        if (result.errors > 0) append(" · ${result.errors} errores")
        if (result.firstError != null) append(" · ${result.firstError}")
    }
}