package com.apex.files.tools

import android.content.Context
import android.content.pm.PackageManager
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.FileNode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Detects reclaimable junk on the internal volume, following the pattern of
 * [EmptyCleaner] (Flow-based scan, emits progress):
 *
 *  - **Orphan app data**: `/Android/data/<pkg>` and `/Android/obb/<pkg>`
 *    where the package is no longer installed (compared with PackageManager).
 *  - **Caches**: `/Android/data/<pkg>/cache` of installed apps.
 *  - **Temporales**: `*.tmp`, `*.part`, `*.crdownload`, `*.download` under
 *    the internal root (shallow, hidden/excluded dirs skipped).
 *
 * Safety: nothing outside the rules above is ever reported; volume roots,
 * `/system`, `/data`, `.apex_trash` and app-private dirs are never touched.
 * Deletion is always user-confirmed by the caller.
 */
class JunkAnalyzer(context: Context) {

    enum class JunkKind { ORPHAN_APP, CACHE, TEMP }

    data class JunkItem(
        val node: FileNode,
        val kind: JunkKind,
        val packageName: String? = null,
        val bytes: Long,
    ) {
        val path: String get() = node.path
        val name: String get() = node.name
    }

    data class ScanState(
        val currentPath: String = "",
        val found: List<JunkItem> = emptyList(),
        val done: Boolean = false,
    )

    private val internalRoot: File = Paths.internalRoot()
    private val packageManager: PackageManager = context.packageManager

    private val TEMP_EXTS = setOf("tmp", "part", "crdownload", "download", "temp")
    private val TEMP_MAX_DEPTH = 4

    fun scan(): Flow<ScanState> = flow {
        val found = ArrayList<JunkItem>()

        // Installed packages once per scan.
        val installed: Set<String> = runCatching {
            packageManager.getInstalledApplications(0).map { it.packageName }.toSet()
        }.getOrDefault(emptySet())

        // Orphan app dirs + caches under Android/data and Android/obb.
        scanAppDirs(File(internalRoot, "Android/data"), installed, found) { state -> emit(state) }
        scanAppDirs(File(internalRoot, "Android/obb"), installed, found) { state -> emit(state) }

        // Temp files (shallow walk).
        scanTemp(internalRoot, 0, found)

        emit(ScanState(found = found, done = true))
    }.flowOn(Dispatchers.IO)

    private suspend fun scanAppDirs(
        androidDir: File,
        installed: Set<String>,
        out: MutableList<JunkItem>,
        emit: suspend (ScanState) -> Unit,
    ) {
        if (!androidDir.isDirectory) return
        val children = androidDir.listFiles() ?: return
        for (child in children) {
            currentCoroutineContext().ensureActive()
            if (!child.isDirectory) continue
            val pkg = child.name
            emit(ScanState(currentPath = child.absolutePath, found = out.toList()))
            if (pkg !in installed) {
                // Orphan: the whole directory is reclaimable.
                val bytes = dirSize(child)
                out.add(
                    JunkItem(
                        node = FileNode.forDirectory(child.name, child.absolutePath, child.lastModified()),
                        kind = JunkKind.ORPHAN_APP,
                        packageName = pkg,
                        bytes = bytes,
                    )
                )
            } else if (androidDir.name == "data") {
                // Installed app: only its cache/ subtree is junk.
                val cache = File(child, "cache")
                if (cache.isDirectory) {
                    val bytes = dirSize(cache)
                    if (bytes > 0L) {
                        out.add(
                            JunkItem(
                                node = FileNode.forDirectory(cache.name, cache.absolutePath, cache.lastModified()),
                                kind = JunkKind.CACHE,
                                packageName = pkg,
                                bytes = bytes,
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun scanTemp(dir: File, depth: Int, out: MutableList<JunkItem>) {
        if (depth > TEMP_MAX_DEPTH) return
        currentCoroutineContext().ensureActive()
        val children = dir.listFiles() ?: return
        for (child in children) {
            currentCoroutineContext().ensureActive()
            if (Paths.isExcluded(child)) continue
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                scanTemp(child, depth + 1, out)
            } else {
                val ext = child.name.substringAfterLast('.', "").lowercase()
                if (ext in TEMP_EXTS) {
                    out.add(
                        JunkItem(
                            node = FileNode(
                                name = child.name,
                                path = child.absolutePath,
                                isDir = false,
                                size = child.length(),
                                lastModified = child.lastModified(),
                            ),
                            kind = JunkKind.TEMP,
                            bytes = child.length(),
                        )
                    )
                }
            }
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.isDirectory) return 0L
        var sum = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            val list = f.listFiles() ?: continue
            for (c in list) {
                if (c.isDirectory && !Paths.isSymlink(c)) {
                    stack.addLast(c)
                } else if (c.isFile) {
                    sum += c.length()
                }
            }
        }
        return sum
    }
}