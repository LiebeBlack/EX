package com.apex.files

import com.apex.files.data.fs.SizeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class SizeFormatterTest {

    @Test
    fun `formats bytes`() {
        assertEquals("0 B", SizeFormatter.format(0))
        assertEquals("512 B", SizeFormatter.format(512))
        assertEquals("1023 B", SizeFormatter.format(1023))
    }

    @Test
    fun `formats kb mb gb tb`() {
        assertEquals("1.0 KB", SizeFormatter.format(1024))
        assertEquals("1.5 KB", SizeFormatter.format(1536))
        assertEquals("1.0 MB", SizeFormatter.format(1024L * 1024))
        assertEquals("2.5 MB", SizeFormatter.format((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.00 GB", SizeFormatter.format(1024L * 1024 * 1024))
        assertEquals("3.00 TB", SizeFormatter.format(3L * 1024 * 1024 * 1024 * 1024))
    }

    @Test
    fun `negative sizes clamp to zero`() {
        assertEquals("0 B", SizeFormatter.format(-5))
    }
}