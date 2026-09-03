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
import com.apex.files.data.model.SortOrder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Single entry point for every file operation. Dispatches transparently to
 * java.io.File (All Files Access) or [SafRepository] (SAF trees) based on
 * whether the [FileNode] carries a content [FileNode.uri].
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

    suspend fun list(dir: FileNode, showHidden: Boolean, sort: SortOrder): List<FileNode> =
        withContext(Dispatchers.IO) {
            if (dir.uri != null) return@withContext saf.list(dir, showHidden, sort)
            val file = File(dir.path)
            val children = file.listFiles() ?: return@withContext emptyList()
            val out = ArrayList<FileNode>(children.size)
            for (child in children) {
                if (Paths.isExcluded(child)) continue
                if (!showHidden && (child.name.startsWith(".") || hasNomedia(child))) continue
                out.add(child.toNode())
            }
            out.sortedWith(Sorters.comparator(sort))
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

    suspend fun delete(node: FileNode, onProgress: suspend (OpProgress) -> Unit) = withContext(Dispatchers.IO) {
        if (node.uri != null) {
            saf.delete(node, onProgress)
            return@withContext
        }
        val file = File(node.path)
        if (!file.exists()) return@withContext
        val total = countFiles(file)
        val sink = ProgressSink(OpType.DELETE, onProgress)
        deleteRecursive(file, sink, total)
    }

    private suspend fun deleteRecursive(file: File, sink: ProgressSink, total: Int): Int {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        var done = 0
        if (file.isDirectory && !Paths.isSymlink(file)) {
            val children = file.listFiles() ?: return 0
            for (c in children) {
                done += if (c.isDirectory) deleteRecursive(c, sink, total) else deleteFile(c, sink, total)
            }
            file.delete()
        } else {
            done = deleteFile(file, sink, total)
        }
        return done
    }

    private suspend fun deleteFile(file: File, sink: ProgressSink, total: Int): Int {
        return if (file.delete()) {
            sink.emit(1L, null, 1, total, file.name)
            1
        } else {
            0
        }
    }

    // ---------------------------------------------------------------- copy

    suspend fun copy(src: FileNode, destDir: FileNode, onProgress: suspend (OpProgress) -> Unit) =
        withContext(Dispatchers.IO) {
            if (src.uri != null || destDir.uri != null) {
                saf.copy(src, destDir, onProgress)
                return@withContext
            }
            val srcFile = File(src.path)
            if (!srcFile.exists()) return@withContext
            val totalBytes = sizeOf(src)
            val sink = ProgressSink(OpType.COPY, onProgress)
            copyRecursive(srcFile, File(destDir.path), sink, totalBytes)
        }

    private suspend fun copyRecursive(src: File, destDir: File, sink: ProgressSink, total: Long) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (src.isDirectory && !Paths.isSymlink(src)) {
            val dest = uniqueFile(destDir, src.name).apply { mkdirs() }
            val children = src.listFiles() ?: return
            for (c in children) copyRecursive(c, dest, sink, total)
        } else {
            val dest = uniqueFile(destDir, src.name)
            copyFile(src, dest, sink, total)
        }
    }

    private suspend fun copyFile(src: File, dest: File, sink: ProgressSink, total: Long) {
        val buffer = ByteArray(64 * 1024)
        var done = 0L
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output ->
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
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
    }

    // ----------------------------------------------------------------- move

    suspend fun move(src: FileNode, destDir: FileNode, onProgress: suspend (OpProgress) -> Unit) =
        withContext(Dispatchers.IO) {
            if (src.uri != null || destDir.uri != null) {
                saf.move(src, destDir, onProgress)
                return@withContext
            }
            val srcFile = File(src.path)
            if (!srcFile.exists()) return@withContext
            val dest = uniqueFile(File(destDir.path), src.name)
            if (srcFile.renameTo(dest)) return@withContext
            // Cross-volume: copy then delete.
            val total = sizeOf(src)
            val sink = ProgressSink(OpType.MOVE, onProgress)
            copyRecursive(srcFile, File(destDir.path), sink, total)
            deleteRecursive(srcFile, ProgressSink(OpType.MOVE, onProgress), countFiles(srcFile))
        }

    // ------------------------------------------------------------ compress

    suspend fun compress(
        sources: List<FileNode>,
        destDir: FileNode,
        name: String,
        onProgress: suspend (OpProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(sources.all { it.uri == null } && destDir.uri == null) {
            "La compresión a SAF aún no está soportada"
        }
        if (sources.isEmpty()) return@withContext
        val dest = uniqueFile(File(destDir.path), if (name.endsWith(".zip", true)) name else "$name.zip")
        val totalBytes = sources.sumOf { sizeOf(it) }
        val sink = ProgressSink(OpType.COMPRESS, onProgress)
        ZipOutputStream(FileOutputStream(dest)).use { zip ->
            var done = 0L
            for (src in sources) {
                done += addToZip(File(src.path), "", zip, sink, done, totalBytes)
            }
            zip.finish()
        }
        sink.emit(totalBytes, totalBytes, sources.size, sources.size, dest.name)
    }

    private suspend fun addToZip(
        file: File,
        prefix: String,
        zip: ZipOutputStream,
        sink: ProgressSink,
        done: Long,
        total: Long,
    ): Long {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (file.isDirectory && !Paths.isSymlink(file)) {
            var d = done
            val children = file.listFiles() ?: return d
            for (c in children) {
                d = addToZip(c, "$prefix${file.name}/", zip, sink, d, total)
            }
            return d
        }
        val entryName = prefix + file.name
        zip.putNextEntry(ZipEntry(entryName))
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        FileInputStream(file).use { input ->
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) {
                    zip.write(buffer, 0, read)
                    copied += read
                    sink.emit(done + copied, total, 1, null, entryName)
                }
            }
        }
        zip.closeEntry()
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
        if (!dir.mkdirs()) return@withContext null
        dir.toNode()
    }

    suspend fun sizeOf(node: FileNode): Long = withContext(Dispatchers.IO) {
        if (node.uri != null) return@withContext saf.sizeOf(node)
        val file = File(node.path)
        if (file.isFile) return@withContext file.length().coerceAtLeast(0L)
        sumSizes(file)
    }

    private suspend fun sumSizes(file: File): Long {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
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
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
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