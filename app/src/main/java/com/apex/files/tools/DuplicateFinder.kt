package com.apex.files.tools

import com.apex.files.core.HashAlgorithm
import com.apex.files.core.HashUtil
import com.apex.files.data.fs.FsRepository
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Two-phase duplicate detection:
 *  Phase 1 — group files by exact byte size (skip 0-byte files).
 *  Phase 2 — SHA-256 hash ONLY the groups that matched by size.
 */
class DuplicateFinder(private val fs: FsRepository) {

    data class DupGroup(
        val size: Long,
        val hash: String,
        val files: List<FileNode>,
    ) {
        val reclaimable: Long get() = size * (files.size - 1)
    }

    data class DupScan(
        val currentPath: String = "",
        val hashed: Int = 0,
        val totalCandidates: Int = 0,
        val done: Boolean = false,
        val groups: List<DupGroup> = emptyList(),
    )

    fun find(root: FileNode): Flow<DupScan> = flow {
        // ---- Phase 1: group by exact size ----
        val bySize = HashMap<Long, MutableList<FileNode>>()
        suspend fun walk(dir: FileNode) {
            val children = fs.list(dir, showHidden = true, sort = SortOrder.NAME)
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (Paths.isExcluded(child.path)) continue
                if (child.isDir) {
                    walk(child)
                } else if (child.size > 0L) {
                    bySize.getOrPut(child.size) { ArrayList() }.add(child)
                }
            }
        }
        walk(root)

        val candidates = bySize.values.filter { it.size >= 2 }
        val total = candidates.sumOf { it.size }
        var hashed = 0

        // ---- Phase 2: SHA-256 only within size-matched groups ----
        val groups = ArrayList<DupGroup>()
        for (group in candidates) {
            currentCoroutineContext().ensureActive()
            val byHash = HashMap<String, MutableList<FileNode>>()
            for (file in group) {
                val stream = fs.openInputStream(file)
                if (stream != null) {
                    try {
                        val hash = HashUtil.hash(stream, HashAlgorithm.SHA256) {
                            currentCoroutineContext().ensureActive()
                            true
                        }
                        byHash.getOrPut(hash) { ArrayList() }.add(file)
                    } finally {
                        stream.close()
                    }
                }
                hashed++
                emit(DupScan(currentPath = file.path, hashed = hashed, totalCandidates = total))
            }
            for ((hash, files) in byHash) {
                if (files.size >= 2) {
                    groups.add(DupGroup(size = group.first().size, hash = hash, files = files.sortedBy { it.path }))
                }
            }
        }
        emit(DupScan(done = true, hashed = hashed, totalCandidates = total, groups = groups))
    }.flowOn(Dispatchers.IO)
}

/** Pure phase-2 grouping, unit-testable without Android. */
object DuplicateAlgorithm {

    /** Hashes candidates by size group and returns groups with >= 2 files. */
    fun groupByHash(
        bySize: Map<Long, List<FileNode>>,
        hasher: (FileNode) -> String,
    ): List<DuplicateFinder.DupGroup> {
        val groups = ArrayList<DuplicateFinder.DupGroup>()
        for ((size, files) in bySize) {
            if (files.size < 2) continue
            val byHash = HashMap<String, MutableList<FileNode>>()
            for (file in files) {
                byHash.getOrPut(hasher(file)) { ArrayList() }.add(file)
            }
            for ((hash, matches) in byHash) {
                if (matches.size >= 2) {
                    groups.add(DuplicateFinder.DupGroup(size, hash, matches.sortedBy { it.path }))
                }
            }
        }
        return groups
    }

    /**
     * Smart default for "select duplicates": keep the most recently
     * modified copy per group and return the rest for deletion. Ties are
     * broken keeping the lexicographically smallest path.
     */
    fun filesToDelete(files: List<FileNode>): List<FileNode> {
        if (files.size < 2) return emptyList()
        val keeper = files.maxWithOrNull(
            compareBy<FileNode> { it.lastModified }.thenByDescending { it.path }
        ) ?: return files.drop(1)
        return files.filterNot { it.path == keeper.path }
    }
}