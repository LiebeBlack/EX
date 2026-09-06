package com.apex.files.data.search

import android.content.Context
import com.apex.files.core.PerfMetrics
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap

/** Provides normalized content text for a file path ("" when not indexed). */
fun interface ContentTextSource {
    fun textOf(path: String): String
}

/**
 * Persisted sidecar of the search index: for every indexed plain file it
 * stores the normalized content text (OCR of images/PDFs, or the raw text of
 * plain-text/code files), capped per file and in total.
 *
 * Storage is app-private (filesDir) with the same atomic-write style as
 * [com.apex.files.data.fs.IndexStore]: path<TAB>lastModified<TAB>text, with a
 * version header. Failures are swallowed — a cache must never crash the app.
 */
class ContentIndex(context: Context) : ContentTextSource {

    companion object {
        private const val HEADER = "APEX-CONTENT-v1"
        private const val TAB = '\t'
        private const val LF = '\n'
        private const val CR = '\r'
        private const val ESCAPE = '\\'

        /** Max stored text per file (4 KB of tokens is plenty for ranking). */
        const val MAX_TEXT_PER_FILE = 4096

        /** Mirrors the search index cap so the two stay in lockstep. */
        const val MAX_ENTRIES = 150_000

        /** Total text budget guard (approx. 64 MB of chars worst case). */
        const val MAX_TOTAL_TEXT = 64L * 1024 * 1024
    }

    data class Entry(val path: String, val lastModified: Long, val text: String)

    private val file = File(context.filesDir, "content_index_v2.txt")
    private val map = ConcurrentHashMap<String, Entry>()
    @Volatile
    private var dirty = false

    val size: Int get() = map.size

    /** On-disk size of the last persisted snapshot (0 when never saved). */
    fun snapshotBytes(): Long = if (file.exists()) file.length() else 0L

    fun get(path: String): Entry? = map[path]

    override fun textOf(path: String): String = map[path]?.text ?: ""

    /** Stores [text] (already normalized) for [path]; capped. */
    fun put(path: String, lastModified: Long, text: String) {
        if (map.size >= MAX_ENTRIES && !map.containsKey(path)) return
        map[path] = Entry(path, lastModified, text.take(MAX_TEXT_PER_FILE))
        dirty = true
    }

    fun remove(path: String) {
        if (map.remove(path) != null) dirty = true
    }

    fun clear() {
        if (map.isNotEmpty()) {
            map.clear()
            dirty = true
        }
    }

    fun entries(): Collection<Entry> = map.values.toList()

    /** Loads the snapshot; returns true when it was restored. Blocking: IO. */
    fun load(): Boolean {
        if (!file.exists()) return false
        return try {
            val fresh = HashMap<String, Entry>()
            BufferedReader(FileReader(file)).use { reader ->
                if (reader.readLine() != HEADER) return false
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) continue
                    val firstTab = line.indexOf(TAB)
                    if (firstTab <= 0) continue
                    val secondTab = line.indexOf(TAB, firstTab + 1)
                    if (secondTab <= firstTab) continue
                    val path = unescape(line.substring(0, firstTab))
                    val lastModified = line.substring(firstTab + 1, secondTab).toLongOrNull() ?: continue
                    val text = unescape(line.substring(secondTab + 1))
                    fresh[path] = Entry(path, lastModified, text)
                    if (fresh.size >= MAX_ENTRIES) break
                }
            }
            map.clear()
            map.putAll(fresh)
            dirty = false
            fresh.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /** Atomically replaces the snapshot. Blocking: IO. */
    fun save() {
        if (!dirty) return
        PerfMetrics.time("content.save", countOf = { map.size }) { saveEntries() }
    }

    private fun saveEntries() {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.bufferedWriter(Charsets.UTF_8).use { w ->
                w.write(HEADER)
                w.write(LF.code)
                for (e in map.values) {
                    w.write(escape(e.path))
                    w.write(TAB.code)
                    w.write(e.lastModified.toString())
                    w.write(TAB.code)
                    w.write(escape(e.text))
                    w.write(LF.code)
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            dirty = false
        } catch (e: Exception) {
            // Cache write failure is never fatal.
        }
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                ESCAPE -> sb.append("\\\\")
                TAB -> sb.append("\\t")
                LF -> sb.append("\\n")
                CR -> sb.append("\\r")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun unescape(s: String): String {
        if (s.indexOf(ESCAPE) < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == ESCAPE && i + 1 < s.length) {
                when (s[i + 1]) {
                    ESCAPE -> sb.append(ESCAPE)
                    't' -> sb.append(TAB)
                    'n' -> sb.append(LF)
                    'r' -> sb.append(CR)
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}