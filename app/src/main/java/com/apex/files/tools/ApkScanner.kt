package com.apex.files.tools

import android.content.Context
import android.content.pm.PackageManager
import com.apex.files.data.fs.FsRepository
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Scans for .apk installers and flags those whose package is already
 * installed (redundant installers) vs. not installed.
 */
class ApkScanner(private val context: Context, private val fs: FsRepository) {

    data class ApkInfo(
        val node: FileNode,
        val packageName: String?,
        /** null = cannot determine (SAF-backed or unreadable archive). */
        val installed: Boolean?,
    )

    data class ApkScan(
        val currentPath: String = "",
        val scanned: Int = 0,
        val done: Boolean = false,
        val apks: List<ApkInfo> = emptyList(),
    )

    fun scan(root: FileNode): Flow<ApkScan> = flow {
        val pm = context.packageManager
        val installedPackages = runCatching {
            pm.getInstalledApplications(0).map { it.packageName }.toSet()
        }.getOrDefault(emptySet())

        val results = ArrayList<ApkInfo>()

        suspend fun walk(dir: FileNode) {
            val children = fs.list(dir, showHidden = true, sort = SortOrder.NAME)
            for (child in children) {
                if (Paths.isExcluded(child.path)) continue
                if (child.isDir) {
                    walk(child)
                } else if (child.category == Category.APK) {
                    val info = inspect(child, pm, installedPackages)
                    results.add(info)
                    emit(ApkScan(currentPath = child.path, scanned = results.size))
                }
            }
        }
        walk(root)

        val sorted = results.sortedByDescending { it.node.size }
        emit(ApkScan(scanned = sorted.size, done = true, apks = sorted))
    }.flowOn(Dispatchers.IO)

    @Suppress("DEPRECATION")
    private fun inspect(
        node: FileNode,
        pm: PackageManager,
        installedPackages: Set<String>,
    ): ApkInfo {
        if (node.uri != null) {
            return ApkInfo(node, null, null)
        }
        val file = java.io.File(node.path)
        val packageName = runCatching {
            pm.getPackageArchiveInfo(file.absolutePath, 0)?.packageName
        }.getOrNull()
        return ApkInfo(
            node = node,
            packageName = packageName,
            installed = packageName?.let { it in installedPackages },
        )
    }
}