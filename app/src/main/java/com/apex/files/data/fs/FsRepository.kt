package com.apex.files.data.fs

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.apex.files.core.OpProgress
import com.apex.files.core.OpType
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    ): List<FileNode> = withContext(Dispatchers.IO) {
        if (dir.uri != null) return@withContext saf.list(dir, showHidden, sort, direction)
        val file = File(dir.path)
        val children = file.listFiles() ?: return@withContext emptyList()
        val out = ArrayList<FileNode>(children.size)
        for (child in children) {
            if (Paths.isExcluded(child)) continue
            if (!showHidden && (child.name.startsWith(".") || hasNomedia(child))) continue
            out.add(child.toNode())
        }
        out.sortedWith(Sorters.comparator(sort, direction))
    }

    private fun hasNomedia(dir: File): Boolean =
        dir.isDirectory && File(dir, ".nomedia").exists()

    private fun File.toNode(): FileNode {
        return if (isDirectory) {
            FileNode.forDirectory(name, absolutePath, lastModified())
        } else {
            FileNode(
                name = name,
                path = absolutePath,
                isDir = false,
                size = length(),
                lastModified = lastModified(),
                extension = CategoryEngine.extensionOf(name),
                category = CategoryEngine.classify(name),
            )
        }
    }

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
                acc.files++
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

    private suspend fun copyFileTree(
        src: FileNode,
        destDir: FileNode,
        onProgress: suspend (OpProgress) -> Unit,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ): OpResult {
        val srcFile = File(src.path)
        if (!srcFile.exists()) return OpResult()
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
            for (c in children) copyRecursive(c, dest, sink, total, acc, onConflict)
        } else {
            val dest = resolveDest(destDir, src.name, acc, onConflict) ?: return
            copyFile(src, dest, sink, total, acc)
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
                acc.skipped++
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
                acc.skipped++
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
            acc.bytes += done
            acc.files++
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
                acc.bytes += done
                acc.files++
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
                    acc.skipped++
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
                    acc.skipped++
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
                    acc.skipped++
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
                    acc.skipped++
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
        require(sources.all { it.uri == null } && destDir.uri == null) {
            "La compresión a SAF aún no está soportada"
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
            acc.files += sources.size
        } catch (e: Exception) {
            acc.error("Error comprimiendo: ${e.message.orEmpty()}")
            runCatching { destFile.delete() }
        }
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
            acc.bytes += copied
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
        charset: java.nio.charset.Charset,
    ): Boolean = withContext(Dispatchers.IO) {
        val out = openOutputStream(node) ?: return@withContext false
        try {
            out.use { it.write(content.toByteArray(charset)) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Returns a real [File] for reading (copies SAF documents to cache). */
    fun fileForReading(node: FileNode): File {
        if (node.uri == null) return File(node.path)
        val cache = File(context.cacheDir, "apex_read_" + (node.uri.lastPathSegment ?: node.name))
        if (cache.exists()) return cache
        val input = resolver.openInputStream(node.uri) ?: return cache
        input.use { src ->
            cache.outputStream().use { dst ->
                src.copyTo(dst, bufferSize = 64 * 1024)
            }
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
        if (node.uri != null) saf.document(node)?.exists() ?: false else File(node.path).exists()

    fun isDirEmpty(dir: FileNode): Boolean = when {
        dir.uri != null -> (saf.document(dir)?.listFiles()?.isEmpty() ?: true)
        else -> (File(dir.path).listFiles()?.isEmpty() ?: true)
    }

    // ------------------------------------------------------------- helpers

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
        suspend fun emit(bytesDone: Long, bytesTotal: Long?, filesDone: Int = 0, filesTotal: Int? = null, current: String = "") {
            val speed = tracker.update(bytesDone)
            onProgress(OpProgress(type, bytesDone, bytesTotal, filesDone, filesTotal, current, speed))
        }
    }
}
