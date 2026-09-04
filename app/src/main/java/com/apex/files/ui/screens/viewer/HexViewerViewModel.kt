package com.apex.files.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.HexFormatter
import com.apex.files.data.model.FileNode
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bounded hex viewer: reads the file in windows (never the whole file),
 * formats them with [HexFormatter] and offers "load more" / "load all".
 * Works for any file, including SAF-backed ones (streamed via [AppContainer.fs]).
 */
class HexViewerViewModel(
    private val container: AppContainer,
    val node: FileNode,
) : ViewModel() {

    companion object {
        /** Bytes loaded per window. */
        const val WINDOW: Long = 64L * 1024
        /** Max bytes held in memory (~4 MB), then loading stops with a hint. */
        const val MAX_TOTAL: Long = 4L * 1024 * 1024
    }

    data class UiState(
        val lines: List<String> = emptyList(),
        val loadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val loading: Boolean = false,
        /** True when more data is available beyond the loaded windows. */
        val hasMore: Boolean = false,
        /** True when the in-memory cap was reached (loading stopped early). */
        val capped: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState(totalBytes = node.size))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadMore()
    }

    fun loadMore() {
        if (!canLoad()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            readNextWindow()
            _state.update { it.copy(loading = false) }
        }
    }

    /** Loads all remaining windows (up to the memory cap) in one go. */
    fun loadAll() {
        if (!canLoad()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            while (readNextWindow()) {
                // Keep reading until EOF or the memory cap.
            }
            _state.update { it.copy(loading = false) }
        }
    }

    private fun canLoad(): Boolean {
        val s = _state.value
        if (s.loading || s.capped) return false
        // hasMore starts false; only block re-loading after something was read.
        if (!s.hasMore && s.loadedBytes > 0L) return false
        if (s.loadedBytes >= MAX_TOTAL) {
            _state.update { it.copy(capped = true) }
            return false
        }
        return true
    }

    /** Reads one window; returns true when more data may remain (EOF or cap stops it). */
    private suspend fun readNextWindow(): Boolean {
        val s = _state.value
        if (s.loadedBytes >= MAX_TOTAL) {
            _state.update { it.copy(capped = true) }
            return false
        }
        val want = minOf(WINDOW, MAX_TOTAL - s.loadedBytes).toInt()
        val result = withContext(Dispatchers.IO) {
            val stream = container.fs.openInputStream(node) ?: return@withContext null
            try {
                stream.use { input ->
                    skipFully(input, s.loadedBytes)
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
            _state.update { it.copy(error = "No se pudo leer el archivo") }
            return false
        }
        val lines = HexFormatter.formatWindow(s.loadedBytes, result)
        var more = false
        _state.update {
            val total = s.loadedBytes + result.size
            val hitEof = result.size < want
            val known = it.totalBytes > 0L
            val cappedNow = it.capped || total >= MAX_TOTAL
            // With a known size, stop at the reported end; with an unknown one
            // (some SAF nodes report size 0), keep streaming until a short read
            // proves EOF, and only then record the real total.
            more = if (known) {
                !hitEof && total < it.totalBytes && !cappedNow
            } else {
                !hitEof && !cappedNow
            }
            it.copy(
                lines = it.lines + lines,
                loadedBytes = total,
                totalBytes = when {
                    known -> it.totalBytes
                    hitEof -> total
                    else -> it.totalBytes
                },
                hasMore = more,
                capped = cappedNow,
            )
        }
        return more
    }

    /** Skips [bytes] even when the stream only advances partially per call. */
    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped <= 0L) break
            remaining -= skipped
        }
    }
}