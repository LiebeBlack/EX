package com.apex.files.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.StructuredFormat
import com.apex.files.data.model.FileNode
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        val editable: Boolean = false,
        val editing: Boolean = false,
        val draft: String = "",
        val saving: Boolean = false,
        val notice: String? = null,
        // Structured documents (.xml/.json): can be pretty-printed.
        val structured: Boolean = false,
        val formatting: Boolean = false,
        // Search state (line indexes are 0-based across the whole file).
        val searchQuery: String = "",
        val searching: Boolean = false,
        val matches: List<Int> = emptyList(),
        val matchIndex: Int = -1,
        val matchesTruncated: Boolean = false,
        /** Soft-wrap each line instead of horizontal truncation. */
        val wrap: Boolean = false,
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

    /** Moves the visible window one page backwards. */
    fun windowUp() {
        val s = _state.value
        if (s.baseLine <= 0) return
        val prev = (s.baseLine - WINDOW).coerceAtLeast(0)
        viewModelScope.launch(Dispatchers.IO) {
            val lines = readWindowFrom(prev, WINDOW)
            val total = _state.value.totalLines
            _state.update {
                it.copy(
                    lines = lines,
                    baseLine = prev,
                    truncated = total == null || prev + lines.size < total,
                )
            }
        }
    }

    fun backToStart() {
        viewModelScope.launch { loadInitial() }
    }

    // ------------------------------------------------------------ search

    private var searchJob: Job? = null

    /**
     * Scans the whole file (streaming, bounded matches) for [raw] and jumps
     * to the first match. Results are 0-based absolute line indexes.
     */
    fun startSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        _state.update { it.copy(searchQuery = q, searching = true, matches = emptyList(), matchIndex = -1, matchesTruncated = false) }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val found = ArrayList<Int>()
            try {
                open().use { stream ->
                    val reader = BufferedReader(InputStreamReader(stream, charsetFor(_state.value.encoding)), 64 * 1024)
                    var lineIdx = 0
                    var truncatedMatches = false
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.contains(q, ignoreCase = true)) {
                            if (found.size < MAX_MATCHES) {
                                found.add(lineIdx)
                            } else {
                                truncatedMatches = true
                                break
                            }
                        }
                        lineIdx++
                    }
                    _state.update {
                        it.copy(
                            searching = false,
                            matches = found,
                            matchIndex = if (found.isEmpty()) -1 else 0,
                            matchesTruncated = truncatedMatches,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, notice = "No se pudo buscar en el archivo") }
            }
            if (_state.value.matchIndex >= 0) jumpToMatch(_state.value.matchIndex)
        }
    }

    fun nextMatch() {
        val s = _state.value
        if (s.matches.isEmpty()) return
        jumpToMatch((s.matchIndex + 1) % s.matches.size)
    }

    fun prevMatch() {
        val s = _state.value
        if (s.matches.isEmpty()) return
        jumpToMatch((s.matchIndex - 1 + s.matches.size) % s.matches.size)
    }

    private fun jumpToMatch(match: Int) {
        val s = _state.value
        val line = s.matches.getOrNull(match) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val base = (line - WINDOW / 2).coerceAtLeast(0)
            val lines = readWindowFrom(base, WINDOW)
            val total = _state.value.totalLines
            _state.update {
                it.copy(
                    matchIndex = match,
                    baseLine = base,
                    lines = lines,
                    truncated = total == null || base + lines.size < total,
                )
            }
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searchQuery = "", searching = false, matches = emptyList(), matchIndex = -1, matchesTruncated = false) }
    }

    fun toggleWrap() {
        _state.update { it.copy(wrap = !it.wrap) }
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
                    truncated = lines.size < total,
                    editable = node.size in 1..MAX_EDIT_BYTES,
                    structured = node.size in 1..MAX_EDIT_BYTES &&
                        (node.extension in FORMAT_EXTS || sniffStructured(head.first, head.second, encoding)),
                    loading = false,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Error", loading = false) }
        }
    }

    // -------------------------------------------------------------- editor

    /** Loads the whole file (bounded by [MAX_EDIT_BYTES]) into the draft. */
    fun startEditing() {
        val s = _state.value
        if (!s.editable || s.editing || s.saving) return
        viewModelScope.launch(Dispatchers.IO) {
            val content = readWholeText()
            if (content == null) {
                _state.update { it.copy(notice = "No se pudo abrir para editar") }
            } else {
                _state.update { it.copy(editing = true, draft = content) }
            }
        }
    }

    /**
     * Pretty-prints the whole document when it looks like JSON or XML and
     * drops the result into the editor as a draft (Save writes it back).
     */
    fun formatStructured() {
        val s = _state.value
        if (!s.editable || !s.structured || s.editing || s.saving || s.formatting) return
        _state.update { it.copy(formatting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val content = readWholeText()
            if (content == null) {
                _state.update { it.copy(formatting = false, notice = "No se pudo leer el archivo") }
                return@launch
            }
            val kind = StructuredFormat.detect(content)
            if (kind == null) {
                _state.update { it.copy(formatting = false, notice = "No parece un documento XML ni JSON") }
                return@launch
            }
            val pretty = StructuredFormat.format(content, kind)
            if (pretty == null) {
                _state.update { it.copy(formatting = false, notice = "No se pudo formatear: el documento contiene errores") }
                return@launch
            }
            _state.update { it.copy(editing = true, draft = pretty, formatting = false) }
        }
    }

    /** Whole-file read bounded by [MAX_EDIT_BYTES]; null on failure. */
    private suspend fun readWholeText(): String? {
        val s = _state.value
        if (s.totalLines != null && node.size > MAX_EDIT_BYTES) return null
        return try {
            open().use { stream ->
                stream.readBytes().toString(charsetFor(s.encoding))
            }
        } catch (e: Exception) {
            null
        }
    }

    fun updateDraft(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun cancelEditing() {
        _state.update { it.copy(editing = false, draft = "") }
    }

    fun saveEditing() {
        val s = _state.value
        if (!s.editing || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = container.fs.saveText(node, s.draft, charsetFor(s.encoding))
            _state.update {
                it.copy(
                    editing = false,
                    draft = "",
                    saving = false,
                    notice = if (ok) "Guardado" else "No se pudo guardar",
                )
            }
            if (ok) loadInitial()
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun open(): InputStream =
        container.fs.openInputStream(node) ?: throw IOException("No se pudo abrir el archivo")

    // ------------------------------------------------------------ encoding

    /**
     * Content sniff: does the first line of the file look like structured
     * data (starts with `{`, `[` or `<`)? Enables “Formatear” even for files
     * whose extension is not on the allow-list.
     */
    private fun sniffStructured(head: ByteArray, n: Int, encoding: String): Boolean {
        val limit = minOf(n, 4096)
        var end = limit
        for (i in 0 until limit) {
            if (head[i] == '\n'.code.toByte()) {
                end = i
                break
            }
        }
        if (end == 0) return false
        val text = String(head, 0, end, charsetFor(encoding))
        return StructuredFormat.detect(text) != null
    }

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
        /** Documents the formatter can pretty-print. */
        val FORMAT_EXTS: Set<String> = setOf("json", "xml", "html", "htm", "svg", "xsd", "plist")

        const val WINDOW = 20_000

        /** Cap for search hits (the scan stops past this). */
        const val MAX_MATCHES = 2_000

        /** Files larger than this stay read-only in the viewer. */
        const val MAX_EDIT_BYTES = 2L * 1024 * 1024
    }
}