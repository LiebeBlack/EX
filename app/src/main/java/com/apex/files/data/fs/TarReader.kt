package com.apex.files.data.fs

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

/** A tar archive entry (virtual path inside the archive). */
data class TarEntry(
    val name: String,
    val size: Long,
    val isDir: Boolean,
    val lastModified: Long,
)

/**
 * Minimal native TAR reader: supports ustar and GNU formats, including GNU
 * long names (typeflag 'L') and gracefully skips pax headers ('x'/'g').
 * Zero third-party dependencies.
 */
class TarReader(input: InputStream) : Closeable {

    private val inn = BufferedInputStream(input, 64 * 1024)

    /** Lists every entry without extracting anything. */
    suspend fun readAll(): List<TarEntry> {
        val entries = ArrayList<TarEntry>()
        forEachEntry { entry, _ -> entries.add(entry) }
        return entries
    }

    /**
     * Streams entries in order. For file entries the handler receives an
     * [InputStream] bounded to exactly [TarEntry.size] bytes; it must fully
     * consume it (the reader drains whatever remains before continuing).
     */
    suspend fun forEachEntry(handler: suspend (TarEntry, InputStream?) -> Unit) {
        var pendingLongName: String? = null
        while (true) {
            val header = ByteArray(512)
            val read = readFully(header)
            if (read == 0) break
            if (read < 512) throw IOException("Archivo tar truncado")
            if (header.all { it == 0.toByte() }) break
            verifyChecksum(header)

            val type = header[156].toInt().toChar()
            var name = parseString(header, 0, 100)
            val size = parseNumber(header, 124, 12)
            val mtime = parseNumber(header, 136, 12) * 1000L
            val prefix = parseString(header, 345, 155)
            if (prefix.isNotEmpty() && name.isNotEmpty()) name = "$prefix/$name"

            when (type) {
                // GNU long name: the data block contains the real name.
                'L' -> {
                    val data = ByteArray(size.toInt().coerceAtMost(1 shl 20))
                    readFully(data)
                    pendingLongName = String(data, Charsets.UTF_8).trimEnd('\u0000').trim()
                    skipPadding(size)
                }
                // pax extended/global headers: skip the data block.
                'x', 'g' -> {
                    skipBytes(size)
                    skipPadding(size)
                }
                '5' -> {
                    handler(TarEntry((pendingLongName ?: name).trimEnd('/'), 0L, true, mtime), null)
                    pendingLongName = null
                }
                '0', '\u0000', '7', '1', '2', '3', '4', '6' -> {
                    val finalName = pendingLongName ?: name
                    pendingLongName = null
                    val isFile = type == '0' || type == '\u0000' || type == '7'
                    val entry = TarEntry(finalName.trimEnd('/'), if (isFile) size else 0L, false, mtime)
                    if (isFile && size > 0L) {
                        val bounded = BoundedInputStream(inn, size)
                        handler(entry, bounded)
                        bounded.drain()
                        skipPadding(size)
                    } else {
                        handler(entry, null)
                    }
                }
                else -> {
                    skipBytes(size)
                    skipPadding(size)
                }
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private fun readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = inn.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun skipBytes(n: Long) {
        var remaining = n
        while (remaining > 0) {
            val skipped = inn.skip(remaining)
            if (skipped <= 0L) {
                if (inn.read() < 0) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun skipPadding(size: Long) {
        val pad = (512L - (size % 512L)) % 512L
        if (pad > 0) skipBytes(pad)
    }

    private fun parseString(header: ByteArray, offset: Int, length: Int): String {
        val end = minOf(offset + length, header.size)
        var i = offset
        while (i < end && header[i] != 0.toByte()) i++
        return String(header, offset, i - offset, Charsets.UTF_8)
    }

    private fun parseNumber(header: ByteArray, offset: Int, length: Int): Long {
        val first = header[offset].toInt() and 0xFF
        if (first and 0x80 != 0) {
            // GNU base-256 encoding (positive values: top bit is the sign).
            var value = 0L
            for (i in 0 until length) {
                val byte = if (i == 0) (header[offset + i].toInt() and 0x7F) else (header[offset + i].toInt() and 0xFF)
                value = (value shl 8) or byte.toLong()
            }
            return value
        }
        var text = ""
        for (i in 0 until length) {
            val c = header[offset + i].toInt()
            if (c == 0 || c == 0x20) continue
            text += c.toChar()
        }
        return text.toLongOrNull(8) ?: 0L
    }

    private fun verifyChecksum(header: ByteArray) {
        var sum = 0L
        for (i in header.indices) {
            sum += if (i in 148 until 156) 0x20L else (header[i].toInt() and 0xFF).toLong()
        }
        val expected = parseNumber(header, 148, 8)
        if (expected != 0L && expected != sum) {
            throw IOException("Checksum de cabecera tar inválido")
        }
    }

    override fun close() {
        try {
            inn.close()
        } catch (e: IOException) {
            // ignore
        }
    }
}

/** An [InputStream] that reports EOF after [limit] bytes. */
private class BoundedInputStream(
    private val inner: InputStream,
    private val limit: Long,
) : InputStream() {

    private var remaining = limit

    override fun read(): Int {
        if (remaining <= 0) return -1
        val b = inner.read()
        if (b < 0) return -1
        remaining--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val toRead = minOf(len.toLong(), remaining).toInt()
        val read = inner.read(b, off, toRead)
        if (read < 0) return -1
        remaining -= read
        return read
    }

    fun drain() {
        val buffer = ByteArray(16 * 1024)
        while (remaining > 0) {
            val read = read(buffer)
            if (read < 0) break
        }
    }
}