package com.apex.files.data.fs

import android.content.Context
import com.apex.files.data.model.FileNode
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Persists a snapshot of the search index in app-private storage
 * (filesDir, i.e. "datos del software") so a cold start does not re-walk
 * every volume root. Only plain-file entries are stored (path / size /
 * lastModified); names, extensions and categories are recomputed on load,
 * exactly like the live walk does.
 *
 * Files are stored one per line: path<TAB>lastModified<TAB>size, with a
 * version header. Writes are atomic (temp file + rename) and failures are
 * swallowed — a cache must never crash the app.
 */
class IndexStore(context: Context) {

    companion object {
        private const val HEADER = "APEX-INDEX-v1"
        private const val TAB = '\t'
        private const val LF = '\n'
        private const val CR = '\r'
        private const val ESCAPE = '\\'

        /** After this age the Home screen silently re-indexes in background. */
        const val AUTO_REINDEX_AFTER_MS = 60L * 60 * 1000
    }

    private val file = File(context.filesDir, "search_index_v1.txt")
    private val metaFile = File(context.filesDir, "search_index_v1.meta")

    /** Timestamp of the last successful save, or 0L when never saved. */
    fun lastSavedAtMillis(): Long =
        if (metaFile.exists()) metaFile.readText().trim().toLongOrNull() ?: 0L else 0L

    /** Returns cached entries or null when missing / corrupt / empty. Blocking: call on [Dispatchers.IO]. */
    fun load(): List<FileNode>? {
        if (!file.exists()) return null
        val out = ArrayList<FileNode>(4096)
        return try {
            BufferedReader(FileReader(file)).use { reader ->
                if (reader.readLine() != HEADER) return null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) continue
                    val firstTab = line.indexOf(TAB)
                    if (firstTab <= 0) continue
                    val secondTab = line.indexOf(TAB, firstTab + 1)
                    if (secondTab <= firstTab) continue
                    val path = unescape(line.substring(0, firstTab))
                    val lastModified = line.substring(firstTab + 1, secondTab).toLongOrNull() ?: continue
                    val size = line.substring(secondTab + 1).toLongOrNull() ?: continue
                    val name = path.substringAfterLast('/')
                    if (name.isEmpty()) continue
                    out.add(
                        FileNode(
                            name = name,
                            path = path,
                            isDir = false,
                            size = size.coerceAtLeast(0L),
                            lastModified = lastModified,
                            extension = CategoryEngine.extensionOf(name),
                            category = CategoryEngine.classify(name),
                        )
                    )
                }
            }
            if (out.isEmpty()) null else out
        } catch (e: Exception) {
            null
        }
    }

    /** Atomically replaces the snapshot. Blocking: call on [Dispatchers.IO]. */
    fun save(nodes: Collection<FileNode>) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.bufferedWriter(Charsets.UTF_8).use { w ->
                w.write(HEADER)
                w.write(LF)
                for (n in nodes) {
                    if (n.isDir) continue
                    w.write(escape(n.path))
                    w.write(TAB)
                    w.write(n.lastModified.toString())
                    w.write(TAB)
                    w.write(n.size.toString())
                    w.write(LF)
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            metaFile.writeText(System.currentTimeMillis().toString())
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
