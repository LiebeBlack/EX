package com.apex.files.ui.screens.sqlite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.SqliteRepository
import com.apex.files.data.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SqliteViewModel(
    private val container: AppContainer,
    val node: FileNode,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val objects: List<Pair<String, String>> = emptyList(),
        val table: SqliteRepository.TableInfo? = null,
        val query: String = "",
        val queryResult: SqliteRepository.QueryResult? = null,
        val querying: Boolean = false,
    ) {
        val showingTable: Boolean get() = table != null
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var handle: SqliteRepository.Handle? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val h = container.sqlite.open(node)
            handle = h
            if (!h.isOpen) {
                _state.update { it.copy(loading = false, error = h.error ?: "No se pudo abrir la base de datos") }
                return@launch
            }
            val objects = runCatching { container.sqlite.listObjects(h.db!!) }.getOrDefault(emptyList())
            _state.update { it.copy(loading = false, objects = objects) }
        }
    }

    fun openTable(name: String, kind: String) {
        val h = handle ?: return
        if (!h.isOpen) return
        _state.update { it.copy(table = null, query = "", queryResult = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val info = runCatching { container.sqlite.tableInfo(h.db!!, name, kind) }.getOrNull()
            if (info != null) {
                _state.update { it.copy(table = info) }
            } else {
                _state.update { it.copy(error = "No se pudo leer la tabla `$name`") }
            }
        }
    }

    fun backToTables() {
        _state.update { it.copy(table = null, query = "", queryResult = null) }
    }

    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text) }
    }

    fun runQuery() {
        val h = handle ?: return
        if (!h.isOpen) return
        val sql = _state.value.query.trim()
        if (sql.isEmpty()) return
        _state.update { it.copy(querying = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = container.sqlite.runQuery(h.db!!, sql)
            _state.update { it.copy(queryResult = result, querying = false) }
        }
    }

    fun clearQuery() {
        _state.update { it.copy(query = "", queryResult = null) }
    }

    fun consumeError(): String? {
        val e = _state.value.error
        _state.update { it.copy(error = null) }
        return e
    }

    override fun onCleared() {
        handle?.close()
        handle = null
    }
}
