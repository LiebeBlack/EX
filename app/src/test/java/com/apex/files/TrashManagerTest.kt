package com.apex.files

import com.apex.files.data.fs.TrashManager
import com.apex.files.data.model.FileNode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrashManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun manager(): TrashManager {
        val root = tmp.root
        return TrashManager(
            rootForPath = { root },
            allRoots = { listOf(root) },
        )
    }

    private fun writeFile(file: File, content: String = "hola") {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `trash moves the file into the hidden folder`() = runBlocking {
        val file = File(tmp.root, "photo.jpg")
        writeFile(file)
        val node = FileNode("photo.jpg", file.absolutePath, false, 4L, 0L)

        val result = manager().trash(node)

        assertTrue(result.ok)
        assertFalse("original should be gone", file.exists())
        val trashDir = File(tmp.root, ".apex_trash")
        assertTrue(trashDir.exists())
        val moved = trashDir.walkTopDown().filter { it.isFile && it.name == "photo.jpg" }.firstOrNull()
        assertTrue("file should live in the trash", moved != null && moved.exists())
    }

    @Test
    fun `list returns the trashed item with original path`() = runBlocking {
        val file = File(tmp.root, "notes.txt")
        writeFile(file)
        val node = FileNode("notes.txt", file.absolutePath, false, 4L, 0L)
        val m = manager()
        m.trash(node)

        val entries = m.list()

        assertEquals(1, entries.size)
        assertEquals("notes.txt", entries[0].name)
        assertEquals(file.absolutePath, entries[0].originalPath)
        assertFalse(entries[0].isDir)
        assertTrue(entries[0].trashedAt > 0L)
    }

    @Test
    fun `restore puts the file back at its original location`() = runBlocking {
        val file = File(tmp.root, "report.pdf")
        writeFile(file, "report.pdf")
        val node = FileNode("report.pdf", file.absolutePath, false, 5L, 0L)
        val m = manager()
        m.trash(node)
        val entry = m.list().first()

        val result = m.restore(entry)

        assertTrue(result.ok)
        assertTrue("original should be back", file.exists())
        assertEquals("report.pdf", file.readText())
        assertTrue("trash should be empty", m.list().isEmpty())
    }

    @Test
    fun `restore keeps both when the original name is taken`() = runBlocking {
        val file = File(tmp.root, "docs.txt")
        writeFile(file, "nuevo")
        val node = FileNode("docs.txt", file.absolutePath, false, 5L, 0L)
        val m = manager()
        m.trash(node)
        // Occupied the original location with a different file.
        writeFile(file, "ocupado")
        val entry = m.list().first()

        val result = m.restore(entry)

        assertTrue(result.ok)
        assertTrue("occupied file is untouched", file.readText() == "ocupado")
        assertTrue(m.list().isEmpty())
        // The restored copy exists somewhere under the same parent.
        val restored = tmp.root.listFiles { f -> f.isFile && f.name.startsWith("docs") }
        assertEquals(2, restored?.size)
    }

    @Test
    fun `empty removes everything permanently`() = runBlocking {
        val m = manager()
        val a = File(tmp.root, "a.txt")
        val b = File(tmp.root, "sub/b.txt")
        writeFile(a)
        writeFile(b)
        m.trash(FileNode("a.txt", a.absolutePath, false, 0L, 0L))
        m.trash(FileNode("b.txt", b.absolutePath, false, 0L, 0L))
        assertEquals(2, m.list().size)

        val result = m.empty(tmp.root)

        assertTrue(result.ok)
        assertTrue(m.list().isEmpty())
        assertTrue("trash dir should be gone", !File(tmp.root, ".apex_trash").exists())
    }

    @Test
    fun `deleting a missing node is a no-op that still succeeds`() = runBlocking {
        val result = manager().trash(FileNode("ghost.txt", File(tmp.root, "ghost.txt").absolutePath, false, 0L, 0L))
        assertTrue(result.ok)
        assertEquals(0, result.filesDone)
    }
}