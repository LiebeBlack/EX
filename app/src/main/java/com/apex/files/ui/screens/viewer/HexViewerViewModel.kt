package com.apex.files.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.HexFormatter
import com.apex.files.data.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bounded hex viewer: reads the file in windows (never the whole file),
 * formats them with [HexFormatter] and offers "load more". Works for any
 * file, including SAF-backed ones (streamed via [AppContainer.fs]).
 */
class HexViewerViewModel(
    private val container: AppContainer,
    val node: FileNode,
) : ViewModel() {

    companion object {
        /** Bytes loaded per window. */
        const val WINDOW = 64 * 1024
        /** Max bytes held in memory (~4 MB), then loading stops with a hint. */
        const val MAX_TOTAL = 4 * 1024 * 1024
    }

    data class UiState(
        val lines: List<String> = emptyList(),
        val loadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val loading: Boolean = true,
        val truncated: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState(totalBytes = node.size))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadMore()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.truncated) return
        if (s.loadedBytes >= MAX_TOTAL) {
            _state.update { it.copy(truncated = true) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val stream = container.fs.openInputStream(node) ?: return@withContext null
                try {
                    stream.use { input ->
                        input.skip(s.loadedBytes)
                        val want = minOf(WINDOW.toLong(), MAX_TOTAL - s.loadedBytes).toInt()
                        val bytes = ByteArray(want)
                        var read = 0
                        while (read < bytes.size) {
                            val n = input.read(bytes, read, bytes.size - read)
                            if (n < 0) break
                            read += n
                        }
                        bytes.copyOf(read)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (result == null) {
                _state.update { it.copy(loading = false, error = "No se pudo leer el archivo") }
                return@launch
            }
            val lines = HexFormatter.formatWindow(s.loadedBytes, result)
            _state.update {
                val total = s.loadedBytes + result.size
                it.copy(
                    lines = it.lines + lines,
                    loadedBytes = total,
                    totalBytes = if (it.totalBytes <= 0L) total else it.totalBytes,
                    loading = false,
                    truncated = result.size < WINDOW.toInt() || total >= MAX_TOTAL,
                )
            }
        }
    }
}