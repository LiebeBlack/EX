package com.apex.files

import com.apex.files.data.search.SpanishNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanishNormalizerTest {

    @Test
    fun `normalize lowercases and strips accents`() {
        assertEquals("factura ultimo mes", SpanishNormalizer.normalize("FACTURA ÚLTIMO MÉS"))
        assertEquals("fotos de gatos", SpanishNormalizer.normalize("Fótós dë gätós"))
        assertEquals("nino", SpanishNormalizer.normalize("niño"))
    }

    @Test
    fun `tokens drop stopwords and punctuation`() {
        assertEquals(
            listOf("facturas", "semana"),
            SpanishNormalizer.tokens("facturas de la semana"),
        )
        assertEquals(
            listOf("recibo", "enero", "pdf"),
            SpanishNormalizer.tokens("Recibo, enero.pdf"),
        )
    }

    @Test
    fun `empty and stopword-only inputs yield no tokens`() {
        assertTrue(SpanishNormalizer.tokens("").isEmpty())
        assertTrue(SpanishNormalizer.tokens("de el la y o").isEmpty())
        assertTrue(SpanishNormalizer.tokens("   ").isEmpty())
    }

    @Test
    fun `digits and words are kept as tokens`() {
        val tokens = SpanishNormalizer.tokens("recibo 2026")
        assertTrue(tokens.contains("2026"))
        assertTrue(tokens.contains("recibo"))
    }
}