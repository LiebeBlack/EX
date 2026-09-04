package com.apex.files.data.fs

import java.io.File

/**
 * Guards that prevent recursive file operations from walking a folder into
 * its own subtree (which would duplicate data until the path limit).
 *
 * Enforced before copy/move when the source is a directory: the destination
 * must never be the source itself nor one of its descendants.
 */
object TransferGuard {

    /**
     * True when [dest] is [srcTree] itself or lives inside it (File-backed).
     * Symlinks are resolved through canonical paths when both sides are
     * regular directories; when a symlink is involved the answer is false
     * (we must not block legitimate copies of symlink targets).
     */
    fun isInsideOrSelf(dest: File, srcTree: File): Boolean {
        if (!dest.isAbsolute || !srcTree.isAbsolute) return false
        val destPath = dest.absolutePath
        val srcPath = srcTree.absolutePath
        return sameOrDescendant(destPath, srcPath)
    }

    /**
     * True when [dest] is [srcTree] itself or lives inside it, compared by
     * plain absolute paths without touching the filesystem.
     */
    fun sameOrDescendant(dest: String, src: String): Boolean {
        // Both sides are normalized so trailing separators never matter
        // ("a/b/" vs "a/b" is the same node).
        val d = dest.trimEnd('/')
        val s = src.trimEnd('/')
        if (d == s) return true
        if (s.isEmpty()) return d.startsWith("/")
        return d.startsWith("$s/")
    }

    /** Same test for SAF document ids ("primary:Download/A" style). */
    fun safInsideOrSelf(destDocId: String?, srcDocId: String?): Boolean {
        if (destDocId.isNullOrBlank() || srcDocId.isNullOrBlank()) return false
        return sameOrDescendant(destDocId, srcDocId)
    }

    /** True when a move/copy would place [srcTree] inside [dest]. */
    fun wouldSelfMove(srcTree: File, dest: File): Boolean = isInsideOrSelf(dest, srcTree)
}

/**
 * Thrown by file operations whose preconditions are violated (e.g. copying
 * a folder into its own subtree). The message is user-facing (Spanish).
 */
class TransferException(message: String) : Exception(message)
