package com.apex.files

import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.tools.DuplicateAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateAlgorithmTest {

    private fun file(name: String, modified: Long) = FileNode(
        name = name,
        path = "/x/$name",
        isDir = false,
        size = 100L,
        lastModified = modified,
        extension = "txt",
        category = Category.DOCUMENT,
    )

    @Test
    fun `keeps the most recently modified copy`() {
        val files = listOf(
            file("v1.txt", modified = 100L),
            file("v2.txt", modified = 300L),
            file("v3.txt", modified = 200L),
        )
        val toDelete = DuplicateAlgorithm.filesToDelete(files).map { it.name }
        assertEquals(setOf("v1.txt", "v3.txt"), toDelete.toSet())
        assertTrue("v2.txt" !in toDelete)
    }

    @Test
    fun `tie on modified keeps smallest path`() {
        val files = listOf(
            file("b.txt", modified = 100L),
            file("a.txt", modified = 100L),
            file("c.txt", modified = 100L),
        )
        val kept = files.map { it.path }.toSet() - DuplicateAlgorithm.filesToDelete(files).map { it.path }.toSet()
        assertEquals(setOf("/x/a.txt"), kept)
    }

    @Test
    fun `single file never selects anything`() {
        assertTrue(DuplicateAlgorithm.filesToDelete(listOf(file("solo.txt", modified = 1L))).isEmpty())
        assertTrue(DuplicateAlgorithm.filesToDelete(emptyList()).isEmpty())
    }
}
