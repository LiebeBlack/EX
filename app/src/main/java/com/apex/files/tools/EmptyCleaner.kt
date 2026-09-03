package com.apex.files.tools

import com.apex.files.data.fs.FsRepository
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

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
}