package com.apex.files.tools

import kotlin.math.max
import kotlin.math.min

data class TreemapRect(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val node: SpaceAnalyzer.SpaceNode,
) {
    fun contains(px: Float, py: Float): Boolean =
        px >= x && px <= x + w && py >= y && py <= y + h
}

/**
 * Squarified treemap layout (Bruls, Huizing, van Wijk) — pure JVM and
 * unit-testable. Produces proportional, near-square blocks with no overlap.
 */
object TreemapLayout {

    fun layout(
        nodes: List<SpaceAnalyzer.SpaceNode>,
        width: Float,
        height: Float,
    ): List<TreemapRect> {
        val items = nodes.filter { it.size > 0L }.sortedByDescending { it.size }
        if (items.isEmpty() || width <= 0f || height <= 0f) return emptyList()
        val total = items.sumOf { it.size }.toFloat()
        if (total <= 0f) return emptyList()

        // Constant per-node area scale (Bruls, Huizing, van Wijk).
        val scale = width * height / total
        val out = ArrayList<TreemapRect>(items.size)
        place(items, 0, 0f, 0f, width, height, scale, out)
        return out
    }

    private fun place(
        items: List<SpaceAnalyzer.SpaceNode>,
        start: Int,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        scale: Float,
        out: MutableList<TreemapRect>,
    ) {
        if (start >= items.size) return
        if (w <= 0.5f || h <= 0.5f) return

        val side = min(w, h)

        // Greedily grow a row while the worst aspect ratio improves.
        val row = ArrayList<SpaceAnalyzer.SpaceNode>()
        var rowSum = 0f
        var i = start

        fun worst(candidate: List<SpaceAnalyzer.SpaceNode>, sum: Float): Float {
            if (candidate.isEmpty() || sum <= 0f) return Float.MAX_VALUE
            var maxArea = 0f
            var minArea = Float.MAX_VALUE
            for (n in candidate) {
                val area = n.size * scale
                if (area > maxArea) maxArea = area
                if (area < minArea) minArea = area
            }
            val s2 = side * side
            val r1 = s2 * maxArea / (sum * sum)
            val r2 = (sum * sum) / (s2 * minArea)
            return max(r1, r2)
        }

        while (i < items.size) {
            val next = items[i]
            val candidateWorst = worst(row + next, rowSum + next.size)
            if (row.isEmpty() || candidateWorst <= worst(row, rowSum)) {
                row.add(next)
                rowSum += next.size
                i++
            } else {
                break
            }
        }

        // The strip spans the FULL height (vertical) or FULL width
        // (horizontal) of the remaining rectangle, so its thickness is the
        // row's area divided by that spanning dimension.
        val spanning = if (w <= h) h else w
        val thickness = rowSum * scale / spanning
        if (thickness <= 0f) return

        var offset = 0f
        if (w <= h) {
            // Vertical strip of width `thickness` on the left.
            for (n in row) {
                val rh = n.size * scale / thickness
                out.add(TreemapRect(x + offset, y, thickness, rh, n))
                offset += rh
            }
            place(items, i, x + thickness, y, w - thickness, h, scale, out)
        } else {
            // Horizontal strip of height `thickness` on top.
            for (n in row) {
                val rw = n.size * scale / thickness
                out.add(TreemapRect(x, y + offset, rw, thickness, n))
                offset += rw
            }
            place(items, i, x, y + thickness, w, h - thickness, scale, out)
        }
    }
}