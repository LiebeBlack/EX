package com.apex.files.data.fs

import com.apex.files.data.model.FileNode
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Per-volume recycle bin (\"Papelera\"). Deleting through APEX moves
 * File-backed nodes into a hidden `.apex_trash/` folder on the same volume
 * instead of destroying them; the user can restore them later or empty the
 * bin permanently. Zero dependencies — metadata is a tiny two-line file next
 * to each trashed item.
 *
 * SAF-backed nodes cannot be relocated to the trash (the provider may not
 * allow renames), so they keep the permanent-delete path.
 *
 * Pure JVM: volume discovery is injected through [rootForPath]/[allRoots] so
 * the logic is fully unit-testable with temp directories.
 */
class TrashManager(
    private val rootForPath: (String) -> File,
    private val allRoots: () -> List<File> = { emptyList() },
) {

    companion object {
        const val TRASH_DIR = ".apex_trash"
        const val META_FILE = ".trash-meta"
        private const val STAMP_PREFIX = "deleted-"
    }

    /** One item currently sitting in the trash. */
    data class TrashEntry(
        val name: String,
        /** Absolute path the item should return to on restore (\"\" when unknown). */
        val originalPath: String,
        val trashedAt: Long,
        /** Current location of the trashed item. */
        val path: String,
        val isDir: Boolean,
    )

    fun trashRootFor(path: String): File = File(rootForPath(path), TRASH_DIR)

    /** Moves [node] into the trash (same volume, so a rename suffices). */
    suspend fun trash(node: FileNode): OpResult = withContext(Dispatchers.IO) {
        if (node.uri != null) {
            return@withContext OpResult().recordError("Los archivos SAF no se pueden enviar a la papelera")
        }
        val src = File(node.path)
        if (!src.exists()) return@withContext OpResult()
        val root = trashRootFor(node.path)
        runCatching { root.mkdirs() }
        if (!root.isDirectory) {
            return@withContext OpResult().recordError("No se pudo crear la papelera en ${root.absolutePath}")
        }
        val stamp = System.currentTimeMillis()
        val itemDir = uniqueDir(root, "$STAMP_PREFIX$stamp")
        val dest = uniqueFile(itemDir, src.name)
        if (!itemDir.mkdirs()) {
            return@withContext OpResult().recordError("No se pudo preparar la papelera")
        }
        if (!src.renameTo(dest)) {
            return@withContext OpResult().recordError("No se pudo mover ${src.name} a la papelera")
        }
        writeMeta(itemDir, src.absolutePath, stamp)
        OpResult(filesDone = 1)
    }

    /** Lists every item in the trash across all volumes, newest first. */
    suspend fun list(): List<TrashEntry> = withContext(Dispatchers.IO) {
        val out = ArrayList<TrashEntry>()
        for (root in allRoots()) {
            val trashDir = File(root, TRASH_DIR)
            if (!trashDir.isDirectory) continue
            val dirs = trashDir.listFiles { f -> f.isDirectory && f.name.startsWith(STAMP_PREFIX) }
            for (dir in dirs ?: emptyArray()) {
                val meta = readMeta(dir)
                val child = dir.listFiles()?.firstOrNull { it.name != META_FILE }
                if (child == null) continue
                out.add(
                    TrashEntry(
                        name = child.name,
                        originalPath = meta?.first ?: "",
                        trashedAt = meta?.second ?: stampOf(dir.name),
                        path = child.absolutePath,
                        isDir = child.isDirectory,
                    )
                )
            }
        }
        out.sortedByDescending { it.trashedAt }
    }

    /**
     * Moves [entry] back to its original location, recreating missing parent
     * folders. If the target name is taken, the item is restored under a
     * unique sibling name instead of failing.
     */
    suspend fun restore(entry: TrashEntry): OpResult = withContext(Dispatchers.IO) {
        val src = File(entry.path)
        if (!src.exists()) {
            return@withContext OpResult().recordError("El elemento ya no está en la papelera")
        }
        if (entry.originalPath.isBlank()) {
            return@withContext OpResult().recordError("Faltan los datos de origen; no se puede restaurar")
        }
        val target = File(entry.originalPath)
        val parent = target.parentFile
        if (parent != null) runCatching { parent.mkdirs() }
        val dest = if (target.exists()) {
            val unique = uniqueSibling(parent ?: src.parentFile, target.name)
            if (unique == null) return@withContext OpResult().recordError("No se pudo restaurar ${src.name}")
            unique
        } else {
            target
        }
        if (!src.renameTo(dest)) {
            return@withContext OpResult().recordError("No se pudo restaurar ${src.name}")
        }
        runCatching { src.parentFile?.delete() } // Drop the now-empty deleted-* folder.
        OpResult(filesDone = 1)
    }

    /** Permanently deletes a single trashed item. */
    suspend fun deletePermanently(entry: TrashEntry): OpResult = withContext(Dispatchers.IO) {
        val src = File(entry.path)
        if (!src.exists()) return@withContext OpResult()
        val acc = OpAccumulator()
        deleteRecursive(src, acc)
        runCatching { src.parentFile?.delete() }
        acc.result()
    }

    /** Permanently deletes every item in the trash of [root]'s volume. */
    suspend fun empty(root: File): OpResult = withContext(Dispatchers.IO) {
        val trashDir = File(root, TRASH_DIR)
        if (!trashDir.isDirectory) return@withContext OpResult()
        val acc = OpAccumulator()
        val children = trashDir.listFiles() ?: return@withContext acc.result()
        for (c in children) {
            ensureActive()
            deleteRecursive(c, acc)
        }
        // Drop the now-empty hidden folder too; trash() recreates it on demand.
        runCatching { trashDir.delete() }
        acc.result()
    }

    /** Total size of everything currently in the trash (files only, lazy). */
    suspend fun sizeInTrash(): Long = withContext(Dispatchers.IO) {
        var total = 0L
        for (entry in list()) {
            val f = File(entry.path)
            if (f.isFile) total += f.length()
        }
        total
    }

    private fun deleteRecursive(file: File, acc: OpAccumulator) {
        if (file.isDirectory && !Paths.isSymlink(file)) {
            for (c in file.listFiles() ?: return) deleteRecursive(c, acc)
            if (!file.delete() && acc.files > 0) acc.error("No se pudo eliminar ${file.name}")
        } else {
            if (file.delete()) acc.files++ else acc.error("No se pudo eliminar ${file.name}")
        }
    }

    // ------------------------------------------------------------ metadata

    private fun writeMeta(itemDir: File, originalPath: String, trashedAt: Long) {
        runCatching {
            val encoded = URLEncoder.encode(originalPath, "UTF-8")
            File(itemDir, META_FILE).writeText("$encoded\n$trashedAt\n", Charsets.UTF_8)
        }
    }

    /** Returns (originalPath, trashedAt) or null when the meta file is missing. */
    private fun readMeta(itemDir: File): Pair<String, Long>? {
        val meta = File(itemDir, META_FILE)
        if (!meta.isFile) return null
        return runCatching {
            val lines = meta.readLines(Charsets.UTF_8)
            if (lines.size < 2) return null
            val path = URLDecoder.decode(lines[0], "UTF-8")
            val at = lines[1].toLongOrNull() ?: 0L
            path to at
        }.getOrNull()
    }

    private fun stampOf(dirName: String): Long =
        dirName.removePrefix(STAMP_PREFIX).toLongOrNull() ?: 0L

    // ------------------------------------------------------------ helpers

    private fun uniqueDir(dir: File, name: String): File {
        var candidate = File(dir, name)
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$name-$i")
            i++
        }
        return candidate
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        do {
            candidate = File(dir, "$base ($i)$ext")
            i++
        } while (candidate.exists())
        return candidate
    }

    private fun uniqueSibling(dir: File?, name: String): File? {
        if (dir == null || !dir.isDirectory) return null
        return uniqueFile(dir, name)
    }
}