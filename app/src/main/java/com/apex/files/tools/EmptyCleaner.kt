package com.apex.files.tools

import com.apex.files.data.fs.FsRepository
import com.apex.files.data.fs.OpResult
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finds directories whose entire subtree sums to 0 bytes (empty dirs and
 * dirs containing only 0-byte files), then deletes them bottom-up.
 */
class EmptyCleaner(private val fs: FsRepository) {

    data class CleanerScan(
        val currentPath: String = "",
        val scanned: Long = 0,
        val found: Int = 0,
        val done: Boolean = false,
        val results: List<FileNode> = emptyList(),
    )

    fun scan(root: FileNode): Flow<CleanerScan> = flow {
        val candidates = ArrayList<FileNode>()
        suspend fun walk(dir: FileNode): Long {
            currentCoroutineContext().ensureActive()
            val children = fs.list(dir, showHidden = true, sort = SortOrder.NAME)
            var sum = 0L
            for (child in children) {
                if (child.uri != null) continue
                if (Paths.isExcluded(child.path)) continue
                if (!child.isDir) {
                    sum += child.size
                } else {
                    val sub = walk(child)
                    if (sub == 0L) candidates.add(child)
                    sum += sub
                }
            }
            emit(CleanerScan(currentPath = dir.path, scanned = 0, found = candidates.size))
            return sum
        }
        walk(root)
        emit(
            CleanerScan(
                currentPath = "",
                scanned = 0,
                found = candidates.size,
                done = true,
                results = candidates,
            )
        )
    }.flowOn(Dispatchers.IO)

    /** Deletes all empty directories found in the scan. */
    suspend fun deleteEmptyDirs(nodes: List<FileNode>): OpResult = withContext(Dispatchers.IO) {
        val acc = com.apex.files.data.fs.OpAccumulator()
        for (node in nodes) {
            currentCoroutineContext().ensureActive()
            if (node.uri != null) continue
            val file = File(node.path)
            if (file.isDirectory) {
                deleteRecursive(file, acc)
            }
        }
        acc.result()
    }

    private fun deleteRecursive(file: File, acc: com.apex.files.data.fs.OpAccumulator) {
        if (file.isDirectory && !Paths.isSymlink(file)) {
            for (c in file.listFiles() ?: return) deleteRecursive(c, acc)
            if (!file.delete() && acc.files > 0) acc.error("No se pudo eliminar ${file.name}")
        } else {
            if (file.delete()) acc.addFiles() else acc.error("No se pudo eliminar ${file.name}")
        }
    }

    /** Finds directories containing only hidden files (starting with ".") */
    fun scanHiddenOnly(root: FileNode): Flow<CleanerScan> = flow {
        val candidates = ArrayList<FileNode>()
        suspend fun walk(dir: FileNode): Boolean {
            currentCoroutineContext().ensureActive()
            val children = fs.list(dir, showHidden = true, sort = SortOrder.NAME)
            var hasVisible = false
            for (child in children) {
                if (child.uri != null) continue
                if (Paths.isExcluded(child.path)) continue
                if (child.name.startsWith(".")) continue
                if (child.isDir) {
                    val subHasVisible = walk(child)
                    if (!subHasVisible) candidates.add(child)
                    else hasVisible = true
                } else {
                    hasVisible = true
                }
            }
            emit(CleanerScan(currentPath = dir.path, scanned = 0, found = candidates.size))
            return hasVisible
        }
        walk(root)
        emit(
            CleanerScan(
                currentPath = "",
                scanned = 0,
                found = candidates.size,
                done = true,
                results = candidates,
            )
        )
    }.flowOn(Dispatchers.IO)
}