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
 */
class ArchiveRepository(private val context: Context, private val fs: FsRepository) {

    sealed interface Handle : Closeable {
        fun entries(): List<ArchiveEntry>
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
    ) = withContext(Dispatchers.IO) {
        require(destDir.uri == null) { "La extracción a SAF aún no está soportada" }
        val base = File(destDir.path)
        val ext = extensionOf(archiveNode.name)
        val sink = ProgressSink(onProgress)
        when (ext) {
            "zip", "jar", "cbz" -> {
                ZipFile(fs.fileForReading(archiveNode)).use { zip ->
                    if (entry.isDir) {
                        val prefix = entry.name.trimEnd('/') + "/"
                        val target = File(base, safeName(entry.name))
                        target.mkdirs()
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
                                copyEntry(zip.getInputStream(ze), File(target, safeName(rel)), ze.size, sink, ze.name)
                            }
                        }
                    } else {
                        val zipEntry = zip.getEntry(entry.name)
                            ?: throw IOException("Entrada no encontrada: ${entry.name}")
                        copyEntry(
                            zip.getInputStream(zipEntry),
                            File(base, safeName(entry.name)),
                            entry.size,
                            sink,
                            entry.name,
                        )
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
                            else if (stream != null) copyEntry(stream, File(base, safeName(name)), tarEntry.size, sink, name)
                        } else if (entry.isDir && name.startsWith(prefix + "/")) {
                            found = true
                            if (tarEntry.isDir) {
                                File(base, safeName(name)).mkdirs()
                            } else if (stream != null) {
                                copyEntry(stream, File(base, safeName(name)), tarEntry.size, sink, name)
                            }
                        }
                    }
                    if (!found) throw IOException("Entrada no encontrada: ${entry.name}")
                }
            }
            "gz" -> {
                GZIPInputStream(BufferedInputStream(FileInputStream(fs.fileForReading(archiveNode)), 64 * 1024)).use { gz ->
                    val outName = archiveNode.name.removeSuffix(".gz").ifBlank { "extraido" }
                    copyEntry(gz, File(base, outName), -1L, sink, outName)
                }
            }
            else -> throw IOException("Formato de archivo no soportado")
        }
    }

    private suspend fun copyEntry(input: java.io.InputStream, dest: File, size: Long, sink: ProgressSink, name: String) {
        dest.parentFile?.mkdirs()
        val buffer = ByteArray(64 * 1024)
        var done = 0L
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
    }

    /** Removes traversal components from archive paths. */
    private fun safeName(name: String): String {
        val parts = name.split('/')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        return parts.joinToString(File.separator)
    }

    private class ZipHandle(private val zip: ZipFile) : Handle {
        override fun entries(): List<ArchiveEntry> {
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
        override fun entries(): List<ArchiveEntry> = reader.readAll().map {
            ArchiveEntry(it.name, it.size, it.isDir, it.lastModified)
        }

        override fun name(): String = archiveName

        override fun close() = reader.close()
    }

    private class GzHandle(private val archiveName: String, private val file: File) : Handle {
        private val outName: String = archiveName.removeSuffix(".gz").ifBlank { "extraido" }

        override fun entries(): List<ArchiveEntry> = listOf(
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