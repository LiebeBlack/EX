package com.apex.files

import com.apex.files.data.fs.TarReader
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class TarReaderTest {

    private fun writeField(header: ByteArray, offset: Int, value: String, length: Int) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        for (i in bytes.indices) {
            if (offset + i < header.size) header[offset + i] = bytes[i]
        }
    }

    private fun header(
        name: String,
        size: Long,
        type: Char,
        mtime: Long = 1_700_000_000L,
    ): ByteArray {
        val h = ByteArray(512)
        writeField(h, 0, name, 100)
        writeField(h, 100, "0000644\u0000", 8)
        writeField(h, 108, "0000000\u0000", 8)
        writeField(h, 116, "0000000\u0000", 8)
        writeField(h, 124, size.toString(8).padStart(11, '0') + "\u0000", 12)
        writeField(h, 136, mtime.toString(8).padStart(11, '0') + "\u0000", 12)
        h[156] = type.code.toByte()
        writeField(h, 257, "ustar\u0000", 6)
        writeField(h, 263, "00", 2)
        writeField(h, 265, "root", 32)
        writeField(h, 297, "root", 32)
        // checksum computed with the checksum field blanked to spaces
        for (i in 148 until 156) h[i] = 0x20.toByte()
        val sum = h.sumOf { it.toInt() and 0xFF }
        val checksum = sum.toString(8).padStart(6, '0') + "\u0000 "
        writeField(h, 148, checksum, 8)
        return h
    }

    private fun dataBlock(data: ByteArray): ByteArray {
        val padded = ByteArray(((data.size + 511) / 512) * 512)
        data.copyInto(padded)
        return padded
    }

    private fun buildTar(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(header("hello.txt", 5, '0'))
        out.write(dataBlock("hello".toByteArray()))
        out.write(header("dir/", 0, '5'))
        out.write(header("dir/nested.txt", 3, '0'))
        out.write(dataBlock("abc".toByteArray()))
        out.write(ByteArray(1024)) // end-of-archive zero blocks
        return out.toByteArray()
    }

    @Test
    fun `reads ustar entries with sizes and types`() {
        TarReader(ByteArrayInputStream(buildTar())).use { reader ->
            val entries = reader.readAll()
            assertEquals(3, entries.size)
            assertEquals("hello.txt", entries[0].name)
            assertEquals(5L, entries[0].size)
            assertEquals(false, entries[0].isDir)
            assertEquals("dir", entries[1].name)
            assertEquals(true, entries[1].isDir)
            assertEquals("dir/nested.txt", entries[2].name)
            assertEquals(3L, entries[2].size)
            assertEquals(false, entries[2].isDir)
        }
    }

    @Test
    fun `streams file content through the entry input`() {
        TarReader(ByteArrayInputStream(buildTar())).use { reader ->
            var content: String? = null
            reader.forEachEntry { entry, stream ->
                if (entry.name == "hello.txt") {
                    content = stream?.readBytes()?.toString(Charsets.UTF_8)
                }
            }
            assertEquals("hello", content)
        }
    }

    @Test
    fun `supports GNU long names`() {
        val longName = "carpeta/archivo_con_un_nombre_muy_largo_para_probar_el_soporte_gnu.txt"
        val out = java.io.ByteArrayOutputStream()
        // GNU long-name header: type 'L', data = the real name
        out.write(header("", longName.length.toLong(), 'L'))
        out.write(dataBlock(longName.toByteArray()))
        // real header with a placeholder name
        out.write(header("placeholder", 0, '0'))
        out.write(ByteArray(1024))

        TarReader(ByteArrayInputStream(out.toByteArray())).use { reader ->
            val entries = reader.readAll()
            assertEquals(1, entries.size)
            assertEquals(longName, entries[0].name)
        }
    }

    @Test
    fun `rejects corrupted checksums`() {
        val bytes = buildTar()
        bytes[0] = 0x58 // corrupt the name byte
        var thrown = false
        try {
            TarReader(ByteArrayInputStream(bytes)).use { it.readAll() }
        } catch (e: Exception) {
            thrown = true
        }
        assertEquals(true, thrown)
    }
}