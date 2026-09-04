package com.apex.files

import com.apex.files.data.fs.HexFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HexFormatterTest {

    @Test
    fun `empty window formats nothing`() {
        assertEquals(emptyList<String>(), HexFormatter.formatWindow(0L, ByteArray(0)))
    }

    @Test
    fun `single line has offset hex and ascii gutter`() {
        val line = HexFormatter.formatLine(0x40L, byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F))
        assertTrue(line.startsWith("00000040"))
        assertTrue(line.contains("48 65 6C 6C 6F"))
        assertTrue(line.contains("|Hello|"))
    }

    @Test
    fun `non printable ascii becomes dots`() {
        val line = HexFormatter.formatLine(0L, byteArrayOf(0x00, 0x01, 0x02, 0x1F))
        assertTrue(line.contains("|....|"))
    }

    @Test
    fun `short final line pads up to full width`() {
        val line = HexFormatter.formatLine(0L, byteArrayOf(0x41), 16)
        // 48 chars of hex area (2 digits + space per byte, plus center gap)
        assertTrue(line.contains("41"))
        assertTrue(line.contains("3 spaces")) // padded area stays blank
    }

    @Test
    fun `window splits across lines at the byte boundary`() {
        val bytes = ByteArray(40) { it.toByte() }
        val lines = HexFormatter.formatWindow(0L, bytes, 16)
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("00000000"))
        assertTrue(lines[1].startsWith("00000010"))
        assertTrue(lines[2].startsWith("00000020"))
    }

    @Test
    fun `middle gap separates the two hex halves`() {
        val bytes = ByteArray(16) { 0x41 }
        val line = HexFormatter.formatLine(0L, bytes, 16)
        // 8 bytes + 8 bytes with a gap: "41 41 41 41 41 41 41 41  41 41 …"
        assertTrue(line.contains("41 41 41 41 41 41 41 41  41 41"))
    }
}