package com.apex.files.data.fs

import android.os.Environment
import java.io.File
import java.nio.file.Files

/** Filesystem roots and the exclusion policy for recursive scans. */
object Paths {

    /** Directories that recursive tools and the indexer never touch. */
    private val EXCLUDED_TOKENS = listOf(
        "/Android/data/",
        "/Android/obb/",
        "/LOST.DIR/",
    )

    fun isExcluded(path: String): Boolean =
        EXCLUDED_TOKENS.any { path.contains(it, ignoreCase = false) && path.length > it.length }

    fun isExcluded(file: File): Boolean =
        isExcluded(file.absolutePath) || Files.isSymbolicLink(file.toPath())

    fun isSymlink(file: File): Boolean = Files.isSymbolicLink(file.toPath())

    fun internalRoot(): File = Environment.getExternalStorageDirectory()

    /** Removable volumes mounted under /storage (SD cards, USB mass storage). */
    fun removableRoots(): List<File> {
        val storage = File("/storage")
        if (!storage.exists() || !storage.isDirectory) return emptyList()
        val internal = internalRoot().canonicalFile
        return storage.listFiles { f ->
            f.isDirectory &&
                f.canonicalFile != internal &&
                (f.canRead() || Environment.isExternalStorageRemovable(f))
        }?.toList() ?: emptyList()
    }
}