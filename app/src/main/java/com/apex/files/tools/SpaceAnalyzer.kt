package com.apex.files.tools

import com.apex.files.data.fs.FsRepository
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Builds a bounded size tree for the treemap analyzer. Depth and node count
 * are capped; beyond the cap, directories are summed without descending.
 * Each directory aggregates its children to the top 12 + "Otros".
 */
class SpaceAnalyzer(private val fs: FsRepository) {

    data class SpaceNode(
        val name: String,
        val size: Long,
        val category: Category?,
        val children: List<SpaceNode> = emptyList(),
        val isFile: Boolean = true,
    )

    data class SpaceScan(
        val currentPath: String = "",
        val done: Boolean = false,
        val root: SpaceNode? = null,
    )

    companion object {
        const val MAX_DEPTH = 6
        const val MAX_NODES = 8_000
        const val TOP_CHILDREN = 12
    }

    fun analyze(root: FileNode): Flow<SpaceScan> = flow {
        val nodes = arrayOf(0)

        suspend fun build(node: FileNode, depth: Int): SpaceNode {
            currentCoroutineContext().ensureActive()
            emit(SpaceScan(currentPath = node.path))

            if (node.uri != null) {
                return SpaceNode(node.name, fs.sizeOf(node), Category.OTHER, isFile = !node.isDir)
            }

            val file = java.io.File(node.path)
            if (file.isFile) {
                return SpaceNode(node.name, file.length().coerceAtLeast(0), node.category, isFile = true)
            }
            if (!file.isDirectory || Paths.isSymlink(file)) {
                return SpaceNode(node.name, 0L, null, isFile = false)
            }

            val children = file.listFiles() ?: return SpaceNode(node.name, 0L, null, isFile = false)
            val childNodes = ArrayList<SpaceNode>()
            var sum = 0L

            for (child in children) {
                if (Paths.isExcluded(child)) continue
                if (child.isDirectory) {
                    if (depth >= MAX_DEPTH || nodes[0] >= MAX_NODES) {
                        // Leaf mode: just sum the subtree size without descending.
                        sum += fs.sizeOf(FileNode.forDirectory(child.name, child.absolutePath, child.lastModified()))
                    } else {
                        nodes[0]++
                        val sub = build(
                            FileNode.forDirectory(child.name, child.absolutePath, child.lastModified()),
                            depth + 1,
                        )
                        childNodes.add(sub)
                        sum += sub.size
                    }
                } else {
                    nodes[0]++
                    val size = child.length().coerceAtLeast(0)
                    childNodes.add(
                        SpaceNode(
                            child.name,
                            size,
                            com.apex.files.data.fs.CategoryEngine.classify(child.name),
                            isFile = true,
                        )
                    )
                    sum += size
                }
            }

            val aggregated = aggregate(childNodes, sum)
            return SpaceNode(node.name, sum, null, aggregated, isFile = false)
        }

        val rootNode = build(root, 0)
        emit(SpaceScan(currentPath = "", done = true, root = rootNode))
    }.flowOn(Dispatchers.IO)

    /** Keeps the top [TOP_CHILDREN] children by size, folding the rest into "Otros". */
    private fun aggregate(children: List<SpaceNode>, total: Long): List<SpaceNode> {
        if (children.size <= TOP_CHILDREN) return children
        val sorted = children.sortedByDescending { it.size }
        val kept = sorted.take(TOP_CHILDREN)
        val restSize = sorted.drop(TOP_CHILDREN).sumOf { it.size }
        if (restSize <= 0L) return kept
        return kept + SpaceNode("Otros", restSize, Category.OTHER, isFile = false)
    }
}