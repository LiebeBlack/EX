package com.apex.files

import com.apex.files.data.fs.Sorters
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.SortDirection
import com.apex.files.data.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class SortersTest {

    private fun file(name: String, size: Long = 0L, modified: Long = 0L) = FileNode(
        name = name,
        path = "/x/$name",
        isDir = false,
        size = size,
        lastModified = modified,
        extension = "",
        category = com.apex.files.data.model.Category.OTHER,
    )

    private fun dir(name: String) = FileNode.forDirectory(name, "/x/$name")

    @Test
    fun `directories always come first in both directions`() {
        val items = listOf(file("a.txt"), dir("z"), file("b.txt"), dir("a"))
        val asc = items.sortedWith(Sorters.comparator(SortOrder.NAME, SortDirection.ASC))
        assertEquals(listOf("a", "z", "a.txt", "b.txt"), asc.map { it.name })
        val desc = items.sortedWith(Sorters.comparator(SortOrder.NAME, SortDirection.DESC))
        // dirs first, then names descending
        assertEquals(listOf("z", "a", "b.txt", "a.txt"), desc.map { it.name })
    }

    @Test
    fun `name ascending and descending`() {
        val files = listOf(file("beta"), file("Alpha"), file("gamma"))
        assertEquals(
            listOf("Alpha", "beta", "gamma"),
            files.sortedWith(Sorters.comparator(SortOrder.NAME, SortDirection.ASC)).map { it.name },
        )
        assertEquals(
            listOf("gamma", "beta", "Alpha"),
            files.sortedWith(Sorters.comparator(SortOrder.NAME, SortDirection.DESC)).map { it.name },
        )
    }

    @Test
    fun `size sorts big first ascending stays big first only when ascending requested`() {
        val files = listOf(file("a", size = 10), file("b", size = 100), file("c", size = 1))
        // Existing product convention: ascending means small→large…
        assertEquals(
            listOf("c", "a", "b"),
            files.sortedWith(Sorters.comparator(SortOrder.SIZE, SortDirection.ASC)).map { it.name },
        )
        assertEquals(
            listOf("b", "a", "c"),
            files.sortedWith(Sorters.comparator(SortOrder.SIZE, SortDirection.DESC)).map { it.name },
        )
    }

    @Test
    fun `date ascending and descending`() {
        val files = listOf(file("old", modified = 1_000), file("new", modified = 5_000), file("mid", modified = 3_000))
        assertEquals(
            listOf("old", "mid", "new"),
            files.sortedWith(Sorters.comparator(SortOrder.DATE, SortDirection.ASC)).map { it.name },
        )
        assertEquals(
            listOf("new", "mid", "old"),
            files.sortedWith(Sorters.comparator(SortOrder.DATE, SortDirection.DESC)).map { it.name },
        )
    }
}
