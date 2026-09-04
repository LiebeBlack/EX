package com.apex.files.data.fs

import android.content.Context
import com.apex.files.core.OpProgress
import com.apex.files.core.OpType
import com.apex.files.core.SpeedTracker
import com.apex.files.data.model.FileNode
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** An entry inside a compressed archive, browsed as a virtual file. */
data class ArchiveEntry(
    val name: String,
    val size: Long,
    val isDir: Boolean,
    val lastModified: Long,
)

/**
 * Native archive engine: .zip via java.util.zip, .tar / .tar.gz via
 * [TarReader] over (optionally gzipped) streams, .gz as single-stream
 * decompression. Entries are listed virtually; extraction streams only the
 * requested entries to disk — the whole archive is never unpacked.
 *
 * Extraction reports an honest [OpResult] and pauses on destination
 * collisions through [onConflict] (default keeps the historical
 * overwrite-when-no-callback behavior).
 */
class ArchiveRepository(private val context: Context, private val fs: FsRepository) {

    sealed interface Handle : Closeable {
        suspend fun entries(): List<ArchiveEntry>
        fun name(): String
    }

    fun isSupported(node: FileNode): Boolean = when (extensionOf(node.name)) {
        "zip", "jar", "cbz", "tar", "tgz", "tar.gz", "gz" -> true
        else -> false
    }

