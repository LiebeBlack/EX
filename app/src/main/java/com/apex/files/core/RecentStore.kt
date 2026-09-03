package com.apex.files.core

import android.content.Context
import android.content.SharedPreferences
import com.apex.files.data.fs.CategoryEngine
import com.apex.files.data.model.FileNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A file opened recently, newest first. */
data class RecentEntry(
    val node: FileNode,
    val openedAt: Long,
)

/**
 * Persisted, capped history of opened files (path / name / size / modified /
 * optional SAF uri). Written to app-private SharedPreferences via org.json
 * (part of the Android platform — no extra dependencies).
 */
class RecentStore(context: Context) {

    companion object {
        const val CAP = 20
        private const val KEY = "recent_v1"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("apex_recents", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<RecentEntry>> = _items.asStateFlow()

    /** Moves [node] to the top of the history (deduplicated by path). */
    fun record(node: FileNode) {
        if (node.isDir) return
        val updated = buildList {
            add(RecentEntry(node, System.currentTimeMillis()))
            for (e in _items.value) {
                if (e.node.path != node.path) add(e)
                if (size >= CAP) break
            }
        }
        _items.value = updated
        persist(updated)
    }

    fun clear() {
        _items.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun persist(items: List<RecentEntry>) {
        val arr = JSONArray()
        for (e in items) {
            arr.put(
                JSONObject()
                    .put("p", e.node.path)
                    .put("n", e.node.name)
                    .put("s", e.node.size)
                    .put("m", e.node.lastModified)
                    .put("t", e.openedAt)
                    .put("u", e.node.uri?.toString() ?: "")
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<RecentEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<RecentEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = o.optString("p")
                val name = o.optString("n")
                if (path.isEmpty() || name.isEmpty()) continue
                val uri = o.optString("u").takeIf { it.isNotEmpty() }?.let(android.net.Uri::parse)
                out.add(
                    RecentEntry(
                        node = FileNode(
                            name = name,
                            path = path,
                            isDir = false,
                            size = o.optLong("s", 0L).coerceAtLeast(0L),
                            lastModified = o.optLong("m", 0L),
                            extension = CategoryEngine.extensionOf(name),
                            category = CategoryEngine.classify(name),
                            uri = uri,
                        ),
                        openedAt = o.optLong("t", 0L),
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}