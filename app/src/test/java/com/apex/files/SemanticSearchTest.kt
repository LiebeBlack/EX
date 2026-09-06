package com.apex.files

import com.apex.files.data.fs.MemoryIndex
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.search.SemanticSearch
import com.apex.files.data.search.SmartGroup
import com.apex.files.data.search.SpanishNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticSearchTest {

    private fun node(name: String, category: Category = Category.DOCUMENT, modified: Long = 0L) = FileNode(
        name = name,
        path = "/fake/$name",
        isDir = false,
        size = 1000L,
        lastModified = modified,
        extension = name.substringAfterLast('.', ""),
        category = category,
    )

    private fun indexOf(vararg nodes: FileNode): MemoryIndex {
        val index = MemoryIndex()
        for (n in nodes) index.put(n)
        return index
    }

    @Test
    fun `natural language query finds by name, date and synonym`() {
        val now = System.currentTimeMillis()
        val day = 24L * 3600_000
        val invoice = node("factura_enero.pdf", modified = now - 5 * day)
        val oldInvoice = node("factura_2024.pdf", modified = now - 400 * day)
        val boleta = node("boleta_marzo.txt", modified = now - 2 * day)
        val index = indexOf(invoice, oldInvoice, boleta)

        // "facturas" expands to {facturas, recibo, boleta, invoice, ...} so
        // boleta matches through the synonym table; the 400-day-old file is
        // excluded by the implicit "mes pasado" date range.
        val results = SemanticSearch.search(
            index = index,
            textOf = { path -> SpanishNormalizer.normalize(path.substringAfterLast('/')) },
            query = "facturas del mes pasado",
        )
        assertTrue(results.contains(invoice))
        assertTrue(results.contains(boleta))
        assertTrue(!results.contains(oldInvoice))
    }

    @Test
    fun `content-only matches are found via OCR text`() {
        val img = node("IMG_20260810.jpg", Category.IMAGE)
        val textOf: (String) -> String = { path ->
            if (path == img.path) "factura total iva 21%" else ""
        }
        val index = indexOf(img, node("paisaje.jpg", Category.IMAGE))
        val results = SemanticSearch.search(index, textOf, "facturas")
        assertEquals(listOf(img), results)
    }

    @Test
    fun `smart group filters candidates`() {
        val receipt = node("recibo_01.pdf")
        val photo = node("foto.jpg", Category.IMAGE)
        val index = indexOf(receipt, photo)
        val results = SemanticSearch.search(
            index = index,
            textOf = { "" },
            query = "",
            smartGroup = SmartGroup.COMPROBANTE,
        )
        assertEquals(listOf(receipt), results)
    }

    @Test
    fun `no matches returns empty`() {
        val index = indexOf(node("paisaje.jpg", Category.IMAGE))
        val results = SemanticSearch.search(index, { "" }, "zzzxyz no existe")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `stopword-only query lists everything`() {
        val a = node("a.pdf")
        val b = node("b.pdf")
        val index = indexOf(a, b)
        val results = SemanticSearch.search(index, { "" }, "de la el")
        assertEquals(2, results.size)
    }

    @Test
    fun `extended filters still apply`() {
        val pdf = node("factura.pdf")
        val jpg = node("factura.jpg", Category.IMAGE)
        val index = indexOf(pdf, jpg)
        val results = SemanticSearch.search(
            index = index,
            textOf = { "" },
            query = "factura",
            extFilter = "*.pdf",
        )
        assertEquals(listOf(pdf), results)
    }
}