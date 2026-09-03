package com.apex.files.core

import android.content.Context
import android.content.SharedPreferences
import com.apex.files.data.fs.CategoryEngine
import com.apex.files.data.model.FileNode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A user-starred folder or file. */
data class Favorite(val node: FileNode)

/**
 * Persisted "Favoritos" (starred) folders/files, shown on the Home screen
 * for one-tap access. Stored as JSON in app-private SharedPreferences;
 * folders keep their SAF uri when applicable so they stay navigable.
 */
class FavoritesStore(context: Context) {

    private companion object {
        const val KEY = "favorites_v1"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("apex_favorites", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Favorite>> = _items.asStateFlow()

    fun isFavorite(path: String): Boolean = _items.value.any { it.node.path == path }

    /** Adds or removes [node]; returns true when it is now a favorite. */
    fun toggle(node: FileNode): Boolean {
        val current = _items.value.toMutableList()
        val idx = current.indexOfFirst { it.node.path == node.path }
        if (idx >= 0) current.removeAt(idx) else current.add(Favorite(node))
        _items.value = current
        persist(current)
        return idx < 0
    }

    fun remove(path: String) {
        val current = _items.value.filterNot { it.node.path == path }
        _items.value = current
        persist(current)
    }

    /** Drops entries whose files no longer exist (SAF nodes are kept). */
    suspend fun prune() = withContext(Dispatchers.IO) {
        val updated = _items.value.filter { f ->
            f.node.uri != null || File(f.node.path).exists()
        }
        if (updated.size != _items.value.size) {
            _items.value = updated
            persist(updated)
        }
    }

    private fun persist(items: List<Favorite>) {
        val arr = JSONArray()
        for (f in items) {
            arr.put(
                JSONObject()
                    .put("p", f.node.path)
                    .put("n", f.node.name)
                    .put("d", f.node.isDir)
                    .put("m", f.node.lastModified)
                    .put("s", if (f.node.isDir) 0L else f.node.size)
                    .put("u", f.node.uri?.toString() ?: "")
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<Favorite> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Favorite>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = o.optString("p")
                val name = o.optString("n")
                if (path.isEmpty() || name.isEmpty()) continue
                val uri = o.optString("u").takeIf { it.isNotEmpty() }?.let(android.net.Uri::parse)
                val isDir = o.optBoolean("d", false)
                out.add(
                    Favorite(
                        node = if (isDir) {
                            FileNode.forDirectory(
                                name = name,
                                path = path,
                                lastModified = o.optLong("m", 0L),
                                uri = uri,
                            )
                        } else {
                            FileNode(
                                name = name,
                                path = path,
                                isDir = false,
                                size = o.optLong("s", 0L),
                                lastModified = o.optLong("m", 0L),
                                extension = CategoryEngine.extensionOf(name),
                                category = CategoryEngine.classify(name),
                                uri = uri,
                            )
                        }
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}