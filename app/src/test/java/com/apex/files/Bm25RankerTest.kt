package com.apex.files

import com.apex.files.data.fs.SearchFilters
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.search.Bm25Ranker
import com.apex.files.data.search.RelativeDateParser
import com.apex.files.data.search.SynonymExpander
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Bm25RankerTest {

    private fun node(name: String, category: Category = Category.OTHER) = FileNode(
        name = name,
        path = "/fake/$name",
        isDir = false,
        size = 100L,
        lastModified = 0L,
        extension = name.substringAfterLast('.', ""),
        category = category,
    )

    @Test
    fun `name matches rank above content-only matches`() {
        val invoiceNamed = node("factura_mensual.pdf", Category.DOCUMENT)
        val contentOnly = node("scan_001.pdf", Category.DOCUMENT)
        val textOf: (String) -> String = { path ->
            if (path == contentOnly.path) "factura total iva" else ""
        }
        val terms = SynonymExpander.expand(com.apex.files.data.search.SpanishNormalizer.tokens("facturas"))
        val ranked = Bm25Ranker.score(
            listOf(invoiceNamed, contentOnly),
            textOf,
            terms,
            limit = 10,
        )
        assertEquals(invoiceNamed, ranked[0])
        assertTrue(ranked.contains(contentOnly))
    }

    @Test
    fun `documents without any term are dropped`() {
        val hit = node("recibo.png", Category.IMAGE)
        val miss = node("paisaje.jpg", Category.IMAGE)
        val terms = SynonymExpander.expand(listOf("recibo", "invoice"))
        val ranked = Bm25Ranker.score(listOf(hit, miss), { "" }, terms, limit = 10)
        assertEquals(listOf(hit), ranked)
    }

    @Test
    fun `empty terms produce no results`() {
        val ranked = Bm25Ranker.score(listOf(node("a.txt")), { "" }, emptyMap(), limit = 10)
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `relative date parser extracts month range`() {
        val parsed = RelativeDateParser.parse("facturas del mes pasado")
        assertEquals(SearchFilters.DateRange.MONTH, parsed.dateRange)
        assertEquals("facturas", parsed.query)
    }

    @Test
    fun `relative size parser extracts giant band`() {
        val parsed = RelativeDateParser.parse("archivos grandes")
        assertEquals(SearchFilters.SizeBand.GIANT, parsed.sizeBand)
        assertEquals("archivos", parsed.query)
    }

    @Test
    fun `plain queries keep everything`() {
        val parsed = RelativeDateParser.parse("fotos de gatos")
        assertEquals(null, parsed.dateRange)
        assertEquals(null, parsed.sizeBand)
        assertEquals("fotos de gatos", parsed.query)
    }
}