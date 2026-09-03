package com.apex.files.data.model

import android.net.Uri

/**
 * Immutable description of a file or directory, produced by both the
 * [com.apex.files.data.fs.FsRepository] (java.io.File) and the
 * [com.apex.files.data.fs.SafRepository] (DocumentFile) backends.
 *
 * [path] is the display path used for breadcrumbs and identity. For SAF
 * nodes it is a virtual path under the tree root. [uri] is non-null for
 * SAF / MediaStore backed nodes and drives thumbnails and share intents.
 */
data class FileNode(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String = "",
    val category: Category = Category.OTHER,
    val uri: Uri? = null,
    val isRoot: Boolean = false,
) {
    val isHidden: Boolean
        get() = name.startsWith(".")

    companion object {
        fun forDirectory(
            name: String,
            path: String,
            lastModified: Long = 0L,
            uri: Uri? = null,
            isRoot: Boolean = false,
        ) = FileNode(
            name = name,
            path = path,
            isDir = true,
            size = 0L,
            lastModified = lastModified,
            extension = "",
            category = Category.DIRECTORY,
            uri = uri,
            isRoot = isRoot,
        )
    }
}