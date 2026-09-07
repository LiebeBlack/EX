package com.apex.files.data.fs

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.apex.files.core.OpProgress
import com.apex.files.core.OpType
import com.apex.files.core.PerfMetrics
import com.apex.files.core.SpeedTracker
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import com.apex.files.data.model.SortDirection
import com.apex.files.data.model.SortOrder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Single entry point for every file operation. Dispatches to java.io.File
 * (All Files Access), [SafRepository] (SAF trees) or a mixed File ⇄ SAF
 * transfer depending on which side of the operation carries a content URI.
 *
 * Operations:
 *  - return an honest [OpResult] (partial failures are counted),
 *  - block copying/moving a folder into its own subtree ([TransferGuard]),
 *  - pause on destination collisions through [onConflict] when provided.
 */
class FsRepository(private val context: Context) {

    companion object {
        /** Concurrent workers for the parallel file copy path. */
        const val COPY_WORKERS = 4
    }

    private val saf = SafRepository(context)
    private val resolver get() = context.contentResolver

    // ------------------------------------------------------------ listing

    fun rootNode(location: Location): FileNode = when (location) {
        is Location.Fs -> FileNode.forDirectory(
            name = location.label,
            path = location.root.absolutePath,
            lastModified = location.root.lastModified(),
            isRoot = true,
        )
        is Location.Saf -> FileNode.forDirectory(
            name = location.label,
            path = location.label,
            lastModified = 0L,
            uri = location.rootUri,
            isRoot = true,
        )
    }

    suspend fun list(
        dir: FileNode,
        showHidden: Boolean,
        sort: SortOrder,
        direction: SortDirection = SortDirection.ASC,
    ): List<FileNode> = PerfMetrics.timeSuspend(
        tag = "fs.list",
        detail = if (dir.uri != null) "saf:${dir.name}" else dir.name,
        countOf = { it.size },
    ) {
        withContext(Dispatchers.IO) { listEntries(dir, showHidden, sort, direction) }
    }

    private suspend fun listEntries(
        dir: FileNode,
        showHidden: Boolean,
        sort: SortOrder,
        direction: SortDirection,
    ): List<FileNode> = withContext(Dispatchers.IO) {
        if (dir.uri != null) return@withContext saf.list(dir, showHidden, sort, direction)
        val file = File(dir.path)
        val children = file.listFiles() ?: return@withContext emptyList()
        val out = ArrayList<FileNode>(children.size)
        for (child in children) {
            if (Paths.isExcluded(child)) continue
            // One isDirectory() stat per child, reused: the previous shape
            // called isDirectory up to three times per entry (hasNomedia +
            // toNode) and length() even for directories — noticeable on
            // folders with thousands of entries.
            val isDir = child.isDirectory
            if (!showHidden) {
                if (child.name.startsWith(".")) continue
                if (isDir && File(child, ".nomedia").exists()) continue
            }
            out.add(if (isDir) child.toDirectoryNode() else child.toFileNode())
        }
        out.sortedWith(Sorters.comparator(sort, direction))
    }

    private fun File.toDirectoryNode(): FileNode =
        FileNode.forDirectory(name, absolutePath, lastModified())

    private fun File.toFileNode(): FileNode {
        return FileNode(
            name = name,
            path = absolutePath,
            isDir = false,
            size = length(),
            lastModified = lastModified(),
            extension = CategoryEngine.extensionOf(name),
            category = CategoryEngine.classify(name),
        )
    }

    /** General variant for call sites that don't know the kind in advance. */
    private fun File.toNode(): FileNode =
        if (isDirectory) toDirectoryNode() else toFileNode()

    // ------------------------------------------------------------ deletion

    suspend fun delete(node: FileNode, onProgress: suspend (OpProgress) -> Unit): OpResult =
        withContext(Dispatchers.IO) {
            if (node.uri != null) return@withContext saf.delete(node, onProgress)
            val file = File(node.path)
            if (!file.exists()) return@withContext OpResult()
            val total = countFiles(file)
            val sink = ProgressSink(OpType.DELETE, onProgress)
            val acc = OpAccumulator()
            deleteRecursive(file, sink, total, acc)
            acc.result()
        }

    private suspend fun deleteRecursive(file: File, sink: ProgressSink, total: Int, acc: OpAccumulator) {
        currentCoroutineContext().ensureActive()
        if (file.isDirectory && !Paths.isSymlink(file)) {
            val children = file.listFiles() ?: return
            for (c in children) deleteRecursive(c, sink, total, acc)
            if (!file.delete() && acc.files > 0) {
                acc.error("No se pudo eliminar la carpeta ${file.name}")
            }
        } else {
            if (file.delete()) {
                acc.addFiles()
                sink.emit(1L, null, acc.files, total, file.name)
            } else {
                acc.error("No se pudo eliminar ${file.name}")
            }
        }
    }

    // ---------------------------------------------------------------- copy

    suspend fun copy(
        src: FileNode,
        destDir: FileNode,
        onProgress: suspend (OpProgress) -> Unit,
        onConflict: (suspend (Conflict) -> ConflictDecision)? = null,
    ): OpResult = withContext(Dispatchers.IO) {
        when {
            src.uri == null && destDir.uri == null -> copyFileTree(src, destDir, onProgress, onConflict)
            src.uri != null && destDir.uri != null -> saf.copy(src, destDir, onProgress, onConflict)
            else -> mixedTransfer(src, destDir, onProgress, onConflict, isMove = false)
        }
    }

