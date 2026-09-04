package com.apex.files

import com.apex.files.tools.ExifReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifReaderTest {

    /** One TIFF directory entry (tag, type, component count, value). */
    private data class TagVal(val tag: Int, val type: Int, val count: Int, val value: Long)

    /** In-memory little-endian TIFF writer. */
    private class ByteArrayBuilder {
        // Not private: file-scope extension functions (ifd/tag) need it.
        val data = ArrayList<Byte>()

        fun byte(b: Int) { data.add(b.toByte()) }
        fun bytes(b: ByteArray) { b.forEach { data.add(it) } }
        fun u16(v: Int) {
            data.add((v and 0xFF).toByte())
            data.add(((v ushr 8) and 0xFF).toByte())
        }
        fun u32(v: Long) {
            data.add((v and 0xFF).toByte())
            data.add(((v ushr 8) and 0xFF).toByte())
            data.add(((v ushr 16) and 0xFF).toByte())
            data.add(((v ushr 24) and 0xFF).toByte())
        }
        fun bytes(): ByteArray = data.toByteArray()
    }

    /** Builds a minimal JPEG: SOI + APP1(Exif/TIFF) + SOF0 + EOI. */
    private fun jpegWithExif(build: ByteArrayBuilder.() -> Unit): ByteArray {
        val tiff = ByteArrayBuilder()
        tiff.byte(0x49); tiff.byte(0x49) // little-endian
        tiff.u16(42)                     // TIFF magic
        tiff.u32(8)                      // IFD0 at offset 8
        build(tiff)
        val tiffBytes = tiff.bytes()

        val app1 = ByteArrayBuilder()
        app1.bytes("Exif\u0000\u0000".toByteArray())
        app1.bytes(tiffBytes)
        val app1Data = app1.bytes()

        val out = ByteArrayBuilder()
        out.byte(0xFF); out.byte(0xD8)      // SOI
        out.byte(0xFF); out.byte(0xE1)      // APP1
        out.u16(app1Data.size + 2)          // segment length (incl. its 2 bytes)
        out.bytes(app1Data)
        out.byte(0xFF); out.byte(0xC0)      // SOF0
        out.u16(4 + 6 + 1)
        out.byte(8); out.u16(6); out.u16(8); out.byte(3)
        out.byte(0xFF); out.byte(0xD9)      // EOI
        return out.bytes()
    }

    private fun ByteArrayBuilder.tag(tag: Int, type: Int, count: Int, value: Long) {
        u16(tag); u16(type); u32(count.toLong()); u32(value)
    }

    /** Writes an IFD with [entries]; returns its offset (current position). */
    private fun ByteArrayBuilder.ifd(entries: List<TagVal>): Int {
        val offset = data.size
        u16(entries.size)
        for (e in entries) tag(e.tag, e.type, e.count, e.value)
        u32(0) // next IFD
        return offset
    }

    // ---------------------------------------------------------------- tests

    @Test
    fun `non jpeg returns empty data`() {
        val data = ExifReader.read("no es un jpeg".toByteArray())
        assertFalse(data.hasExif)
        assertNull(data.model)
    }

    @Test
    fun `parses make model and orientation from ifd0`() {
        val bytes = jpegWithExif {
            // IFD0 at offset 8: Make, Model, Orientation
            val makeOffset = 8 + 2 + 3 * 12 + 4
            val modelOffset = makeOffset + 6 // "Canon\0"
            val entries = listOf(
                TagVal(0x010F, 2, 5, makeOffset.toLong()),   // ASCII "Canon"
                TagVal(0x0110, 2, 6, modelOffset.toLong()),  // ASCII "EOS R6"
                TagVal(0x0112, 3, 1, 6L),                    // SHORT orientation=6
            )
            ifd(entries)
            bytes("Canon\u0000".toByteArray())
            bytes("EOS R6\u0000".toByteArray())
        }
        val data = ExifReader.read(bytes)
        assertTrue(data.hasExif)
        assertEquals("Canon", data.make)
        assertEquals("EOS R6", data.model)
        assertEquals(6, data.orientation)
    }

    @Test
    fun `reads exif subifd exposure and iso`() {
        val bytes = jpegWithExif {
            // IFD0 with a pointer (tag 0x8769, type LONG) to the EXIF sub-IFD.
            val exifIfdOffset = 8 + 2 + 1 * 12 + 4
            val entries = listOf(TagVal(0x8769, 4, 1, exifIfdOffset.toLong()))
            ifd(entries)
            // EXIF IFD: two SHORT tags stored inline.
            ifd(
                listOf(
                    TagVal(0x8827, 3, 1, 320L), // ISO 320
                    TagVal(0x9209, 3, 1, 1L),   // Flash fired
                )
            )
        }
        val data = ExifReader.read(bytes)
        assertTrue(data.hasExif)
        assertEquals(320, data.iso)
        assertEquals(1, data.flash)
    }

    @Test
    fun `non exif app1 is ignored and returns empty`() {
        val out = ByteArrayBuilder()
        out.byte(0xFF); out.byte(0xD8)
        out.byte(0xFF); out.byte(0xE1)
        out.bytes("MetadataHere".toByteArray())
        out.byte(0xFF); out.byte(0xD9)
        val data = ExifReader.read(out.bytes())
        assertFalse(data.hasExif)
    }
}