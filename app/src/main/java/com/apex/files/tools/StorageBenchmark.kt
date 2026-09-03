package com.apex.files.tools

import android.content.Context
import com.apex.files.core.OpProgress
import com.apex.files.core.OpType
import com.apex.files.core.SpeedTracker
import com.apex.files.data.fs.FsRepository
import com.apex.files.data.fs.SafRepository
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Measures real read/write throughput on a volume by writing and reading a
 * temporary payload (32 MB, 64 KB chunks, fsync'd) and deleting it after.
 */
class StorageBenchmark(private val context: Context, private val fs: FsRepository) {

    data class Result(
        val writeMbps: Double,
        val readMbps: Double,
        val writeMs: Long,
        val readMs: Long,
        val payloadBytes: Long,
    ) {
        val writeLabel: String get() = "%.1f MB/s".format(java.util.Locale.US, writeMbps)
        val readLabel: String get() = "%.1f MB/s".format(java.util.Locale.US, readMbps)
    }

    suspend fun run(location: Location, payloadBytes: Long = 32L * 1024 * 1024): Result {
        val tmpName = "apex_bench_${System.currentTimeMillis()}.tmp"
        val file = withContext(Dispatchers.IO) {
            when (location) {
                is Location.Fs -> File(location.root, tmpName)
                is Location.Saf -> null // SAF trees benchmarked via their streams below
            }
        }

        return try {
            val writeMs = withContext(Dispatchers.IO) { measureWrite(file, location, tmpName, payloadBytes) }
            val readMs = withContext(Dispatchers.IO) { measureRead(file, location, tmpName, payloadBytes) }
            Result(
                writeMbps = payloadBytes / 1024.0 / 1024.0 / (maxOf(writeMs, 1L) / 1000.0),
                readMbps = payloadBytes / 1024.0 / 1024.0 / (maxOf(readMs, 1L) / 1000.0),
                writeMs = writeMs,
                readMs = readMs,
                payloadBytes = payloadBytes,
            )
        } finally {
            withContext(Dispatchers.IO) {
                if (file != null) {
                    file.delete()
                } else {
                    val root = SafRepository(context).document(
                        FileNode.forDirectory(location.label, location.label, uri = location.rootUri, isRoot = true)
                    )
                    root?.findFile(tmpName)?.delete()
                }
            }
        }
    }

    private suspend fun measureWrite(file: File?, location: Location, name: String, payload: Long): Long {
        val output = openOutput(file, location, name)
        val chunk = ByteArray(64 * 1024)
        Random(42).nextBytes(chunk)
        val start = System.currentTimeMillis()
        output.use { out ->
            var written = 0L
            while (written < payload) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val n = minOf(chunk.size.toLong(), payload - written).toInt()
                out.write(chunk, 0, n)
                written += n
            }
            out.flush()
            (out as? FileOutputStream)?.fd?.sync()
        }
        return System.currentTimeMillis() - start
    }

    private suspend fun measureRead(file: File?, location: Location, name: String, payload: Long): Long {
        val input = openInput(file, location, name)
        val chunk = ByteArray(64 * 1024)
        val start = System.currentTimeMillis()
        input.use { inn ->
            var total = 0L
            while (total < payload) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val read = inn.read(chunk)
                if (read < 0) break
                total += read
            }
        }
        return System.currentTimeMillis() - start
    }

    private fun openOutput(file: File?, location: Location, name: String): OutputStream = when (location) {
        is Location.Fs -> FileOutputStream(file!!)
        is Location.Saf -> {
            val root = SafRepository(context).document(
                FileNode.forDirectory(location.label, location.label, uri = location.rootUri, isRoot = true)
            )!!
            val doc = root.createFile("application/octet-stream", name)!!
            context.contentResolver.openOutputStream(doc.uri)!!
        }
    }

    private fun openInput(file: File?, location: Location, name: String): java.io.InputStream = when (location) {
        is Location.Fs -> file!!.inputStream()
        is Location.Saf -> {
            val root = SafRepository(context).document(
                FileNode.forDirectory(location.label, location.label, uri = location.rootUri, isRoot = true)
            )!!
            val doc = root.findFile(name) ?: error("Archivo temporal no encontrado")
            context.contentResolver.openInputStream(doc.uri)!!
        }
    }

    /** Progress events for the Operation Center (optional). */
    fun runWithProgress(location: Location, onProgress: (OpProgress) -> Unit): Flow<OpProgress> = flow {
        val tracker = SpeedTracker()
        onProgress(OpProgress(OpType.BENCHMARK, currentName = location.label))
        val result = run(location)
        onProgress(OpProgress(OpType.BENCHMARK, bytesDone = 1, bytesTotal = 1, currentName = result.writeLabel))
        emit(OpProgress(OpType.BENCHMARK, bytesDone = 1, bytesTotal = 1, currentName = "${result.writeLabel} · ${result.readLabel}"))
    }
}