    /**
     * Duplicates a local file next to itself under a unique sibling name
     * ("foto.jpg" → "foto (1).jpg"). SAF-backed nodes are not supported
     * because their parent DocumentFile is not resolvable from the child uri.
     */
    suspend fun duplicateFile(
        src: FileNode,
        onProgress: suspend (OpProgress) -> Unit,
    ): OpResult = withContext(Dispatchers.IO) {
        if (src.uri != null || src.isDir) {
            return@withContext OpResult(skipped = 1, firstError = "Solo se pueden duplicar archivos locales")
        }
        val srcFile = File(src.path)
        if (!srcFile.isFile) {
            return@withContext OpResult(skipped = 1, firstError = "El archivo ya no existe")
        }
        val parent = srcFile.parentFile
        if (parent == null) {
            return@withContext OpResult(skipped = 1, firstError = "No se pudo duplicar el archivo")
        }
        val target = uniqueFile(parent, srcFile.name)
        val acc = OpAccumulator()
        val sink = ProgressSink(OpType.COPY, onProgress)
        copyFile(srcFile, target, sink, srcFile.length().coerceAtLeast(1), acc)
        acc.result()
    }

    private suspend fun copyFileTree(
        src: FileNode,
        destDir: FileNode,
        onProgress: suspend (OpProgress) -> Unit,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ): OpResult {
        val srcFile = File(src.path)
        if (!srcFile.exists()) return OpResult()
        // A file must never be copied onto itself (OVERWRITE would truncate
        // the source before the copy starts).
        if (!src.isDir && File(destDir.path, srcFile.name).absolutePath == srcFile.absolutePath) {
            return OpResult(skipped = 1, firstError = "El archivo ya está en la carpeta de destino")
        }
        if (src.isDir) {
            val dest = File(destDir.path)
            if (TransferGuard.isInsideOrSelf(dest, srcFile)) {
                throw TransferException("No se puede copiar una carpeta dentro de sí misma")
            }
        }
        val totalBytes = sizeOf(src)
        val sink = ProgressSink(OpType.COPY, onProgress)
        val acc = OpAccumulator()
        copyRecursive(srcFile, File(destDir.path), sink, totalBytes, acc, onConflict)
        // New files on plain storage are invisible to MediaStore until a
        // rescan; notify it so Galería/Descargas see the result instantly.
        rescan(File(destDir.path).absolutePath)
        return acc.result()
    }

    private suspend fun copyRecursive(
        src: File,
        destDir: File,
        sink: ProgressSink,
        total: Long,
        acc: OpAccumulator,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ) {
        currentCoroutineContext().ensureActive()
        if (src.isDirectory && !Paths.isSymlink(src)) {
            val dest = createOrResolveDir(destDir, src.name, acc, onConflict) ?: return
            val children = src.listFiles() ?: return
            val dirs = ArrayList<File>()
            val files = ArrayList<File>()
            for (c in children) {
                if (c.isDirectory && !Paths.isSymlink(c)) dirs.add(c)
                else if (c.isFile) files.add(c)
            }
            // Subdirectories stay sequential (deterministic conflict ordering);
            // the plain files of this level copy in parallel for throughput.
            for (d in dirs) copyRecursive(d, dest, sink, total, acc, onConflict)
            copyFilesParallel(files, dest, sink, total, acc, onConflict)
        } else {
            val dest = resolveDest(destDir, src.name, acc, onConflict) ?: return
            copyFile(src, dest, sink, total, acc)
        }
    }

    /**
     * Copies [files] into [destDir] with up to [COPY_WORKERS] workers. The
     * accumulator and progress sink are thread-safe; conflict resolution stays
     * serialized through [ConflictController] (a busy dialog answers with
     * KEEP_BOTH, so parallel collisions degrade gracefully).
     */
    private suspend fun copyFilesParallel(
        files: List<File>,
        destDir: File,
        sink: ProgressSink,
        total: Long,
        acc: OpAccumulator,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ) {
        if (files.isEmpty()) return
        if (files.size == 1) {
            val dest = resolveDest(destDir, files[0].name, acc, onConflict) ?: return
            copyFile(files[0], dest, sink, total, acc)
            return
        }
        val semaphore = Semaphore(COPY_WORKERS)
        coroutineScope {
            for (src in files) {
                launch {
                    semaphore.withPermit {
                        currentCoroutineContext().ensureActive()
                        val dest = resolveDest(destDir, src.name, acc, onConflict) ?: return@withPermit
                        copyFile(src, dest, sink, total, acc)
                    }
                }
            }
        }
    }

    private suspend fun createOrResolveDir(
        destDir: File,
        name: String,
        acc: OpAccumulator,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ): File? {
        val target = File(destDir, name)
        if (!target.exists()) {
            return if (target.mkdirs() || target.isDirectory) {
                target
            } else {
                acc.error("No se pudo crear la carpeta $name")
                null
            }
        }
        val decision = onConflict?.invoke(
            Conflict(name, destDir.absolutePath, isDir = true, existingSize = -1L, existingModified = target.lastModified())
        ) ?: ConflictDecision.KEEP_BOTH
        return when (decision) {
            ConflictDecision.OVERWRITE -> target
            ConflictDecision.SKIP -> {
                acc.addSkipped()
                null
            }
            ConflictDecision.CANCEL_OPERATION -> throw ConflictCancelledException()
            ConflictDecision.KEEP_BOTH -> {
                val unique = uniqueFile(destDir, name)
                if (!unique.mkdirs()) acc.error("No se pudo crear la carpeta $name")
                unique
            }
        }
    }

