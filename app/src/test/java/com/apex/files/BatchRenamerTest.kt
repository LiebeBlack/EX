package com.apex.files

import com.apex.files.data.fs.BatchRenamer
import com.apex.files.data.fs.BatchRenamer.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRenamerTest {

    private fun namesOf(plan: BatchRenamer.Plan): Map<String, String> =
        plan.items.associate { it.from to it.to }

    @Test
    fun `find and replace on stem preserves extension`() {
        val plan = BatchRenamer.plan(
            listOf("foto_vieja.jpg", "imagen_vieja.png"),
            Options(find = "_vieja", replace = "_nueva"),
        )
        assertTrue(plan.errors.isEmpty())
        val map = namesOf(plan)
        assertEquals("foto_nueva.jpg", map["foto_vieja.jpg"])
        assertEquals("imagen_nueva.png", map["imagen_vieja.png"])
    }

    @Test
    fun `prefix and suffix wrap the stem before the extension`() {
        val plan = BatchRenamer.plan(
            listOf("reporte.txt"),
            Options(prefix = "2026_", suffix = "_final"),
        )
        val map = namesOf(plan)
        assertEquals("2026_reporte_final.txt", map["reporte.txt"])
    }

    @Test
    fun `renumbering pads zeroes and starts at the given number`() {
        val plan = BatchRenamer.plan(
            listOf("a.jpg", "b.jpg", "c.jpg"),
            Options(renumber = true, start = 5, digits = 3),
        )
        assertTrue(plan.errors.isEmpty())
        val map = namesOf(plan)
        assertEquals("a_005.jpg", map["a.jpg"])
        assertEquals("b_006.jpg", map["b.jpg"])
        assertEquals("c_007.jpg", map["c.jpg"])
    }

    @Test
    fun `hidden files keep the leading dot as stem`() {
        val plan = BatchRenamer.plan(
            listOf(".gitignore"),
            Options(prefix = "x_"),
        )
        // ".gitignore" → stem ".gitignore" + prefix → "x_.gitignore"
        assertEquals("x_.gitignore", namesOf(plan)[".gitignore"])
    }

    @Test
    fun `duplicate targets are reported as errors and left unchanged`() {
        val plan = BatchRenamer.plan(
            listOf("a.txt", "b.txt"),
            Options(replace = "", find = "a"),
        )
        assertFalse(plan.errors.isEmpty())
        assertEquals("a.txt", namesOf(plan)["a.txt"])
    }

    @Test
    fun `invalid characters are rejected`() {
        val plan = BatchRenamer.plan(
            listOf("nota.txt"),
            Options(prefix = "ab/c"),
        )
        assertTrue(plan.errors.any { it.contains("caracteres no válidos") })
        assertEquals("nota.txt", namesOf(plan)["nota.txt"])
    }

    @Test
    fun `empty rules produce no changes`() {
        val plan = BatchRenamer.plan(listOf("a.txt", "b.png"), Options())
        assertEquals(0, plan.changes)
        assertTrue(plan.errors.isEmpty())
    }

    @Test
    fun `splitting handles edge case names`() {
        assertEquals("nota" to ".txt", BatchRenamer.split("nota.txt"))
        assertEquals(".gitignore" to "", BatchRenamer.split(".gitignore"))
        assertEquals(".hidden.txt" to "", BatchRenamer.split(".hidden.txt"))
        assertEquals("archive.tar" to ".gz", BatchRenamer.split("archive.tar.gz"))
        assertEquals("file." to "", BatchRenamer.split("file."))
        assertEquals("noext" to "", BatchRenamer.split("noext"))
    }
}