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
import com.apex.files.data.model.SortDirection
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
 *
 * Operations return an honest [OpResult] (partial failures are counted, never
 * reported as blind success) and pause on destination name collisions through
 * the optional [onConflict] callback.
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

    /** Resolves the provider document id of a node (for subtree guards). */
    private fun docId(node: FileNode): String? = try {
        node.uri?.let { DocumentsContract.getDocumentId(it) }
    } catch (e: Exception) {
        null
    }

    fun list(dir: FileNode, showHidden: Boolean, sort: SortOrder, direction: SortDirection = SortDirection.ASC): List<FileNode> {
        val doc = document(dir) ?: return emptyList()
        if (!doc.isDirectory) return emptyList()
        val out = ArrayList<FileNode>()
        for (child in doc.listFiles()) {
            val name = child.name ?: child.uri.lastPathSegment ?: continue
            if (!showHidden && (name.startsWith(".") || hasNomedia(child))) continue
            out.add(child.toNode(dir))
        }
        return out.sortedWith(Sorters.comparator(sort, direction))
    }

    /** A child is hidden when it is a directory carrying a `.nomedia` marker. */
    private fun hasNomedia(child: DocumentFile): Boolean = try {
        child.isDirectory && child.listFiles().any { it.name == ".nomedia" }
    } catch (e: Exception) {
        false
    }

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

    /** Opens an output stream for an existing SAF document. */
    fun openOutputStream(node: FileNode): OutputStream? = try {
        node.uri?.let { resolver.openOutputStream(it, "wt") }
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- delete

    suspend fun delete(node: FileNode, onProgress: suspend (OpProgress) -> Unit): OpResult =
        withContext(Dispatchers.IO) {
            val doc = document(node) ?: return@withContext OpResult()
            val total = countFiles(doc)
            val sink = ProgressSink(OpType.DELETE, onProgress)
            val acc = OpAccumulator()
            deleteRecursive(doc, sink, total, acc)
            acc.result()
        }

    private suspend fun deleteRecursive(doc: DocumentFile, sink: ProgressSink, total: Int, acc: OpAccumulator) {
        try {
            if (doc.isDirectory) {
                for (c in doc.listFiles()) deleteRecursive(c, sink, total, acc)
                if (!doc.delete() && acc.files > 0) acc.error("No se pudo eliminar la carpeta ${doc.name.orEmpty()}")
            } else {
                if (doc.delete()) {
                    acc.files++
                    sink.emit(1L, null, acc.files, total, doc.name ?: "")
                } else {
                    acc.error("No se pudo eliminar ${doc.name.orEmpty()}")
                }
            }
        } catch (e: Exception) {
            acc.error("No se pudo eliminar ${doc.name.orEmpty()}: ${e.message.orEmpty()}")
            try {
                if (doc.delete()) acc.files++
            } catch (ignored: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------ copy

    suspend fun copy(
        src: FileNode,
        destDir: FileNode,
        onProgress: suspend (OpProgress) -> Unit,
        onConflict: (suspend (Conflict) -> ConflictDecision)? = null,
    ): OpResult = withContext(Dispatchers.IO) {
        val srcDoc = document(src) ?: return@withContext OpResult().recordError("Origen no disponible")
        val destDoc = document(destDir) ?: return@withContext OpResult().recordError("Destino no disponible")
        if (!destDoc.isDirectory) return@withContext OpResult().recordError("El destino no es una carpeta")
        if (src.isDir) {
            val srcId = docId(src)
            val destId = docId(destDir)
            if (TransferGuard.safInsideOrSelf(destId, srcId)) {
                throw TransferException("No se puede copiar una carpeta dentro de sí misma")
            }
        }
        val total = sizeOf(src)
        val sink = ProgressSink(OpType.COPY, onProgress)
        val acc = OpAccumulator()
        copyRecursive(srcDoc, destDoc, sink, total, acc, onConflict)
        acc.result()
    }

    private suspend fun copyRecursive(
        src: DocumentFile,
        destDir: DocumentFile,
        sink: ProgressSink,
        total: Long,
        acc: OpAccumulator,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (src.isDirectory) {
            val dest = createOrResolveDir(destDir, src.name ?: "carpeta", acc, onConflict) ?: return
            for (c in src.listFiles()) copyRecursive(c, dest, sink, total, acc, onConflict)
        } else {
            val name = src.name ?: return
            val mime = src.type ?: "application/octet-stream"
            val existing = destDir.findFile(name)
            val dest = resolveDest(destDir, name, mime, existing, acc, onConflict) ?: return
            copyStream(
                resolver.openInputStream(src.uri),
                resolver.openOutputStream(dest.uri, "wt"),
                sink,
                total,
                name,
                acc,
            )
        }
    }

    /**
     * Directory collision: OVERWRITE merges into the existing directory,
     * KEEP_BOTH creates a unique sibling, SKIP returns null.
     */
    private suspend fun createOrResolveDir(
        destDir: DocumentFile,
        name: String,
        acc: OpAccumulator,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ): DocumentFile? {
        val existing = destDir.findFile(name)
        if (existing == null) {
            return destDir.createDirectory(name) ?: run {
                acc.error("No se pudo crear la carpeta $name")
                null
            }
        }
        val decision = onConflict?.invoke(
            Conflict(name, destDir.uri.toString(), isDir = true, existingSize = -1L, existingModified = existing.lastModified())
        ) ?: ConflictDecision.KEEP_BOTH
        return when (decision) {
            ConflictDecision.OVERWRITE -> existing
            ConflictDecision.SKIP -> {
                acc.skipped++
                null
            }
            ConflictDecision.CANCEL_OPERATION -> throw ConflictCancelledException()
            ConflictDecision.KEEP_BOTH -> {
                destDir.createDirectory(uniqueName(destDir, name)) ?: run {
                    acc.error("No se pudo crear la carpeta $name")
                    null
                }
            }
        }
    }

    /** File collision: returns the DocumentFile to write into, or null when skipped. */
    private suspend fun resolveDest(
        destDir: DocumentFile,
        name: String,
        mime: String,
        existing: DocumentFile?,
        acc: OpAccumulator,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ): DocumentFile? {
        if (existing == null) {
            return destDir.createFile(mime, name) ?: run {
                acc.error("No se pudo crear el archivo $name")
                null
            }
        }
        val decision = onConflict?.invoke(
            Conflict(name, destDir.uri.toString(), isDir = false, existingSize = existing.length(), existingModified = existing.lastModified())
        ) ?: ConflictDecision.KEEP_BOTH
        return when (decision) {
            ConflictDecision.OVERWRITE -> {
                // SAF cannot replace in place: guarded delete + recreate.
                val ok = runCatching { existing.delete() }.getOrDefault(false)
                if (!ok) {
                    acc.error("No se pudo sobrescribir $name")
                    null
                } else {
                    destDir.createFile(mime, name) ?: run {
                        acc.error("No se pudo sobrescribir $name")
                        null
                    }
                }
            }
            ConflictDecision.SKIP -> {
                acc.skipped++
                null
            }
            ConflictDecision.CANCEL_OPERATION -> throw ConflictCancelledException()
            ConflictDecision.KEEP_BOTH -> {
                destDir.createFile(mime, uniqueName(destDir, name)) ?: run {
                    acc.error("No se pudo crear el archivo $name")
                    null
                }
            }
        }
    }

    // ------------------------------------------------------------------ move

    suspend fun move(
        src: FileNode,
        destDir: FileNode,
        onProgress: suspend (OpProgress) -> Unit,
        onConflict: (suspend (Conflict) -> ConflictDecision)? = null,
    ): OpResult = withContext(Dispatchers.IO) {
        val srcDoc = document(src) ?: return@withContext OpResult().recordError("Origen no disponible")
        val destDoc = document(destDir) ?: return@withContext OpResult().recordError("Destino no disponible")
        if (!destDoc.isDirectory) return@withContext OpResult().recordError("El destino no es una carpeta")
        if (src.isDir) {
            val srcId = docId(src)
            val destId = docId(destDir)
            if (TransferGuard.safInsideOrSelf(destId, srcId)) {
                throw TransferException("No se puede mover una carpeta dentro de sí misma")
            }
        }
        val parent = srcDoc.parentFile
        val sameParent = parent != null && parent.uri == destDoc.uri
        if (sameParent) {
            val existing = destDoc.findFile(src.name.orEmpty())
            val name = src.name ?: return@withContext OpResult().recordError("Nombre desconocido")
            if (existing == null || existing.uri == srcDoc.uri) {
                if (existing == null && srcDoc.renameTo(name)) {
                    sinkDone(OpType.MOVE, onProgress, 1)
                    return@withContext OpResult(filesDone = 1)
                }
            } else {
                val decision = onConflict?.invoke(
                    Conflict(name, destDoc.uri.toString(), isDir = src.isDir, existingSize = existing.length(), existingModified = existing.lastModified())
                ) ?: ConflictDecision.KEEP_BOTH
                if (decision == ConflictDecision.SKIP) {
                    return@withContext OpResult(skipped = 1)
                }
                if (decision == ConflictDecision.CANCEL_OPERATION) throw ConflictCancelledException()
                val targetName = if (decision == ConflictDecision.KEEP_BOTH) uniqueName(destDoc, name) else name
                if (decision == ConflictDecision.OVERWRITE) {
                    runCatching { existing.delete() }
                }
                if (srcDoc.renameTo(targetName)) {
                    sinkDone(OpType.MOVE, onProgress, 1)
                    return@withContext OpResult(filesDone = 1)
                }
            }
        }
        // Cross-directory / cross-volume move: full copy, then delete the
        // source only when the copy completed without errors (data safety).
        val total = sizeOf(src)
        val sink = ProgressSink(OpType.MOVE, onProgress)
        val acc = OpAccumulator()
        copyRecursive(srcDoc, destDoc, sink, total, acc, onConflict)
        if (acc.errors == 0) {
            val delAcc = OpAccumulator()
            deleteRecursive(srcDoc, ProgressSink(OpType.MOVE, onProgress), countFiles(srcDoc), delAcc)
            if (delAcc.errors > 0) {
                acc.error("La copia terminó, pero no se pudieron eliminar todos los archivos de origen")
            }
        } else {
            acc.error("La copia no se completó; el origen no se eliminó")
        }
        acc.result()
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
        val created = doc.createDirectory(uniqueName(doc, name)) ?: return@withContext null
        val p = if (parent.path == parent.name) name else "${parent.path}/$name"
        FileNode.forDirectory(name, p, created.lastModified(), created.uri)
    }

    /** Creates a file entry with the exact [name] inside [parent]; null on failure. */
    suspend fun createFile(parent: FileNode, name: String, mime: String): FileNode? = withContext(Dispatchers.IO) {
        val doc = document(parent) ?: return@withContext null
        val created = doc.createFile(mime, name) ?: return@withContext null
        val p = if (parent.path == parent.name) name else "${parent.path}/$name"
        FileNode(
            name = name,
            path = p,
            isDir = false,
            size = 0L,
            lastModified = created.lastModified(),
            extension = CategoryEngine.extensionOf(name),
            category = CategoryEngine.classify(name),
            uri = created.uri,
        )
    }

    /** Exposes whether a same-name child exists (used by the mixed engine). */
    fun nameExists(parent: FileNode, name: String): Boolean =
        document(parent)?.findFile(name) != null

    /** Deletes a node whose file system entry changed underneath. */
    fun deleteNode(node: FileNode): Boolean = runCatching {
        document(node)?.delete() ?: false
    }.getOrDefault(false)

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

    private fun uniqueName(dir: DocumentFile, name: String): String {
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
        acc: OpAccumulator,
    ) {
        if (src == null || dest == null) {
            acc.error("No se pudo leer/escribir $name")
            return
        }
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
            acc.bytes += done
            acc.files++
        } catch (e: Exception) {
            acc.error("Error copiando $name: ${e.message.orEmpty()}")
        }
    }

    private suspend fun sinkDone(type: OpType, onProgress: suspend (OpProgress) -> Unit, files: Int) {
        onProgress(OpProgress(type, bytesDone = 0L, bytesTotal = 0L, filesDone = files, filesTotal = files))
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

/**
 * Mutable accumulator feeding an [OpResult]; lets recursion count bytes,
 * files and errors without copying immutable data classes on every hop.
 */
internal class OpAccumulator {
    var bytes: Long = 0L
    var files: Int = 0
    var errors: Int = 0
    var firstError: String? = null
    var skipped: Int = 0

    fun error(message: String) {
        errors++
        if (firstError == null) firstError = message
    }

    fun result(): OpResult = OpResult(
        bytesDone = bytes,
        filesDone = files,
        errors = errors,
        firstError = firstError,
        skipped = skipped,
    )
}

/** Shared sorting: directories first, then the requested key + direction. */
object Sorters {
    fun comparator(sort: SortOrder, direction: SortDirection = SortDirection.ASC): Comparator<FileNode> =
        Comparator { a, b ->
            if (a.isDir != b.isDir) {
                if (a.isDir) -1 else 1
            } else {
                val raw = when (sort) {
                    SortOrder.NAME -> a.name.lowercase().compareTo(b.name.lowercase())
                    SortOrder.SIZE -> a.size.compareTo(b.size)
                    SortOrder.DATE -> a.lastModified.compareTo(b.lastModified)
                }
                if (direction == SortDirection.DESC) -raw else raw
            }
        }
}
