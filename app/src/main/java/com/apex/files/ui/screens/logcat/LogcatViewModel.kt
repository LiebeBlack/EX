package com.apex.files.ui.screens.logcat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * On-device logcat console. Reading the global log requires READ_LOGS, which
 * Android grants only to debuggable builds (protectionLevel "development")
 * or via ADB — on release builds [error] explains the limitation instead of
 * pretending to work.
 */
class LogcatViewModel : ViewModel() {

    enum class Level(val ch: Char, val label: String, val rank: Int) {
        V('V', "Verbose", 0),
        D('D', "Debug", 1),
        I('I', "Info", 2),
        W('W', "Aviso", 3),
        E('E', "Error", 4),
        F('F', "Fatal", 5),
    }

    data class LogLine(
        val level: Char,
        val tag: String,
        val pid: String,
        val text: String,
    )

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val lines: List<LogLine> = emptyList(),
        val truncated: Boolean = false,
        val minLevel: Level = Level.V,
        val query: String = "",
        val refreshedAt: Long = 0L,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val capturing = AtomicBoolean(false)
    private var allLines: List<LogLine> = emptyList()

    init {
        capture()
    }

    fun capture() {
        if (!capturing.compareAndSet(false, true)) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { execLogcat() }
            val error = result.exceptionOrNull()
            if (error != null) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = when (error) {
                            is SecurityException ->
                                "Permiso READ_LOGS denegado. El registro del sistema solo se puede leer en compilaciones de depuración o con el permiso concedido por ADB."
                            is java.io.IOException ->
                                "No se pudo ejecutar logcat: ${error.message ?: "error"}"
                            else -> "No se pudo leer el registro: ${error.message ?: "error"}"
                        },
                        refreshedAt = System.currentTimeMillis(),
                    )
                }
            } else {
                val (lines, truncated) = result.getOrThrow()
                allLines = lines
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        refreshedAt = System.currentTimeMillis(),
                    )
                }
            }
            applyFilters()
            capturing.set(false)
        }
    }

    fun setLevel(level: Level) {
        _state.update { it.copy(minLevel = level) }
        applyFilters()
    }

    fun setQuery(raw: String) {
        _state.update { it.copy(query = raw) }
        applyFilters()
    }

    fun clear() {
        allLines = emptyList()
        _state.update { it.copy(lines = emptyList(), truncated = false, refreshedAt = System.currentTimeMillis()) }
    }

    /** Recomputes the visible (filtered, capped) line list. */
    private fun applyFilters() {
        val s = _state.value
        val minRank = s.minLevel.rank
        val needle = s.query.trim().lowercase()
        var truncated = false
        val visible = ArrayList<LogLine>()
        for (line in allLines) {
            val rank = LEVEL_CHARS.indexOf(line.level)
            if (rank in 0 until minRank) continue
            if (needle.isNotEmpty()) {
                val hay = (line.text + " " + line.tag).lowercase()
                if (!hay.contains(needle)) continue
            }
            if (visible.size >= MAX_SHOWN) {
                truncated = true
                break
            }
            visible.add(line)
        }
        _state.update { it.copy(lines = visible, truncated = truncated) }
    }

    /** Executes `logcat -d` and parses the brief format lines. */
    private fun execLogcat(): Pair<List<LogLine>, Boolean> {
        val process = try {
            Runtime.getRuntime().exec(arrayOf("logcat", "-v", "brief", "-d", "*:V"))
        } catch (e: Exception) {
            throw e
        }
        try {
            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8), 64 * 1024)
            val out = ArrayList<LogLine>(4096)
            var truncated = false
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) continue
                val parsed = parseLine(line)
                if (out.size >= MAX_RAW) {
                    truncated = true
                    continue
                }
                out.add(parsed)
            }

            // When denied (release builds), logcat exits with a diagnostic on
            // stderr and an empty stdout — surface that as an error instead of
            // pretending there are simply no logs.
            if (out.isEmpty()) {
                val err = StringBuilder()
                val errReader = BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8), 4096)
                while (err.length < 4096) {
                    val line = errReader.readLine() ?: break
                    err.append(line).append('\n')
                }
                if (err.isNotBlank()) {
                    throw java.io.IOException(err.toString().trim().take(400))
                }
            }
            return Pair(out, truncated)
        } finally {
            runCatching { process.destroy() }
        }
    }

    private fun parseLine(line: String): LogLine {
        val match = BRIEF_PATTERN.matchEntire(line)
        if (match != null) {
            val level = match.groupValues[1][0]
            val tag = match.groupValues[2]
            val pid = match.groupValues[3]
            val text = match.groupValues[4]
            return LogLine(level, tag, pid, text)
        }
        return LogLine('?', "", "", line)
    }

    private companion object {
        const val MAX_RAW = 12_000
        const val MAX_SHOWN = 2_500
        val LEVEL_CHARS = "VDIWEF"
        val BRIEF_PATTERN = Regex("^([VDIWEF])/([^ (]+)\\s*\\(\\s*(\\d+)\\):\\s?(.*)$")
    }
}
