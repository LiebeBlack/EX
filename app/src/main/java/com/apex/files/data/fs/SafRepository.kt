package com.apex.files.data.fs

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.apex.files.core.OpProgress
import com.apex.files.core.OpType
import com.apex.files.core.SpeedTracker
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.SortOrder
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * All operations over a SAF tree (USB-OTG or the SAF fallback) using
 * [DocumentFile]. Node identity is the content URI carried by [FileNode.uri];
 * [FileNode.path] is the virtual display path.
 */
class SafRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun document(node: FileNode): DocumentFile? = try {
        node.uri?.let { uri ->
            // Root nodes carry a TREE uri (ACTION_OPEN_DOCUMENT_TREE);
            // children carry DOCUMENT uris — each needs the right wrapper.
            if (node.isRoot) DocumentFile.fromTreeUri(context, uri)
            else DocumentFile.fromSingleUri(context, uri)
        }
    } catch (e: Exception) {
        null
    }

    fun list(dir: FileNode, showHidden: Boolean, sort: SortOrder): List<FileNode> {
        val doc = document(dir) ?: return emptyList()
        if (!doc.isDirectory) return emptyList()
        val out = ArrayList<FileNode>()
        for (child in doc.listFiles()) {
            val name = child.name ?: child.uri.lastPathSegment ?: continue
            if (!showHidden && (name.startsWith(".") || hasNomedia(child))) continue
            out.add(child.toNode(dir))
        }
        return out.sortedWith(Sorters.comparator(sort))
    }

    private fun hasNomedia(dir: DocumentFile): Boolean =
        dir.isDirectory && dir.listFiles().any { it.name == ".nomedia" }

    private fun DocumentFile.toNode(parent: FileNode): FileNode {
        val n = name ?: uri.lastPathSegment ?: "?"
        val p = if (parent.path == parent.name) n else "${parent.path}/$n"
        return if (isDirectory) {
            FileNode.forDirectory(n, p, lastModified(), uri)
        } else {
            val len = length()
            FileNode(
                name = n,
                path = p,
                isDir = false,
                size = len,
                lastModified = lastModified(),
                extension = CategoryEngine.extensionOf(n),
                category = CategoryEngine.classify(n),
                uri = uri,
            )
        }
    }

    fun openInputStream(node: FileNode): InputStream? = try {
        node.uri?.let { resolver.openInputStream(it) }
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- delete

    suspend fun delete(node: FileNode, onProgress: suspend (OpProgress) -> Unit) = withContext(Dispatchers.IO) {
        val doc = document(node) ?: return@withContext
        val total = countFiles(doc)
        val sink = ProgressSink(OpType.DELETE, onProgress)
        deleteRecursive(doc, sink, total)
    }

    private suspend fun deleteRecursive(doc: DocumentFile, sink: ProgressSink, total: Int): Int {
        var done = 0
        try {
            if (doc.isDirectory) {
                for (c in doc.listFiles()) done += deleteRecursive(c, sink, total)
                doc.delete()
            } else {
                if (doc.delete()) {
                    done = 1
                    sink.emit(1, null, done, total, doc.name ?: "")
                }
            }
        } catch (e: Exception) {
            // Unreadable subtree: try to delete what we can.
            try { doc.delete() } catch (ignored: Exception) {}
        }
        return done
    }

    // ------------------------------------------------------------------ copy

    suspend fun copy(src: FileNode, destDir: FileNode, onProgress: suspend (OpProgress) -> Unit) =
        withContext(Dispatchers.IO) {
            val srcDoc = document(src) ?: return@withContext
            val destDoc = document(destDir) ?: return@withContext
            if (!destDoc.isDirectory) return@withContext
            val total = sizeOf(src)
            val sink = ProgressSink(OpType.COPY, onProgress)
            copyRecursive(srcDoc, destDoc, sink, total)
        }

    private suspend fun copyRecursive(src: DocumentFile, destDir: DocumentFile, sink: ProgressSink, total: Long) {
        if (src.isDirectory) {
            val dest = destDir.createDirectory(uniqueDirName(destDir, src.name ?: "carpeta")) ?: return
            for (c in src.listFiles()) copyRecursive(c, dest, sink, total)
        } else {
            val mime = src.type ?: "application/octet-stream"
            val dest = destDir.createFile(mime, uniqueDirName(destDir, src.name ?: "archivo")) ?: return
            copyStream(
                resolver.openInputStream(src.uri),
                resolver.openOutputStream(dest.uri),
                sink,
                total,
                src.name ?: "",
            )
        }
    }

    // ------------------------------------------------------------------ move

    suspend fun move(src: FileNode, destDir: FileNode, onProgress: suspend (OpProgress) -> Unit) =
        withContext(Dispatchers.IO) {
            val srcDoc = document(src) ?: return@withContext
            val destDoc = document(destDir) ?: return@withContext
            if (!destDoc.isDirectory) return@withContext
            val parent = srcDoc.parentFile
            // Same tree, same parent: a rename is a move.
            if (parent != null && parent.uri == destDoc.uri && srcDoc.renameTo(uniqueDirName(destDoc, src.name))) {
                return@withContext
            }
            val total = sizeOf(src)
            val sink = ProgressSink(OpType.MOVE, onProgress)
            copyRecursive(srcDoc, destDoc, sink, total)
            deleteRecursive(srcDoc, ProgressSink(OpType.MOVE, onProgress), countFiles(srcDoc))
        }

    // ----------------------------------------------------------- other ops

    suspend fun rename(node: FileNode, newName: String): FileNode? = withContext(Dispatchers.IO) {
        val doc = document(node) ?: return@withContext null
        if (!doc.renameTo(newName)) return@withContext null
        val parentPath = node.path.substringBeforeLast('/', node.path)
        val n = newName
        val p = if (parentPath == node.path) n else "$parentPath/$n"
        if (node.isDir) FileNode.forDirectory(n, p, doc.lastModified(), doc.uri)
        else FileNode(n, p, false, doc.length(), doc.lastModified(), CategoryEngine.extensionOf(n), CategoryEngine.classify(n), doc.uri)
    }

    suspend fun createDirectory(parent: FileNode, name: String): FileNode? = withContext(Dispatchers.IO) {
        val doc = document(parent) ?: return@withContext null
        val created = doc.createDirectory(uniqueDirName(doc, name)) ?: return@withContext null
        val p = if (parent.path == parent.name) name else "${parent.path}/$name"
        FileNode.forDirectory(name, p, created.lastModified(), created.uri)
    }

    suspend fun sizeOf(node: FileNode): Long = withContext(Dispatchers.IO) {
        val doc = document(node) ?: return@withContext 0L
        sizeOfDoc(doc)
    }

    private fun sizeOfDoc(doc: DocumentFile): Long {
        if (!doc.isDirectory) return doc.length().coerceAtLeast(0L)
        var sum = 0L
        for (c in doc.listFiles()) sum += sizeOfDoc(c)
        return sum
    }

    suspend fun countEntries(node: FileNode): CountResult = withContext(Dispatchers.IO) {
        val doc = document(node) ?: return@withContext CountResult(0, 0)
        countDoc(doc)
    }

    private fun countDoc(doc: DocumentFile): CountResult {
        if (!doc.isDirectory) return CountResult(1, 0)
        var files = 0
        var dirs = 0
        for (c in doc.listFiles()) {
            if (c.isDirectory) {
                dirs++
                val r = countDoc(c)
                files += r.files
                dirs += r.dirs
            } else {
                files++
            }
        }
        return CountResult(files, dirs)
    }

    private fun countFiles(doc: DocumentFile): Int = countDoc(doc).files

    private fun uniqueDirName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        var candidate = "$base ($i)$ext"
        while (dir.findFile(candidate) != null) {
            i++
            candidate = "$base ($i)$ext"
        }
        return candidate
    }

    private suspend fun copyStream(
        src: InputStream?,
        dest: OutputStream?,
        sink: ProgressSink,
        total: Long,
        name: String,
    ) {
        if (src == null || dest == null) return
        val buffer = ByteArray(64 * 1024)
        var done = 0L
        try {
            src.use { input ->
                dest.use { output ->
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            done += read
                            sink.emit(done, total, 1, null, name)
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            sink.emit(done, total, 1, null, name)
        }
    }

    /** Counts files (used for delete progress). */
    suspend fun countFiles(node: FileNode): Int = countEntries(node).files

    private class ProgressSink(
        private val type: OpType,
        private val onProgress: suspend (OpProgress) -> Unit,
    ) {
        private val tracker = SpeedTracker()
        suspend fun emit(bytesDone: Long, bytesTotal: Long?, filesDone: Int = 0, filesTotal: Int? = null, current: String = "") {
            val speed = tracker.update(bytesDone)
            onProgress(OpProgress(type, bytesDone, bytesTotal, filesDone, filesTotal, current, speed))
        }
    }
}

data class CountResult(val files: Int, val dirs: Int)

/** Shared sorting: directories first, then the requested key. */
object Sorters {
    fun comparator(sort: SortOrder): Comparator<FileNode> = Comparator { a, b ->
        when {
            a.isDir != b.isDir -> if (a.isDir) -1 else 1
            else -> when (sort) {
                SortOrder.NAME -> a.name.lowercase().compareTo(b.name.lowercase())
                SortOrder.SIZE -> b.size.compareTo(a.size)
                SortOrder.DATE -> b.lastModified.compareTo(a.lastModified)
            }
        }
    }
}