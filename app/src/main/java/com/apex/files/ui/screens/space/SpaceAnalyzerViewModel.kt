package com.apex.files.ui.screens.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.model.Location
import com.apex.files.tools.SpaceAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpaceAnalyzerViewModel(
    private val container: AppContainer,
    val location: Location,
) : ViewModel() {

    data class UiState(
        val scanning: Boolean = true,
        val currentPath: String = "",
        val root: SpaceAnalyzer.SpaceNode? = null,
        val current: SpaceAnalyzer.SpaceNode? = null,
        val breadcrumb: List<SpaceAnalyzer.SpaceNode> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        analyze()
    }

    fun analyze() {
        _state.update { it.copy(scanning = true, error = null, root = null, current = null, breadcrumb = emptyList()) }
        viewModelScope.launch {
            try {
                val rootNode = container.fs.rootNode(location)
                container.spaceAnalyzer.analyze(rootNode).collect { scan ->
                    _state.update {
                        it.copy(
                            currentPath = scan.currentPath,
                            root = scan.root,
                            scanning = !scan.done,
                            current = if (scan.done) scan.root else it.current,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(scanning = false, error = e.message ?: "Error") }
            }
        }
    }

    fun drill(node: SpaceAnalyzer.SpaceNode) {
        val current = _state.value.current ?: return
        if (node.children.isEmpty()) return
        _state.update {
            it.copy(breadcrumb = it.breadcrumb + current, current = node)
        }
    }

    fun up() {
        val crumbs = _state.value.breadcrumb
        if (crumbs.isEmpty()) return
        _state.update {
            it.copy(breadcrumb = crumbs.dropLast(1), current = crumbs.last())
        }
    }

    fun canGoUp(): Boolean = _state.value.breadcrumb.isNotEmpty()

    fun toRoot() {
        _state.update { it.copy(breadcrumb = emptyList(), current = it.root) }
    }
}