package com.apex.files

import com.apex.files.data.model.Category
import com.apex.files.tools.SpaceAnalyzer
import com.apex.files.tools.TreemapLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TreemapLayoutTest {

    private fun node(name: String, size: Long) = SpaceAnalyzer.SpaceNode(
        name = name,
        size = size,
        category = Category.OTHER,
        isFile = true,
    )

    @Test
    fun `rectangles cover the whole area without overlaps`() {
        val nodes = listOf(
            node("a", 4000), node("b", 3000), node("c", 2000), node("d", 1000),
            node("e", 500), node("f", 400), node("g", 300), node("h", 100),
        )
        val w = 1000f
        val h = 800f
        val rects = TreemapLayout.layout(nodes, w, h)

        assertEquals(8, rects.size)

        // Total area preserved.
        val totalArea = rects.sumOf { it.w * it.h }
        assertEquals(w * h, totalArea, 1.0)

        // Every rect inside the canvas.
        for (r in rects) {
            assertTrue(r.x >= -0.01f && r.y >= -0.01f)
            assertTrue(r.x + r.w <= w + 0.01f && r.y + r.h <= h + 0.01f)
            assertTrue(r.w > 0f && r.h > 0f)
        }

        // No pair overlaps.
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                val a = rects[i]
                val b = rects[j]
                val overlapX = a.x < b.x + b.w && b.x < a.x + a.w
                val overlapY = a.y < b.y + b.h && b.y < a.y + a.h
                assertTrue("solape entre $i y $j", !(overlapX && overlapY))
            }
        }
    }

    @Test
    fun `areas are proportional to sizes`() {
        val nodes = listOf(node("big", 750), node("small", 250))
        val rects = TreemapLayout.layout(nodes, 400f, 400f)
        val big = rects.first { it.node.name == "big" }
        val small = rects.first { it.node.name == "small" }
        val ratio = (big.w * big.h) / (small.w * small.h)
        assertEquals(3.0, ratio, 0.2)
    }

    @Test
    fun `empty and zero-size inputs are safe`() {
        assertEquals(0, TreemapLayout.layout(emptyList(), 100f, 100f).size)
        assertEquals(0, TreemapLayout.layout(listOf(node("z", 0)), 100f, 100f).size)
        assertEquals(0, TreemapLayout.layout(listOf(node("z", 10)), 0f, 0f).size)
    }
}