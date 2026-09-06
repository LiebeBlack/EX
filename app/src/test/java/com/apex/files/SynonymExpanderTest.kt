package com.apex.files

import com.apex.files.data.search.SynonymExpander
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynonymExpanderTest {

    @Test
    fun `original terms weigh 1`() {
        val expanded = SynonymExpander.expand(listOf("facturas"))
        assertEquals(1.0, expanded["facturas"] ?: 0.0, 0.001)
    }

    @Test
    fun `cluster members are added with lower weight`() {
        val expanded = SynonymExpander.expand(listOf("facturas"))
        assertTrue("recibo" in expanded)
        assertTrue("invoice" in expanded)
        assertEquals(0.6, expanded["recibo"] ?: 0.0, 0.001)
    }

    @Test
    fun `singular fallback resolves the cluster`() {
        // "factura" and "facturas" belong to the same cluster either way.
        val a = SynonymExpander.expand(listOf("factura"))
        val b = SynonymExpander.expand(listOf("facturas"))
        assertEquals(a["recibo"], b["recibo"])
    }

    @Test
    fun `unknown words are kept as-is`() {
        val expanded = SynonymExpander.expand(listOf("zzzxyz"))
        assertEquals(setOf("zzzxyz"), expanded.keys)
        assertEquals(1.0, expanded["zzzxyz"] ?: 0.0, 0.001)
    }

    @Test
    fun `empty input expands to nothing`() {
        assertTrue(SynonymExpander.expand(emptyList()).isEmpty())
    }
}