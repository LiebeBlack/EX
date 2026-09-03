package com.apex.files.data.fs

import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode

/** Size / date / extension filter definitions shared by global search. Pure JVM. */
object SearchFilters {

    enum class SizeBand { SMALL, MEDIUM, GIANT }

    enum class DateRange { TODAY, WEEK, MONTH, YEAR }

    private const val MB = 1024L * 1024
    private const val GB = 1024L * MB

    fun matchesSize(bytes: Long, band: SizeBand): Boolean = when (band) {
        SizeBand.SMALL -> bytes < MB
        SizeBand.MEDIUM -> bytes in MB until GB
        SizeBand.GIANT -> bytes >= GB
    }

    fun matchesDate(lastModified: Long, range: DateRange, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (lastModified <= 0L) return false
        val elapsed = nowMillis - lastModified
        return when (range) {
            DateRange.TODAY -> elapsed >= 0 && elapsed < 24L * 3600_000
            DateRange.WEEK -> elapsed >= 0 && elapsed < 7L * 24 * 3600_000
            DateRange.MONTH -> elapsed >= 0 && elapsed < 31L * 24 * 3600_000
            DateRange.YEAR -> elapsed >= 0 && elapsed < 366L * 24 * 3600_000
        }
    }

    /** Matches a wildcard like "*.apk" or "*.pdf" (case-insensitive, single *). */
    fun matchesExtension(name: String, wildcard: String): Boolean {
        val w = wildcard.trim().lowercase()
        if (w.isEmpty()) return true
        if (!w.contains('*')) return name.lowercase().endsWith(".$w")
        if (w.count { it == '*' } > 1) return false
        val star = w.indexOf('*')
        val prefix = w.substring(0, star)
        val suffix = w.substring(star + 1)
        val lower = name.lowercase()
        return lower.startsWith(prefix) && lower.endsWith(suffix) && lower.length >= prefix.length + suffix.length
    }
}

/** Bounded in-memory index of FileNodes for instant search. Not a StateFlow: */
/** it is queried on demand, never collected wholesale (keeps RAM tiny). */
class MemoryIndex {

    companion object {
        const val CAP = 150_000
        const val MAX_DEPTH = 12
    }

    private val map = java.util.concurrent.ConcurrentHashMap<String, FileNode>()
    @Volatile
    private var count: Int = 0

    val size: Int get() = count

    fun putAll(nodes: Collection<FileNode>) {
        for (n in nodes) {
            if (count >= CAP) break
            if (map.putIfAbsent(n.path, n) == null) count++
        }
    }

    fun put(node: FileNode) {
        if (count >= CAP) return
        if (map.putIfAbsent(node.path, node) == null) count++
    }

    fun remove(path: String) {
        if (map.remove(path) != null) count--
    }

    fun clear() {
        map.clear()
        count = 0
    }

    fun search(
        query: String,
        sizeBand: SearchFilters.SizeBand? = null,
        dateRange: SearchFilters.DateRange? = null,
        extFilter: String? = null,
        category: Category? = null,
        limit: Int = 250,
    ): List<FileNode> {
        val q = query.trim()
        val results = ArrayList<FileNode>(minOf(limit, 512))
        for (node in map.values) {
            if (results.size >= limit) break
            if (node.isDir) continue
            if (category != null && node.category != category) continue
            if (q.isNotEmpty() && !node.name.contains(q, ignoreCase = true)) continue
            if (sizeBand != null && !SearchFilters.matchesSize(node.size, sizeBand)) continue
            if (dateRange != null && !SearchFilters.matchesDate(node.lastModified, dateRange)) continue
            if (extFilter != null && !SearchFilters.matchesExtension(node.name, extFilter)) continue
            results.add(node)
        }
        // Best-effort relevance: exact prefix matches first.
        if (q.isNotEmpty()) {
            results.sortBy { if (it.name.startsWith(q, ignoreCase = true)) 0 else 1 }
        }
        return results
    }

    fun countByCategory(): Map<Category, Int> {
        val counts = HashMap<Category, Int>()
        for (node in map.values) {
            if (node.isDir) continue
            counts[node.category] = (counts[node.category] ?: 0) + 1
        }
        return counts
    }

    /** Rebuilds the index from every volume root on [Dispatchers.IO]. */
    suspend fun rebuild(showHidden: Boolean) {
        clear()
        for (root in roots()) {
            walk(root, showHidden, depth = 0)
            if (count >= CAP) break
        }
    }

    private fun roots(): List<FileNode> {
        val nodes = ArrayList<FileNode>()
        val internal = Paths.internalRoot()
        if (internal.exists()) {
            nodes.add(FileNode.forDirectory(internal.name, internal.absolutePath, internal.lastModified(), isRoot = true))
        }
        for (f in Paths.removableRoots()) {
            nodes.add(FileNode.forDirectory(f.name, f.absolutePath, f.lastModified(), isRoot = true))
        }
        return nodes
    }

    private fun walk(dir: FileNode, showHidden: Boolean, depth: Int) {
        if (count >= CAP || depth > MAX_DEPTH) return
        val file = File(dir.path)
        val children = file.listFiles() ?: return
        for (child in children) {
            if (count >= CAP) break
            if (Paths.isExcluded(child)) continue
            if (!showHidden && child.name.startsWith(".")) continue
            val node = if (child.isDirectory) {
                FileNode.forDirectory(child.name, child.absolutePath, child.lastModified())
            } else {
                FileNode(
                    name = child.name,
                    path = child.absolutePath,
                    isDir = false,
                    size = child.length(),
                    lastModified = child.lastModified(),
                    extension = CategoryEngine.extensionOf(child.name),
                    category = CategoryEngine.classify(child.name),
                )
            }
            put(node)
            if (child.isDirectory) {
                walk(node, showHidden, depth + 1)
            }
        }
    }
}