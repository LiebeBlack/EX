package com.apex.files

import com.apex.files.data.model.FileNode
import com.apex.files.tools.DuplicateAlgorithm
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DuplicateFinderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun node(file: File) = FileNode(
        name = file.name,
        path = file.absolutePath,
        isDir = false,
        size = file.length(),
        lastModified = file.lastModified(),
    )

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `identical files are grouped by hash`() {
        val a1 = tmp.newFile("a1.txt").apply { writeText("contenido duplicado") }
        val a2 = tmp.newFile("a2.txt").apply { writeText("contenido duplicado") }
        val b = tmp.newFile("b.txt").apply { writeText("contenido unico") }

        val bySize = listOf(a1, a2, b).map(::node).groupBy { it.size }
        val groups = DuplicateAlgorithm.groupByHash(bySize) { sha256(File(it.path)) }

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals(2, group.files.size)
        assertEquals(a1.length(), group.size)
        assertEquals(a1.length(), group.reclaimable)
    }

    @Test
    fun `files with equal size but different content are not duplicates`() {
        val a1 = tmp.newFile("a1.txt").apply { writeText("AAAA") }
        val a2 = tmp.newFile("a2.txt").apply { writeText("BBBB") }

        val bySize = listOf(a1, a2).map(::node).groupBy { it.size }
        val groups = DuplicateAlgorithm.groupByHash(bySize) { sha256(File(it.path)) }

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `single candidates are never hashed into groups`() {
        val a = tmp.newFile("a.txt").apply { writeText("solo") }
        val groups = DuplicateAlgorithm.groupByHash(mapOf(a.length() to listOf(node(a)))) { "x" }
        assertTrue(groups.isEmpty())
    }
}