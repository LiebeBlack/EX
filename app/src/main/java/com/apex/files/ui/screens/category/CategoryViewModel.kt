package com.apex.files.ui.screens.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val container: AppContainer,
    val category: Category,
) : ViewModel() {

    data class UiState(
        val nodes: List<FileNode> = emptyList(),
        val loading: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val nodes = if (category.isMediaCollection) {
                container.mediaStore.list(category)
            } else {
                container.index.search(
                    query = "",
                    category = category,
                    limit = 2000,
                )
            }
            _state.update { it.copy(nodes = nodes, loading = false) }
        }
    }
}