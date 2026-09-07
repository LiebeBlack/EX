package com.apex.files.tools

import com.apex.files.data.fs.CategoryEngine
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
        /** Real on-disk location of [this] node (empty for aggregated/dirs). */
        val path: String = "",
        /** File modification time when [path] is a real file. */
        val lastModified: Long = 0L,
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
                return SpaceNode(node.name, fs.sizeOf(node), Category.OTHER, isFile = !node.isDir, path = node.path)
            }

            val file = java.io.File(node.path)
            if (file.isFile) {
                return SpaceNode(
                    node.name,
                    file.length().coerceAtLeast(0),
                    node.category,
                    isFile = true,
                    path = node.path,
                    lastModified = node.lastModified,
                )
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
                            CategoryEngine.classify(child.name),
                            isFile = true,
                            path = child.absolutePath,
                            lastModified = child.lastModified(),
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

    /** Analyzes storage usage by category. */
    fun analyzeByCategory(root: FileNode): Flow<Map<Category, Long>> = flow {
        val categorySizes = mutableMapOf<Category, Long>()
        
        suspend fun walk(node: FileNode) {
            currentCoroutineContext().ensureActive()
            if (node.isDir) {
                val children = fs.list(node, showHidden = true, sort = SortOrder.NAME)
                for (child in children) {
                    walk(child)
                }
            } else {
                categorySizes[node.category] = (categorySizes[node.category] ?: 0L) + node.size
            }
        }
        
        walk(root)
        emit(categorySizes.toMap())
    }.flowOn(Dispatchers.IO)

    /** Finds the largest files in a directory tree. */
    fun findLargestFiles(root: FileNode, limit: Int = 100): Flow<List<SpaceNode>> = flow {
        val largestFiles = ArrayList<SpaceNode>()
        
        suspend fun walk(node: FileNode) {
            currentCoroutineContext().ensureActive()
            if (node.isDir) {
                val children = fs.list(node, showHidden = true, sort = SortOrder.NAME)
                for (child in children) {
                    walk(child)
                }
            } else {
                if (largestFiles.size < limit || node.size > largestFiles.last().size) {
                    largestFiles.add(
                        SpaceNode(
                            name = node.name,
                            size = node.size,
                            category = node.category,
                            isFile = true,
                            path = node.path,
                            lastModified = node.lastModified,
                        )
                    )
                    largestFiles.sortByDescending { it.size }
                    if (largestFiles.size > limit) {
                        largestFiles.removeAt(largestFiles.size - 1)
                    }
                }
            }
        }
        
        walk(root)
        emit(largestFiles)
    }.flowOn(Dispatchers.IO)

    /** Analyzes file age distribution. */
    fun analyzeFileAge(root: FileNode): Flow<Map<String, Int>> = flow {
        val ageDistribution = mutableMapOf(
            "Hoy" to 0,
            "Esta semana" to 0,
            "Este mes" to 0,
            "Este año" to 0,
            "Antiguo" to 0,
        )
        
        val now = System.currentTimeMillis()
        val day = 24L * 3600_000
        val week = 7L * day
        val month = 30L * day
        val year = 365L * day
        
        suspend fun walk(node: FileNode) {
            currentCoroutineContext().ensureActive()
            if (node.isDir) {
                val children = fs.list(node, showHidden = true, sort = SortOrder.NAME)
                for (child in children) {
                    walk(child)
                }
            } else {
                val age = now - node.lastModified
                when {
                    age < day -> ageDistribution["Hoy"] = (ageDistribution["Hoy"] ?: 0) + 1
                    age < week -> ageDistribution["Esta semana"] = (ageDistribution["Esta semana"] ?: 0) + 1
                    age < month -> ageDistribution["Este mes"] = (ageDistribution["Este mes"] ?: 0) + 1
                    age < year -> ageDistribution["Este año"] = (ageDistribution["Este año"] ?: 0) + 1
                    else -> ageDistribution["Antiguo"] = (ageDistribution["Antiguo"] ?: 0) + 1
                }
            }
        }
        
        walk(root)
        emit(ageDistribution.toMap())
    }.flowOn(Dispatchers.IO)
}