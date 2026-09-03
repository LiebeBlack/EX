package com.apex.files.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.Location
import java.io.File

/** Browsable volumes: internal storage, removable SD, and USB-OTG SAF trees. */
class DrivesRepository(private val context: Context) {

    data class Volume(
        val key: String,
        val name: String,
        val path: String?,
        val removable: Boolean,
        val safUri: Uri? = null,
    ) {
        val location: Location
            get() = if (safUri != null) Location.Saf(safUri, name) else Location.Fs(File(path!!))
    }

    private val prefs = context.getSharedPreferences("apex_drives", Context.MODE_PRIVATE)

    fun volumes(): List<Volume> {
        val out = ArrayList<Volume>()
        val internal = Paths.internalRoot()
        out.add(
            Volume(
                key = "internal",
                name = "Almacenamiento interno",
                path = internal.absolutePath,
                removable = false,
            )
        )
        for (f in Paths.removableRoots()) {
            out.add(
                Volume(
                    key = "fs:${f.absolutePath}",
                    name = f.name,
                    path = f.absolutePath,
                    removable = true,
                )
            )
        }
        for ((uri, name) in safTrees()) {
            out.add(
                Volume(
                    key = "saf:$uri",
                    name = name,
                    path = null,
                    removable = true,
                    safUri = uri,
                )
            )
        }
        return out
    }

    /** Persists a granted ACTION_OPEN_DOCUMENT_TREE URI for future sessions. */
    fun addSafTree(uri: Uri, name: String) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
            // Permission may already be held; still record the tree.
        }
        val list = prefs.getStringSet(KEY_TREES, emptySet())!!.toMutableSet()
        list.add("$uri|$name")
        prefs.edit().putStringSet(KEY_TREES, list).apply()
    }

    fun removeSafTree(uri: Uri) {
        val prefix = "$uri|"
        val list = prefs.getStringSet(KEY_TREES, emptySet())!!
            .filterNot { it.startsWith(prefix) }
            .toSet()
        prefs.edit().putStringSet(KEY_TREES, list).apply()
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
            // ignore
        }
    }

    fun safTrees(): List<Pair<Uri, String>> {
        val held = context.contentResolver.persistedUriPermissions
            .map { it.uri.toString() }
            .toSet()
        return prefs.getStringSet(KEY_TREES, emptySet())!!
            .mapNotNull { entry ->
                val idx = entry.indexOf('|')
                if (idx <= 0) return@mapNotNull null
                val uriStr = entry.substring(0, idx)
                val name = entry.substring(idx + 1)
                if (uriStr in held) Uri.parse(uriStr) to name else null
            }
            .sortedBy { it.second }
    }

    private companion object {
        const val KEY_TREES = "saf_trees"
    }
}