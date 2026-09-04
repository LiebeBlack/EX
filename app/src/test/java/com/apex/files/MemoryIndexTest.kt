package com.apex.files

import com.apex.files.data.fs.CategoryEngine
import com.apex.files.data.fs.MemoryIndex
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryIndexTest {

    private fun file(path: String, size: Long, category: Category = Category.OTHER) =
        FileNode(
            name = path.substringAfterLast('/'),
            path = path,
            isDir = false,
            size = size,
            lastModified = 0L,
            extension = CategoryEngine.extensionOf(path.substringAfterLast('/')),
            category = category,
        )

    @Test
    fun `largestFiles returns top n sorted`() {
        val index = MemoryIndex()
        index.put(file("/x/a", 100))
        index.put(file("/x/b", 10_000))
        index.put(file("/x/c", 500))
        index.put(file("/x/d", 2_000_000))
        val top = index.largestFiles(2)
        assertEquals(listOf("/x/d", "/x/b"), top.map { it.path })
        assertEquals(listOf("/x/b"), index.largestFiles(1).map { it.path })
        assertTrue(index.largestFiles(0).isEmpty())
    }

    @Test
    fun `largestFiles respects minBytes and skips dirs`() {
        val index = MemoryIndex()
        index.put(file("/x/a", 100))
        index.put(file("/x/big", 10_000))
        index.put(FileNode.forDirectory("carpeta", "/x/carpeta"))
        val top = index.largestFiles(5, minBytes = 1_000)
        assertEquals(listOf("/x/big"), top.map { it.path })
    }

    @Test
    fun `search by category and extension`() {
        val index = MemoryIndex()
        index.put(file("/x/foto.jpg", 10, Category.IMAGE))
        index.put(file("/x/doc.pdf", 20, Category.DOCUMENT))
        index.put(file("/x/nota.txt", 30, Category.DOCUMENT))
        assertEquals(2, index.search("", category = Category.DOCUMENT).size)
        assertEquals(1, index.search("", extFilter = "*.pdf").size)
        assertEquals(0, index.search("", extFilter = "*.apk").size)
    }

    @Test
    fun `countByCategory counts only plain files`() {
        val index = MemoryIndex()
        index.put(file("/x/a.png", 1, Category.IMAGE))
        index.put(file("/x/b.png", 2, Category.IMAGE))
        index.put(file("/x/c.mp4", 3, Category.VIDEO))
        index.put(FileNode.forDirectory("/x/carpeta", "/x/carpeta"))
        assertEquals(2, index.countByCategory()[Category.IMAGE])
        assertEquals(1, index.countByCategory()[Category.VIDEO])
    }
}