    private fun extensionOf(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".tar.gz") -> "tar.gz"
            else -> lower.substringAfterLast('.', "")
        }
    }

    fun open(node: FileNode): Handle {
        val file = fs.fileForReading(node)
        return when (extensionOf(node.name)) {
            "zip", "jar", "cbz" -> ZipHandle(ZipFile(file))
            "tar" -> TarHandle(node.name, TarReader(FileInputStream(file)))
            "tgz", "tar.gz" -> TarHandle(
                node.name,
                TarReader(GZIPInputStream(BufferedInputStream(FileInputStream(file), 64 * 1024))),
            )
            "gz" -> GzHandle(node.name, file)
            else -> throw IOException("Formato de archivo no soportado")
        }
    }

    /**
     * Extracts an entry (file or whole directory) from [archiveNode] into
     * [destDir] (must be File-backed). Streams only the matching data — the
     * full archive is never unpacked to disk.
     */
    suspend fun extract(
        entry: ArchiveEntry,
        archiveNode: FileNode,
        destDir: FileNode,
        onProgress: suspend (OpProgress) -> Unit,
        onConflict: (suspend (Conflict) -> ConflictDecision)? = null,
    ): OpResult = withContext(Dispatchers.IO) {
        require(destDir.uri == null) { "La extracción a SAF aún no está soportada" }
        val base = File(destDir.path)
        val ext = extensionOf(archiveNode.name)
        val sink = ProgressSink(onProgress)
        val acc = OpAccumulator()
        when (ext) {
            "zip", "jar", "cbz" -> {
                ZipFile(fs.fileForReading(archiveNode)).use { zip ->
                    if (entry.isDir) {
                        val prefix = entry.name.trimEnd('/') + "/"
                        val target = File(base, safeName(entry.name))
                        if (!target.exists() && !target.mkdirs()) acc.error("No se pudo crear la carpeta ${entry.name}")
                        val e = zip.entries()
                        while (e.hasMoreElements()) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val ze = e.nextElement()
                            if (!ze.name.startsWith(prefix)) continue
                            val rel = ze.name.removePrefix(prefix)
                            if (rel.isEmpty()) continue
                            if (ze.isDirectory) {
                                File(target, safeName(rel)).mkdirs()
                            } else {
                                val dest = resolveWriteTarget(File(target, safeName(rel)), ze.name, acc, onConflict) ?: continue
                                copyEntry(zip.getInputStream(ze), dest, ze.size, sink, ze.name, acc)
                            }
                        }
                    } else {
                        val zipEntry = zip.getEntry(entry.name)
                            ?: throw IOException("Entrada no encontrada: ${entry.name}")
                        val dest = resolveWriteTarget(File(base, safeName(entry.name)), entry.name, acc, onConflict)
                            ?: return@withContext acc.result()
                        copyEntry(zip.getInputStream(zipEntry), dest, entry.size, sink, entry.name, acc)
                    }
                }
            }
            "tar", "tgz", "tar.gz" -> {
                val file = fs.fileForReading(archiveNode)
                val reader = if (ext == "tar") {
                    TarReader(FileInputStream(file))
                } else {
                    TarReader(GZIPInputStream(BufferedInputStream(FileInputStream(file), 64 * 1024)))
                }
                reader.use {
                    val prefix = entry.name.trimEnd('/')
                    var found = false
                    val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                    it.forEachEntry { tarEntry, stream ->
                        if (job?.isActive == false) throw kotlinx.coroutines.CancellationException("extracción cancelada")
                        val name = tarEntry.name.trimEnd('/')
                        if (name == prefix) {
                            found = true
                            if (tarEntry.isDir) File(base, safeName(name)).mkdirs()
                            else if (stream != null) {
                                val dest = resolveWriteTarget(File(base, safeName(name)), name, acc, onConflict) ?: return@forEachEntry
                                copyEntry(stream, dest, tarEntry.size, sink, name, acc)
                            }
                        } else if (entry.isDir && name.startsWith(prefix + "/")) {
                            found = true
                            if (tarEntry.isDir) {
                                File(base, safeName(name)).mkdirs()
                            } else if (stream != null) {
                                val dest = resolveWriteTarget(File(base, safeName(name)), name, acc, onConflict) ?: return@forEachEntry
                                copyEntry(stream, dest, tarEntry.size, sink, name, acc)
                            }
                        }
                    }
                    if (!found) throw IOException("Entrada no encontrada: ${entry.name}")
                }
            }
            "gz" -> {
                GZIPInputStream(BufferedInputStream(FileInputStream(fs.fileForReading(archiveNode)), 64 * 1024)).use { gz ->
                    val outName = archiveNode.name.removeSuffix(".gz").ifBlank { "extraido" }
                    val dest = resolveWriteTarget(File(base, outName), outName, acc, onConflict) ?: return@withContext acc.result()
                    copyEntry(gz, dest, -1L, sink, outName, acc)
                }
            }
            else -> throw IOException("Formato de archivo no soportado")
        }
        acc.result()
    }

    /**
     * Extracts the whole archive into [destDir]. Top-level entries are
     * extracted one by one (directories stream their full subtree in a
     * single pass); conflicts resolve through [onConflict].
     */
    suspend fun extractAll(
        archiveNode: FileNode,
        destDir: FileNode,
        onProgress: suspend (OpProgress) -> Unit,
        onConflict: (suspend (Conflict) -> ConflictDecision)? = null,
    ): OpResult = withContext(Dispatchers.IO) {
        require(destDir.uri == null) { "La extracción a SAF aún no está soportada" }
        val acc = OpAccumulator()
        open(archiveNode).use { handle ->
            val entries = handle.entries()
            val topLevel = entries.filter { it.isDir || !it.name.contains('/') }
            for (entry in topLevel) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                acc += extract(entry, archiveNode, destDir, onProgress, onConflict)
            }
        }
        acc.result()
    }

    /**
     * When the destination file already exists: without a resolver the
     * historical overwrite behavior applies; with one the user decides
     * (skip / overwrite / keep both / cancel the whole operation).
     */
    private suspend fun resolveWriteTarget(
        dest: File,
        displayName: String,
        acc: OpAccumulator,
        onConflict: (suspend (Conflict) -> ConflictDecision)?,
    ): File? {
        if (!dest.exists()) return dest
        val parent = dest.parentFile
        val decision = onConflict?.invoke(
            Conflict(dest.name, parent?.absolutePath ?: "", isDir = false, existingSize = dest.length(), existingModified = dest.lastModified())
        ) ?: ConflictDecision.OVERWRITE
        return when (decision) {
            ConflictDecision.OVERWRITE -> dest
            ConflictDecision.SKIP -> {
                acc.skipped++
                null
            }
            ConflictDecision.CANCEL_OPERATION -> throw ConflictCancelledException()
            ConflictDecision.KEEP_BOTH -> uniqueSibling(parent ?: dest.parentFile, dest.name)
        }
    }

    private fun uniqueSibling(dir: File, name: String): File {
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

    private suspend fun copyEntry(
        input: java.io.InputStream,
        dest: File,
        size: Long,
        sink: ProgressSink,
        name: String,
        acc: OpAccumulator,
    ) {
        dest.parentFile?.mkdirs()
        val buffer = ByteArray(64 * 1024)
        var done = 0L
        try {
            input.use { src ->
                FileOutputStream(dest).use { out ->
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val read = src.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            out.write(buffer, 0, read)
                            done += read
                            sink.emit(done, if (size > 0) size else null, 1, null, name)
                        }
                    }
                    out.flush()
                }
            }
            acc.bytes += done
            acc.files++
        } catch (e: Exception) {
            acc.error("Error extrayendo $name: ${e.message.orEmpty()}")
            runCatching { dest.delete() }
        }
    }

    /** Removes traversal components from archive paths. */
    private fun safeName(name: String): String {
        val parts = name.split('/')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        return parts.joinToString(File.separator)
    }

    private class ZipHandle(private val zip: ZipFile) : Handle {
        override suspend fun entries(): List<ArchiveEntry> {
            val list = ArrayList<ArchiveEntry>()
            val e = zip.entries()
            while (e.hasMoreElements()) {
                val entry = e.nextElement()
                val isDir = entry.isDirectory
                list.add(
                    ArchiveEntry(
                        name = entry.name.trimEnd('/'),
                        size = if (isDir) 0L else entry.size,
                        isDir = isDir,
                        lastModified = entry.time,
                    )
                )
            }
            return list
        }

        override fun name(): String = zip.name

        override fun close() {
            try {
                zip.close()
            } catch (e: IOException) {
                // ignore
            }
        }
    }

    private class TarHandle(private val archiveName: String, private val reader: TarReader) : Handle {
        override suspend fun entries(): List<ArchiveEntry> = reader.readAll().map {
            ArchiveEntry(it.name, it.size, it.isDir, it.lastModified)
        }

        override fun name(): String = archiveName

        override fun close() = reader.close()
    }

    private class GzHandle(private val archiveName: String, private val file: File) : Handle {
        private val outName: String = archiveName.removeSuffix(".gz").ifBlank { "extraido" }

        override suspend fun entries(): List<ArchiveEntry> = listOf(
            ArchiveEntry(outName, -1L, isDir = false, lastModified = file.lastModified())
        )

        override fun name(): String = archiveName

        override fun close() {}
    }

    private class ProgressSink(private val onProgress: suspend (OpProgress) -> Unit) {
        private val tracker = SpeedTracker()
        suspend fun emit(bytesDone: Long, bytesTotal: Long?, filesDone: Int = 0, filesTotal: Int? = null, current: String = "") {
            val speed = tracker.update(bytesDone)
            onProgress(OpProgress(OpType.EXTRACT, bytesDone, bytesTotal, filesDone, filesTotal, current, speed))
        }
    }
}