    private suspend fun resolveDest(
        destDir: File,
        name: String,
        acc: OpAccumulator,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ): File? {
        val target = File(destDir, name)
        if (!target.exists()) return target
        val decision = onConflict?.invoke(
            Conflict(name, destDir.absolutePath, isDir = false, existingSize = target.length(), existingModified = target.lastModified())
        ) ?: ConflictDecision.KEEP_BOTH
        return when (decision) {
            ConflictDecision.OVERWRITE -> target // FileOutputStream truncates in place.
            ConflictDecision.SKIP -> {
                acc.addSkipped()
                null
            }
            ConflictDecision.CANCEL_OPERATION -> throw ConflictCancelledException()
            ConflictDecision.KEEP_BOTH -> uniqueFile(destDir, name)
        }
    }

    private suspend fun copyFile(src: File, dest: File, sink: ProgressSink, total: Long, acc: OpAccumulator) {
        val buffer = ByteArray(64 * 1024)
        var done = 0L
        try {
            dest.parentFile?.mkdirs()
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            done += read
                            sink.emit(done, total, 1, null, src.name)
                        }
                    }
                    output.flush()
                }
            }
            acc.addBytes(done)
            acc.addFiles()
        } catch (e: Exception) {
            acc.error("Error copiando ${src.name}: ${e.message.orEmpty()}")
            runCatching { dest.delete() }
        }
    }

    // ----------------------------------------------------------------- move

    suspend fun move(
        src: FileNode,
        destDir: FileNode,
        onProgress: suspend (OpProgress) -> Unit,
        onConflict: (suspend (Conflict) -> ConflictDecision)? = null,
    ): OpResult = withContext(Dispatchers.IO) {
        when {
            src.uri == null && destDir.uri == null -> moveFileTree(src, destDir, onProgress, onConflict)
            src.uri != null && destDir.uri != null -> saf.move(src, destDir, onProgress, onConflict)
            else -> mixedTransfer(src, destDir, onProgress, onConflict, isMove = true)
        }
    }

    private suspend fun moveFileTree(
        src: FileNode,
        destDir: FileNode,
        onProgress: suspend (OpProgress) -> Unit,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ): OpResult {
        val srcFile = File(src.path)
        if (!srcFile.exists()) return OpResult()
        val destBase = File(destDir.path)
        if (src.isDir && TransferGuard.isInsideOrSelf(destBase, srcFile)) {
            throw TransferException("No se puede mover una carpeta dentro de sí misma")
        }
        if (srcFile.parentFile?.absolutePath == destBase.absolutePath) {
            // Same directory: a rename (with collision resolution).
            val target = File(destBase, src.name)
            if (!target.exists() || target.absolutePath == srcFile.absolutePath) {
                if (srcFile.renameTo(target)) return OpResult(filesDone = 1)
            } else {
                val decision = onConflict?.invoke(
                    Conflict(src.name, destBase.absolutePath, isDir = src.isDir, existingSize = target.length(), existingModified = target.lastModified())
                ) ?: ConflictDecision.KEEP_BOTH
                when (decision) {
                    ConflictDecision.SKIP -> return OpResult(skipped = 1)
                    ConflictDecision.CANCEL_OPERATION -> throw ConflictCancelledException()
                    ConflictDecision.OVERWRITE -> {
                        runCatching { target.delete() }
                        if (srcFile.renameTo(target)) return OpResult(filesDone = 1)
                    }
                    ConflictDecision.KEEP_BOTH -> {
                        val unique = uniqueFile(destBase, src.name)
                        if (srcFile.renameTo(unique)) return OpResult(filesDone = 1)
                    }
                }
            }
        }
        // Cross-directory / cross-volume: copy then delete the source only
        // when the copy finished without errors (data safety).
        val totalBytes = sizeOf(src)
        val sink = ProgressSink(OpType.MOVE, onProgress)
        val acc = OpAccumulator()
        copyRecursive(srcFile, destBase, sink, totalBytes, acc, onConflict)
        if (acc.errors == 0) {
            val delAcc = OpAccumulator()
            deleteRecursive(srcFile, ProgressSink(OpType.MOVE, onProgress), countFiles(srcFile), delAcc)
            if (delAcc.errors > 0) {
                acc.error("La copia terminó, pero no se pudieron eliminar todos los archivos de origen")
            }
        } else {
            acc.error("La copia no se completó; el origen no se eliminó")
        }
        rescan(destBase.absolutePath)
        return acc.result()
    }

    // ------------------------------------------------------- mixed File⇄SAF

    /**
     * Transfer between different backends (File source into SAF destination
     * or vice versa). Streams bytes between both worlds with the same
     * conflict resolution and subtree guard as the single-backend paths.
     */
    private suspend fun mixedTransfer(
        src: FileNode,
        destDir: FileNode,
        onProgress: suspend (OpProgress) -> Unit,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
        isMove: Boolean,
    ): OpResult {
        if (src.isDir) {
            val dest = when {
                destDir.uri == null -> File(destDir.path)
                else -> null
            }
            if (dest != null && src.uri == null && TransferGuard.isInsideOrSelf(dest, File(src.path))) {
                throw TransferException("No se puede mover una carpeta dentro de sí misma")
            }
        }
        val totalBytes = sizeOf(src)
        val sink = ProgressSink(if (isMove) OpType.MOVE else OpType.COPY, onProgress)
        val acc = OpAccumulator()
        mixedRecursive(src, destDir, sink, totalBytes, acc, onConflict)
        if (isMove && acc.errors == 0) {
            if (src.uri == null) {
                val delAcc = OpAccumulator()
                deleteRecursive(File(src.path), ProgressSink(OpType.MOVE, onProgress), countFiles(File(src.path)), delAcc)
                if (delAcc.errors > 0) {
                    acc.error("La copia terminó, pero no se pudieron eliminar todos los archivos de origen")
                }
            } else {
                val r = saf.delete(src) {}
                if (r.errors > 0) acc.error("La copia se completó pero el origen no se pudo eliminar")
            }
        }
        return acc.result()
    }

    private suspend fun mixedRecursive(
        src: FileNode,
        destDir: FileNode,
        sink: ProgressSink,
        total: Long,
        acc: OpAccumulator,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ) {
        currentCoroutineContext().ensureActive()
        if (src.isDir) {
            val created = mixedCreateDir(destDir, src.name, acc, onConflict) ?: return
            val children = list(src, showHidden = true, sort = SortOrder.NAME)
            for (child in children) mixedRecursive(child, created, sink, total, acc, onConflict)
        } else {
            val name = src.name
            val mime = FileKinds.mimeOf(src)
            val destNode = mixedCreateFile(destDir, name, mime, acc, onConflict) ?: return
            val input = openInputStream(src) ?: run {
                acc.error("No se pudo leer $name")
                return
            }
            val output = if (destNode.uri != null) resolver.openOutputStream(destNode.uri, "wt")
            else try {
                FileOutputStream(File(destNode.path))
            } catch (e: Exception) {
                null
            }
            if (output == null) {
                acc.error("No se pudo escribir $name")
                return
            }
            val buffer = ByteArray(64 * 1024)
            var done = 0L
            try {
                input.use { srcStream ->
                    output.use { outStream ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = srcStream.read(buffer)
                            if (read < 0) break
                            if (read > 0) {
                                outStream.write(buffer, 0, read)
                                done += read
                                sink.emit(done, total, 1, null, name)
                            }
                        }
                        outStream.flush()
                    }
                }
                acc.addBytes(done)
                acc.addFiles()
            } catch (e: Exception) {
                acc.error("Error copiando $name: ${e.message.orEmpty()}")
                if (destNode.uri != null) saf.deleteNode(destNode) else runCatching { File(destNode.path).delete() }
            }
        }
    }

    /** Creates a child directory under [destDir] (File or SAF) honoring conflicts. */
    private suspend fun mixedCreateDir(
        destDir: FileNode,
        name: String,
        acc: OpAccumulator,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ): FileNode? {
        if (destDir.uri == null) {
            val parent = File(destDir.path)
            val target = File(parent, name)
            if (!target.exists()) {
                if (!target.mkdirs()) acc.error("No se pudo crear la carpeta $name")
                return FileNode.forDirectory(target.name, target.absolutePath)
            }
            val decision = onConflict?.invoke(Conflict(name, parent.absolutePath, isDir = true)) ?: ConflictDecision.KEEP_BOTH
            return when (decision) {
                ConflictDecision.OVERWRITE -> FileNode.forDirectory(target.name, target.absolutePath)
                ConflictDecision.SKIP -> {
                    acc.addSkipped()
                    null
                }
                ConflictDecision.CANCEL_OPERATION -> throw ConflictCancelledException()
                ConflictDecision.KEEP_BOTH -> {
                    val unique = uniqueFile(parent, name)
                    if (!unique.mkdirs()) acc.error("No se pudo crear la carpeta $name")
                    FileNode.forDirectory(unique.name, unique.absolutePath)
                }
            }
        } else {
            if (!saf.nameExists(destDir, name)) {
                return saf.createDirectory(destDir, name) ?: run {
                    acc.error("No se pudo crear la carpeta $name")
                    null
                }
            }
            val decision = onConflict?.invoke(Conflict(name, destDir.path, isDir = true)) ?: ConflictDecision.KEEP_BOTH
            return when (decision) {
                ConflictDecision.OVERWRITE -> saf.document(destDir)?.findFile(name)?.let { n ->
                    FileNode.forDirectory(name, "${destDir.path}/$name", n.lastModified(), n.uri)
                }
                ConflictDecision.SKIP -> {
                    acc.addSkipped()
                    null
                }
                ConflictDecision.CANCEL_OPERATION -> throw ConflictCancelledException()
                ConflictDecision.KEEP_BOTH -> saf.createDirectory(destDir, name)?.let {
                    // createDirectory already auto-uniqued the name.
                    it
                } ?: run {
                    acc.error("No se pudo crear la carpeta $name")
                    null
                }
            }
        }
    }

    /** Creates a child file under [destDir] (File or SAF) honoring conflicts. */
    private suspend fun mixedCreateFile(
        destDir: FileNode,
        name: String,
        mime: String,
        acc: OpAccumulator,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ): FileNode? {
        if (destDir.uri == null) {
            val parent = File(destDir.path)
            val target = File(parent, name)
            if (!target.exists()) return FileNode(
                name = name,
                path = "${destDir.path.trimEnd('/')}/$name",
                isDir = false,
                size = 0L,
                lastModified = 0L,
                extension = CategoryEngine.extensionOf(name),
                category = CategoryEngine.classify(name),
            )
            val decision = onConflict?.invoke(Conflict(name, parent.absolutePath, isDir = false, existingSize = target.length())) ?: ConflictDecision.KEEP_BOTH
            return when (decision) {
                ConflictDecision.OVERWRITE -> FileNode(name, target.absolutePath, false, 0L, 0L, CategoryEngine.extensionOf(name), CategoryEngine.classify(name))
                ConflictDecision.SKIP -> {
                    acc.addSkipped()
                    null
                }
                ConflictDecision.CANCEL_OPERATION -> throw ConflictCancelledException()
                ConflictDecision.KEEP_BOTH -> {
                    val unique = uniqueFile(parent, name)
                    FileNode(unique.name, unique.absolutePath, false, 0L, 0L, CategoryEngine.extensionOf(unique.name), CategoryEngine.classify(unique.name))
                }
            }
        } else {
            if (!saf.nameExists(destDir, name)) {
                return saf.createFile(destDir, name, mime) ?: run {
                    acc.error("No se pudo crear el archivo $name")
                    null
                }
            }
            val decision = onConflict?.invoke(Conflict(name, destDir.path, isDir = false)) ?: ConflictDecision.KEEP_BOTH
            return when (decision) {
                ConflictDecision.OVERWRITE -> {
                    val existing = saf.document(destDir)?.findFile(name)
                    if (existing == null) {
                        acc.error("No se pudo sobrescribir $name")
                        null
                    } else {
                        FileNode(
                            name = name,
                            path = "${destDir.path}/${name}".replace("//", "/"),
                            isDir = false,
                            size = 0L,
                            lastModified = 0L,
                            extension = CategoryEngine.extensionOf(name),
                            category = CategoryEngine.classify(name),
                            uri = existing.uri,
                        )
                    }
                }
                ConflictDecision.SKIP -> {
                    acc.addSkipped()
                    null
                }
                ConflictDecision.CANCEL_OPERATION -> throw ConflictCancelledException()
                ConflictDecision.KEEP_BOTH -> saf.createFile(destDir, name, mime)?.let { created ->
                    created.copy(name = created.name) // createFile already uniqued by provider/name lookup
                } ?: run {
                    acc.error("No se pudo crear el archivo $name")
                    null
                }
            }
        }
    }

    // ------------------------------------------------------------ compress

    suspend fun compress(
        sources: List<FileNode>,
        destDir: FileNode,
        name: String,
        onProgress: suspend (OpProgress) -> Unit,
        onConflict: (suspend (Conflict) -> ConflictDecision)? = null,
    ): OpResult = withContext(Dispatchers.IO) {
        // SAF destinations are not supported yet: report it as an honest
        // result instead of throwing (throwing surfaces as a raw failure).
        if (sources.any { it.uri != null } || destDir.uri != null) {
            return@withContext OpResult(errors = 1, firstError = "La compresión a almacenamiento SAF aún no está soportada")
        }
        if (sources.isEmpty()) return@withContext OpResult()
        val base = File(destDir.path)
        val target = File(base, if (name.endsWith(".zip", true)) name else "$name.zip")
        val decision = if (target.exists()) {
            onConflict?.invoke(
                Conflict(target.name, base.absolutePath, isDir = false, existingSize = target.length(), existingModified = target.lastModified())
            ) ?: ConflictDecision.KEEP_BOTH
        } else {
            ConflictDecision.KEEP_BOTH
        }
        val destFile = when (decision) {
            ConflictDecision.OVERWRITE -> target
            ConflictDecision.KEEP_BOTH -> uniqueFile(base, target.name)
            ConflictDecision.SKIP -> return@withContext OpResult(skipped = 1)
            ConflictDecision.CANCEL_OPERATION -> throw ConflictCancelledException()
        }
        val totalBytes = sources.sumOf { sizeOf(it) }
        val sink = ProgressSink(OpType.COMPRESS, onProgress)
        val acc = OpAccumulator()
        try {
            ZipOutputStream(FileOutputStream(destFile)).use { zip ->
                var done = 0L
                for (src in sources) {
                    done += addToZip(File(src.path), "", zip, sink, done, totalBytes, acc)
                }
                zip.finish()
            }
            acc.addFiles(sources.size)
        } catch (e: Exception) {
            acc.error("Error comprimiendo: ${e.message.orEmpty()}")
            runCatching { destFile.delete() }
        }
        rescan(destFile.absolutePath)
        return@withContext acc.result()
    }

    private suspend fun addToZip(
        file: File,
        prefix: String,
        zip: ZipOutputStream,
        sink: ProgressSink,
        done: Long,
        total: Long,
        acc: OpAccumulator,
    ): Long {
        currentCoroutineContext().ensureActive()
        if (file.isDirectory && !Paths.isSymlink(file)) {
            var d = done
            val children = file.listFiles() ?: return d
            for (c in children) {
                d = addToZip(c, "$prefix${file.name}/", zip, sink, d, total, acc)
            }
            return d
        }
        val entryName = prefix + file.name
        zip.putNextEntry(ZipEntry(entryName))
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        try {
            FileInputStream(file).use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        zip.write(buffer, 0, read)
                        copied += read
                        sink.emit(done + copied, total, 1, null, entryName)
                    }
                }
            }
            acc.addBytes(copied)
            zip.closeEntry()
        } catch (e: Exception) {
            acc.error("Error comprimiendo ${file.name}: ${e.message.orEmpty()}")
        }
        return done + copied
    }

    // ------------------------------------------------------------ metadata

    suspend fun rename(node: FileNode, newName: String): FileNode? = withContext(Dispatchers.IO) {
        if (node.uri != null) return@withContext saf.rename(node, newName)
        val file = File(node.path)
        val parent = file.parentFile ?: return@withContext null
        val dest = File(parent, newName)
        if (dest.exists() || !file.renameTo(dest)) return@withContext null
        dest.toNode()
    }

    suspend fun createDirectory(parent: FileNode, name: String): FileNode? = withContext(Dispatchers.IO) {
        if (parent.uri != null) return@withContext saf.createDirectory(parent, name)
        val dir = File(parent.path, name)
        if (dir.exists() || !dir.mkdirs()) return@withContext null
        dir.toNode()
    }

    /**
     * Creates an empty file (e.g. a new text file) inside [parent]; returns
     * null when the name is taken, invalid or the write fails.
     */
    suspend fun createFile(parent: FileNode, name: String): FileNode? = withContext(Dispatchers.IO) {
        val mime = FileKinds.mimeOf(FileNode(name, "", false, 0L, 0L, CategoryEngine.extensionOf(name), CategoryEngine.classify(name)))
        if (parent.uri != null) return@withContext saf.createFile(parent, name, mime)
        val file = File(parent.path, name)
        if (file.exists()) return@withContext null
        val ok = try {
            file.parentFile?.mkdirs()
            file.createNewFile()
        } catch (e: Exception) {
            false
        }
        if (!ok) return@withContext null
        file.toNode()
    }

    suspend fun sizeOf(node: FileNode): Long = withContext(Dispatchers.IO) {
        if (node.uri != null) return@withContext saf.sizeOf(node)
        val file = File(node.path)
        if (file.isFile) return@withContext file.length().coerceAtLeast(0L)
        sumSizes(file)
    }

    private suspend fun sumSizes(file: File): Long {
        currentCoroutineContext().ensureActive()
        if (file.isFile) return file.length().coerceAtLeast(0L)
        if (!file.isDirectory || Paths.isSymlink(file)) return 0L
        val children = file.listFiles() ?: return 0L
        var sum = 0L
        for (c in children) sum += sumSizes(c)
        return sum
    }

    suspend fun countEntries(node: FileNode): CountResult = withContext(Dispatchers.IO) {
        if (node.uri != null) return@withContext saf.countEntries(node)
        countFileTree(File(node.path))
    }

    private suspend fun countFileTree(file: File): CountResult {
        currentCoroutineContext().ensureActive()
        if (file.isFile) return CountResult(1, 0)
        if (!file.isDirectory || Paths.isSymlink(file)) return CountResult(0, 0)
        val children = file.listFiles() ?: return CountResult(0, 0)
        var files = 0
        var dirs = 0
        for (c in children) {
            if (c.isDirectory) {
                dirs++
                val r = countFileTree(c)
                files += r.files
                dirs += r.dirs
            } else {
                files++
            }
        }
        return CountResult(files, dirs)
    }

    private suspend fun countFiles(file: File): Int = countFileTree(file).files

    // -------------------------------------------------------------- streams

    fun openInputStream(node: FileNode): InputStream? = try {
        if (node.uri != null) resolver.openInputStream(node.uri)
        else FileInputStream(File(node.path))
    } catch (e: Exception) {
        null
    }

    fun openOutputStream(node: FileNode): OutputStream? = try {
        if (node.uri != null) resolver.openOutputStream(node.uri, "wt")
        else FileOutputStream(File(node.path))
    } catch (e: Exception) {
        null
    }

    /** Writes [content] back to [node] with the given charset. Returns false on failure. */
    suspend fun saveText(
        node: FileNode,
        content: String,
        charset: Charset,
    ): Boolean = withContext(Dispatchers.IO) {
        val out = openOutputStream(node) ?: return@withContext false
        try {
            out.use { it.write(content.toByteArray(charset)) }
            rescan(node.path)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Appends [content] to the end of [node] with the given charset. Returns false on failure. */
    suspend fun appendText(
        node: FileNode,
        content: String,
        charset: Charset,
    ): Boolean = withContext(Dispatchers.IO) {
        val out = try {
            if (node.uri != null) resolver.openOutputStream(node.uri, "wa")
            else FileOutputStream(File(node.path), true)
        } catch (e: Exception) {
            null
        }
        if (out == null) return@withContext false
        try {
            out.use { it.write(content.toByteArray(charset)) }
            rescan(node.path)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Reads the text content of [node] with the given charset. Returns null on failure. */
    suspend fun readText(
        node: FileNode,
        charset: Charset,
    ): String? = withContext(Dispatchers.IO) {
        val input = openInputStream(node) ?: return@withContext null
        try {
            input.use { it.readBytes().toString(charset) }
        } catch (e: Exception) {
            null
        }
    }

    /** Creates a backup copy of [node] with a .bak extension. Returns the backup node or null. */
    suspend fun createBackup(node: FileNode): FileNode? = withContext(Dispatchers.IO) {
        if (node.uri != null) return@withContext null
        val file = File(node.path)
        if (!file.exists()) return@withContext null
        
        val backupName = "${file.name}.bak"
        val backupFile = File(file.parentFile, backupName)
        
        return@withContext if (file.copyTo(backupFile, overwrite = true)) {
            backupFile.toNode()
        } else {
            null
        }
    }

    /** Creates multiple backups with timestamp (file.txt → file_20250907_143022.txt.bak). */
    suspend fun createTimestampedBackup(node: FileNode): FileNode? = withContext(Dispatchers.IO) {
        if (node.uri != null) return@withContext null
        val file = File(node.path)
        if (!file.exists()) return@withContext null
        
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val dot = file.name.lastIndexOf('.')
        val base = if (dot > 0) file.name.substring(0, dot) else file.name
        val ext = if (dot > 0) file.name.substring(dot) else ""
        val backupName = "${base}_${timestamp}${ext}.bak"
        val backupFile = File(file.parentFile, backupName)
        
        return@withContext if (file.copyTo(backupFile, overwrite = false)) {
            backupFile.toNode()
        } else {
            null
        }
    }

    /** Merges text files by concatenating their contents. */
    suspend fun mergeTextFiles(sources: List<FileNode>, dest: FileNode, charset: Charset): Boolean = withContext(Dispatchers.IO) {
        val out = openOutputStream(dest) ?: return@withContext false
        try {
            out.use { output ->
                for (source in sources) {
                    val input = openInputStream(source) ?: continue
                    try {
                        input.use { input ->
                            output.write(input.readBytes())
                            output.write("\n".toByteArray(charset))
                        }
                    } catch (e: Exception) {
                        // Continue with next file if one fails
                    }
                }
            }
            rescan(dest.path)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Splits a text file into multiple files by line count. */
    suspend fun splitTextFile(
        source: FileNode,
        linesPerFile: Int,
        destDir: FileNode,
        charset: Charset,
    ): List<FileNode> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileNode>()
        val input = openInputStream(source) ?: return@withContext results
        try {
            input.use { input ->
                val reader = input.bufferedReader(charset)
                var fileCounter = 1
                var lineCounter = 0
                var currentContent = StringBuilder()
                val baseName = source.name.substringBeforeLast('.')
                val ext = source.name.substringAfterLast('.', "")
                
                reader.forEachLine { line ->
                    currentContent.append(line).append("\n")
                    lineCounter++
                    
                    if (lineCounter >= linesPerFile) {
                        val newFileName = "${baseName}_part${fileCounter}.${ext}"
                        val newNode = createFile(destDir, newFileName)
                        if (newNode != null) {
                            saveText(newNode, currentContent.toString(), charset)
                            results.add(newNode)
                        }
                        currentContent.clear()
                        lineCounter = 0
                        fileCounter++
                    }
                }
                
                // Write remaining content
                if (currentContent.isNotEmpty()) {
                    val newFileName = "${baseName}_part${fileCounter}.${ext}"
                    val newNode = createFile(destDir, newFileName)
                    if (newNode != null) {
                        saveText(newNode, currentContent.toString(), charset)
                        results.add(newNode)
                    }
                }
            }
        } catch (e: Exception) {
            // Return partial results on error
        }
        results
    }

    /** Batch rename files using a pattern (supports {n} for numbering). */
    suspend fun batchRename(
        files: List<FileNode>,
        pattern: String,
        startNumber: Int = 1,
    ): OpResult = withContext(Dispatchers.IO) {
        val acc = OpAccumulator()
        for ((index, file) in files.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (file.uri != null) {
                acc.addSkipped()
                continue
            }
            
            val number = startNumber + index
            val newName = pattern.replace("{n}", number.toString())
            val renamed = rename(file, newName)
            
            if (renamed != null) {
                acc.addFiles()
            } else {
                acc.error("No se pudo renombrar ${file.name}")
            }
        }
        acc.result()
    }

    /** Creates symbolic link (if supported by the filesystem). */
    suspend fun createSymlink(target: FileNode, linkName: String, parentDir: FileNode): FileNode? = withContext(Dispatchers.IO) {
        if (target.uri != null || parentDir.uri != null) return@withContext null
        
        val parent = File(parentDir.path)
        val linkFile = File(parent, linkName)
        val targetFile = File(target.path)
        
        return@withContext try {
            java.nio.file.Files.createSymbolicLink(linkFile.toPath(), targetFile.toPath())
            linkFile.toNode()
        } catch (e: Exception) {
            null
        }
    }

    /** Changes file/directory ownership (requires root, may fail on most devices). */
    suspend fun changeOwnership(node: FileNode, uid: Int, gid: Int): Boolean = withContext(Dispatchers.IO) {
        if (node.uri != null) return@withContext false
        return@withContext try {
            val file = File(node.path)
            val path = file.absolutePath
            // This requires root access and may not work on most Android devices
            Runtime.getRuntime().exec(arrayOf("chown", "$uid:$gid", path)).waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /** Changes file permissions (UNIX-style, e.g., 755 for rwxr-xr-x). */
    suspend fun changePermissions(node: FileNode, permissions: Int): Boolean = withContext(Dispatchers.IO) {
        if (node.uri != null) return@withContext false
        return@withContext try {
            val file = File(node.path)
            file.setExecutable((permissions and 0b001_001_001) != 0, false)
            file.setReadable((permissions and 0b100_100_100) != 0, false)
            file.setWritable((permissions and 0b010_010_010) != 0, false)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Advanced file comparison: checks if two files have identical content. */
    suspend fun filesAreIdentical(node1: FileNode, node2: FileNode): Boolean = withContext(Dispatchers.IO) {
        if (node1.size != node2.size) return@withContext false
        
        val input1 = openInputStream(node1) ?: return@withContext false
        val input2 = openInputStream(node2) ?: return@withContext false
        
        try {
            input1.use { in1 ->
                input2.use { in2 ->
                    val buffer1 = ByteArray(8192)
                    val buffer2 = ByteArray(8192)
                    while (true) {
                        val read1 = in1.read(buffer1)
                        val read2 = in2.read(buffer2)
                        if (read1 != read2) return@withContext false
                        if (read1 == -1) return@withContext true
                        for (i in 0 until read1) {
                            if (buffer1[i] != buffer2[i]) return@withContext false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Finds files similar to a given file by content similarity (simple prefix matching). */
    suspend fun findSimilarFiles(
        reference: FileNode,
        searchDir: FileNode,
        similarityThreshold: Float = 0.8f,
    ): List<FileNode> = withContext(Dispatchers.IO) {
        val similarFiles = mutableListOf<FileNode>()
        val refContent = readText(reference, Charsets.UTF_8) ?: return@withContext similarFiles
        
        val children = list(searchDir, showHidden = true, sort = SortOrder.NAME)
        for (child in children) {
            if (child.isDir || child.size > 10 * 1024 * 1024) continue // Skip large files and dirs
            
            val content = readText(child, Charsets.UTF_8) ?: continue
            val similarity = calculateSimilarity(refContent, content)
            
            if (similarity >= similarityThreshold) {
                similarFiles.add(child)
            }
        }
        similarFiles
    }

    private fun calculateSimilarity(text1: String, text2: String): Float {
        if (text1.isEmpty() || text2.isEmpty()) return 0f
        
        val set1 = text1.split("\\s+".toRegex()).toSet()
        val set2 = text2.split("\\s+".toRegex()).toSet()
        
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        
        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }

    /** Monitors a directory for changes (polling-based). */
    suspend fun monitorDirectory(
        dir: FileNode,
        intervalMs: Long = 2000L,
        onChange: (List<FileNode>) -> Unit,
    ) {
        var lastSnapshot = list(dir, showHidden = true, sort = SortOrder.NAME)
        
        while (true) {
            kotlinx.coroutines.delay(intervalMs)
            val currentSnapshot = list(dir, showHidden = true, sort = SortOrder.NAME)
            
            if (currentSnapshot.map { it.path }.toSet() != lastSnapshot.map { it.path }.toSet()) {
                onChange(currentSnapshot)
                lastSnapshot = currentSnapshot
            }
        }
    }

    /** Compresses individual file using GZIP. */
    suspend fun compressGzip(source: FileNode, dest: FileNode): Boolean = withContext(Dispatchers.IO) {
        val input = openInputStream(source) ?: return@withContext false
        val output = openOutputStream(dest) ?: return@withContext false
        
        try {
            input.use { inStream ->
                output.use { outStream ->
                    java.util.zip.GZIPOutputStream(outStream).use { gzip ->
                        inStream.copyTo(gzip)
                    }
                }
            }
            rescan(dest.path)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Decompresses GZIP file. */
    suspend fun decompressGzip(source: FileNode, dest: FileNode): Boolean = withContext(Dispatchers.IO) {
        val input = openInputStream(source) ?: return@withContext false
        val output = openOutputStream(dest) ?: return@withContext false
        
        try {
            input.use { inStream ->
                output.use { outStream ->
                    java.util.zip.GZIPInputStream(inStream).use { gzip ->
                        gzip.copyTo(outStream)
                    }
                }
            }
            rescan(dest.path)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Gets the last modified timestamp of a file/directory. */
    fun getLastModified(node: FileNode): Long {
        return if (node.uri != null) {
            saf.document(node)?.lastModified() ?: 0L
        } else {
            File(node.path).lastModified()
        }
    }

    /** Sets the last modified timestamp of a file/directory. */
    suspend fun setLastModified(node: FileNode, timestamp: Long): Boolean = withContext(Dispatchers.IO) {
        if (node.uri != null) return@withContext false
        try {
            File(node.path).setLastModified(timestamp)
        } catch (e: Exception) {
            false
        }
    }

    /** Gets file permissions (read/write/execute) for a file. */
    fun getPermissions(node: FileNode): Triple<Boolean, Boolean, Boolean> {
        return if (node.uri != null) {
            val doc = saf.document(node)
            Triple(
                doc?.canRead() ?: false,
                doc?.canWrite() ?: false,
                false // SAF doesn't expose execute permission
            )
        } else {
            val file = File(node.path)
            Triple(
                file.canRead(),
                file.canWrite(),
                file.canExecute()
            )
        }
    }

    /** Makes a file or directory read-only or writable. */
    suspend fun setReadOnly(node: FileNode, readOnly: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (node.uri != null) return@withContext false
        try {
            File(node.path).setReadOnly(readOnly)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns a real [File] for reading (copies SAF documents to cache).
     * The copy lands in a temp file and is atomically renamed, so a failed
     * or cancelled download can never leave a truncated file behind that a
     * later call would serve as if it were complete.
     */
    fun fileForReading(node: FileNode): File {
        if (node.uri == null) return File(node.path)
        val cache = File(context.cacheDir, "apex_read_" + (node.uri.lastPathSegment ?: node.name))
        if (cache.exists()) return cache
        val input = resolver.openInputStream(node.uri) ?: return cache
        val tmp = File(context.cacheDir, cache.name + ".tmp")
        try {
            input.use { src ->
                tmp.outputStream().use { dst ->
                    src.copyTo(dst, bufferSize = 64 * 1024)
                }
            }
            if (!tmp.renameTo(cache)) {
                // A concurrent reader may have completed the copy first.
                if (!cache.exists()) tmp.renameTo(cache)
            }
        } catch (e: Exception) {
            runCatching { tmp.delete() }
        }
        return cache
    }

    fun shareUri(node: FileNode): Uri? = try {
        if (node.uri != null) {
            node.uri
        } else {
            val file = File(node.path)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    } catch (e: Exception) {
        null
    }

    fun exists(node: FileNode): Boolean =
        if (node.uri != null) saf.document(node) != null else File(node.path).exists()

    fun isDirEmpty(dir: FileNode): Boolean = when {
        dir.uri != null -> (saf.document(dir)?.listFiles()?.isEmpty() ?: true)
        else -> (File(dir.path).listFiles()?.isEmpty() ?: true)
    }

    // ------------------------------------------------------------- helpers

    /**
     * Asks MediaStore to scan [path] (a file or a directory, scanned
     * recursively by the provider). Used after File-backed writes so new
     * media shows up in Galería/Descargas without a manual refresh.
     */
    private fun rescan(path: String) {
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        }
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

    private class ProgressSink(
        private val type: OpType,
        private val onProgress: suspend (OpProgress) -> Unit,
    ) {
        private val tracker = SpeedTracker()
        private var lastEmitAt = 0L
        suspend fun emit(bytesDone: Long, bytesTotal: Long?, filesDone: Int = 0, filesTotal: Int? = null, current: String = "") {
            // Throttle UI updates to ~10 Hz; a big copy otherwise pushes one
            // progress event per 64 KB chunk (tens of thousands of frames).
            val isFinal = bytesDone == bytesTotal ||
                (bytesTotal == null && filesTotal != null && filesDone == filesTotal)
            val speed = synchronized(this) {
                val now = SystemClock.uptimeMillis()
                if (!isFinal && now - lastEmitAt < 100L) return
                lastEmitAt = now
                tracker.update(bytesDone)
            }
            onProgress(OpProgress(type, bytesDone, bytesTotal, filesDone, filesTotal, current, speed))
        }
    }
}
