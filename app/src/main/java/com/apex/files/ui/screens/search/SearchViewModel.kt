package com.apex.files.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.SearchFilters
import com.apex.files.data.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val query: String = "",
        val sizeBand: SearchFilters.SizeBand? = null,
        val dateRange: SearchFilters.DateRange? = null,
        val extFilter: String = "",
        val results: List<FileNode> = emptyList(),
        val searching: Boolean = false,
        val indexed: Int = container.index.size,
    )

    private val _state = MutableStateFlow(UiState(indexed = container.index.size))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val triggers = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            triggers.debounce(200).collect {
                performSearch()
            }
        }
    }

    private fun rerun() {
        _state.update { it.copy(searching = true) }
        triggers.value++
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        rerun()
    }

    fun toggleSize(band: SearchFilters.SizeBand) {
        _state.update { it.copy(sizeBand = if (it.sizeBand == band) null else band) }
        rerun()
    }

    fun toggleDate(range: SearchFilters.DateRange) {
        _state.update { it.copy(dateRange = if (it.dateRange == range) null else range) }
        rerun()
    }

    fun setExtFilter(raw: String) {
        _state.update { it.copy(extFilter = raw) }
        rerun()
    }

    fun refreshIndex() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.index.rebuild(container.settings.showHidden.value)
            }
            _state.update { it.copy(indexed = container.index.size) }
            performSearch()
        }
    }

    private suspend fun performSearch() {
        val s = _state.value
        val wildcard = s.extFilter.trim().let { w ->
            when {
                w.isEmpty() -> null
                w.startsWith("*.") -> w
                w.startsWith(".") -> "*$w"
                else -> "*.$w"
            }
        }
        val results = container.index.search(
            query = s.query,
            sizeBand = s.sizeBand,
            dateRange = s.dateRange,
            extFilter = wildcard,
            limit = 400,
        )
        _state.update { it.copy(results = results, searching = false, indexed = container.index.size) }
    }
}