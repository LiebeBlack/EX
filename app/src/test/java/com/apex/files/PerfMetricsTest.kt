package com.apex.files

import com.apex.files.core.PerfMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerfMetricsTest {

    @Before
    fun reset() {
        PerfMetrics.clear()
    }

    @Test
    fun `time records duration and count`() {
        val list = PerfMetrics.time("test.block", detail = "unit", countOf = { it.size }) {
            listOf("a", "b", "c")
        }
        assertEquals(3, list.size)

        val samples = PerfMetrics.snapshot()
        assertEquals(1, samples.size)
        val sample = samples.first()
        assertEquals("test.block", sample.tag)
        assertEquals(3, sample.count)
        assertEquals("unit", sample.detail)
        assertTrue(sample.durationMillis >= 0)
        assertTrue(sample.atElapsedMillis >= 0)
    }

    @Test
    fun `snapshot keeps newest-last order`() {
        PerfMetrics.report("s1", 1)
        PerfMetrics.report("s2", 2)
        PerfMetrics.report("s3", 3)
        assertEquals(listOf("s1", "s2", "s3"), PerfMetrics.snapshot().map { it.tag })
    }

    @Test
    fun `ring buffer stays bounded`() {
        for (i in 0 until 100) PerfMetrics.report("tag$i", i.toLong())
        val samples = PerfMetrics.snapshot()
        assertEquals(32, samples.size)
        // Oldest entries evicted: the last 32 survive, newest last.
        assertEquals("tag68", samples.first().tag)
        assertEquals("tag99", samples.last().tag)
    }

    @Test
    fun `count is optional and null detail survives`() {
        PerfMetrics.report("bare", 5)
        val sample = PerfMetrics.snapshot().single()
        assertNull(sample.count)
        assertNull(sample.detail)
    }

    @Test
    fun `clear empties the buffer`() {
        PerfMetrics.report("x", 1)
        PerfMetrics.clear()
        assertTrue(PerfMetrics.snapshot().isEmpty())
    }
}
