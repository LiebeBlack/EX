package com.apex.files

import com.apex.files.data.fs.DateFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateFormatterTest {

    // Fixed reference time: 2024-06-15 12:00:00 local
    private val now = 1718452800000L
    private val hour = 3600_000L
    private val day = 24L * hour

    @Test
    fun `today shows only the time`() {
        val ts = now - 2 * hour
        val out = DateFormatter.format(ts, now)
        assertEquals("10:00", out)
    }

    @Test
    fun `yesterday is prefixed`() {
        val ts = now - day - 3 * hour
        val out = DateFormatter.format(ts, now)
        assertTrue(out.startsWith("Ayer"))
    }

    @Test
    fun `older within the year shows day and month`() {
        val ts = now - 40 * day // 2024-05-06
        val out = DateFormatter.format(ts, now)
        assertTrue("esperado mes/día, fue: $out", out.contains("may"))
    }

    @Test
    fun `previous year shows the full date`() {
        val ts = now - 400 * day
        val out = DateFormatter.format(ts, now)
        assertTrue(out.contains("2023"))
    }

    @Test
    fun `invalid timestamps render a dash`() {
        assertEquals("—", DateFormatter.format(0, now))
    }
}