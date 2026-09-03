package com.apex.files.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.model.FileNode
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Memory-bounded streaming text reader: the file is never loaded fully into
 * RAM. A fixed window of lines is held; "Cargar más" advances the window.
 */
class TextViewerViewModel(
    private val container: AppContainer,
    val node: FileNode,
) : ViewModel() {

    data class UiState(
        val lines: List<String> = emptyList(),
        val baseLine: Int = 0,
        val totalLines: Long? = null,
        val encoding: String = "UTF-8",
        val truncated: Boolean = false,
        val loading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { loadInitial() }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.truncated) return
        val next = s.baseLine + s.lines.size
        viewModelScope.launch(Dispatchers.IO) {
            val lines = readWindowFrom(next, WINDOW)
            val total = _state.value.totalLines
            _state.update {
                it.copy(
                    lines = lines,
                    baseLine = next,
                    truncated = total == null || next + lines.size < total,
                )
            }
        }
    }

    fun backToStart() {
        viewModelScope.launch { loadInitial() }
    }

    private suspend fun loadInitial() {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val head = open().use { stream ->
                val buffer = ByteArray(64 * 1024)
                val n = readSome(stream, buffer)
                Pair(buffer, n)
            }
            val encoding = detectEncoding(head.first, head.second)
            val total = open().use { countLines(it) }
            val lines = open().use { readWindow(it, WINDOW) }
            _state.update {
                it.copy(
                    lines = lines,
                    baseLine = 0,
                    totalLines = total,
                    encoding = encoding,
                    truncated = total != null && lines.size < total,
                    loading = false,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Error", loading = false) }
        }
    }

    private fun open(): InputStream =
        container.fs.openInputStream(node) ?: throw IOException("No se pudo abrir el archivo")

    // ------------------------------------------------------------ encoding

    private fun detectEncoding(head: ByteArray, n: Int): String {
        if (n >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()) return "UTF-8"
        if (n >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) return "UTF-16LE"
        return if (looksLikeUtf8(head, n)) "UTF-8" else "ISO-8859-1"
    }

    private fun looksLikeUtf8(bytes: ByteArray, n: Int): Boolean {
        var i = 0
        while (i < n) {
            val b = bytes[i].toInt() and 0xFF
            i++
            if (b < 0x80) continue
            var continuation = when {
                b in 0xC2..0xDF -> 1
                b in 0xE0..0xEF -> 2
                b in 0xF0..0xF4 -> 3
                else -> return false
            }
            while (continuation > 0 && i < n) {
                val c = bytes[i].toInt() and 0xFF
                if (c !in 0x80..0xBF) return false
                i++
                continuation--
            }
        }
        return true
    }

    // -------------------------------------------------------------- count

    private fun countLines(stream: InputStream): Long {
        var count = 0L
        var lastByte = -1
        var sawAny = false
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (read > 0) sawAny = true
            for (i in 0 until read) {
                val b = buffer[i].toInt() and 0xFF
                if (b == '\n'.code) count++
                lastByte = b
            }
        }
        if (sawAny && lastByte != '\n'.code) count++
        return count
    }

    // -------------------------------------------------------------- read

    private fun readWindow(stream: InputStream, maxLines: Int): List<String> {
        val charset = charsetFor(_state.value.encoding)
        val reader = BufferedReader(InputStreamReader(stream, charset), 64 * 1024)
        val out = ArrayList<String>(maxLines)
        while (out.size < maxLines) {
            val line = reader.readLine() ?: break
            out.add(line.removePrefix("\uFEFF"))
        }
        return out
    }

    private fun readWindowFrom(lineIndex: Int, maxLines: Int): List<String> {
        val stream = open()
        return stream.use { s ->
            val charset = charsetFor(_state.value.encoding)
            val reader = BufferedReader(InputStreamReader(s, charset), 64 * 1024)
            var skipped = 0
            while (skipped < lineIndex) {
                if (reader.readLine() == null) break
                skipped++
            }
            val out = ArrayList<String>(maxLines)
            while (out.size < maxLines) {
                val line = reader.readLine() ?: break
                out.add(line.removePrefix("\uFEFF"))
            }
            out
        }
    }

    private fun charsetFor(encoding: String): java.nio.charset.Charset = try {
        java.nio.charset.Charset.forName(encoding)
    } catch (e: Exception) {
        Charsets.UTF_8
    }

    private fun readSome(stream: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = stream.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private companion object {
        const val WINDOW = 20_000
    }
}