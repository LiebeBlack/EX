package com.apex.files

import androidx.compose.runtime.mutableStateListOf
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location

/** All navigation targets. [serial] uniquely identifies each push instance. */
sealed class Screen {
    open val serial: Int = 0

    data object Home : Screen()
    data class Explorer(val location: Location, override val serial: Int = 0) : Screen()
    data class Search(override val serial: Int = 0) : Screen()
    data class Category(val category: com.apex.files.data.model.Category, override val serial: Int = 0) : Screen()
    data object Drives : Screen()
    data object Settings : Screen()
    data object Cleaner : Screen()
    data object Duplicates : Screen()
    data object Apk : Screen()
    data object Stats : Screen()
    data class SpaceAnalyzer(val location: Location, override val serial: Int = 0) : Screen()
    data object Benchmark : Screen()
    /** Full-screen image gallery: [nodes] are the neighboring images of the
     *  opened file (at least the file itself) and [index] the position to
     *  start from. Swiping left/right moves between them. */
    data class ImageViewer(val nodes: List<FileNode>, val index: Int = 0, override val serial: Int = 0) : Screen()
    data class TextViewer(val node: FileNode, override val serial: Int = 0) : Screen()
    data class PdfViewer(val node: FileNode, override val serial: Int = 0) : Screen()
    data class ArchiveViewer(val node: FileNode, override val serial: Int = 0) : Screen()
    /** SQLite database analyzer (read-only). */
    data class SqliteViewer(val node: FileNode, override val serial: Int = 0) : Screen()
    /** System log console (logcat). READ_LOGS is only granted on debug
     *  builds / via ADB, so it may show a permission notice on release. */
    data object Logcat : Screen()
    /** In-app audio player: [nodes] are the neighboring audio tracks and
     *  [index] the track to start from (next/prev move through the list). */
    data class AudioPlayer(val nodes: List<FileNode>, val index: Int = 0, override val serial: Int = 0) : Screen()
    data object About : Screen()
    /** Per-volume recycle bin with restore / permanent delete / empty. */
    data object Trash : Screen()
    /** Batch rename: [nodes] are the selected files to transform. */
    data class BatchRename(val nodes: List<FileNode>, override val serial: Int = 0) : Screen()
    /** Built-in hexadecimal viewer for any file. */
    data class HexViewer(val node: FileNode, override val serial: Int = 0) : Screen()
}

/**
 * Minimal back-stack navigator (no navigation library). Each push gets a
 * fresh serial so per-screen ViewModels never collide.
 */
class Navigator(initial: Screen = Screen.Home) {

    private var counter = 0
    val stack = mutableStateListOf(initial)

    val current: Screen get() = stack.last()

    fun push(screen: Screen): Screen {
        counter++
        val s = withSerial(screen, counter)
        stack.add(s)
        return s
    }

    fun replace(screen: Screen) {
        counter++
        stack[stack.lastIndex] = withSerial(screen, counter)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun popToRoot() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    private fun withSerial(screen: Screen, n: Int): Screen = when (screen) {
        is Screen.Home -> screen
        is Screen.Explorer -> screen.copy(serial = n)
        is Screen.Search -> screen.copy(serial = n)
        is Screen.Category -> screen.copy(serial = n)
        is Screen.Drives -> screen
        is Screen.Settings -> screen
        is Screen.Cleaner -> screen
        is Screen.Duplicates -> screen
        is Screen.Apk -> screen
        is Screen.Stats -> screen
        is Screen.SpaceAnalyzer -> screen.copy(serial = n)
        is Screen.Benchmark -> screen
        is Screen.ImageViewer -> screen.copy(serial = n)
        is Screen.TextViewer -> screen.copy(serial = n)
        is Screen.PdfViewer -> screen.copy(serial = n)
        is Screen.ArchiveViewer -> screen.copy(serial = n)
        is Screen.AudioPlayer -> screen.copy(serial = n)
        is Screen.SqliteViewer -> screen.copy(serial = n)
        is Screen.Logcat -> screen
        is Screen.About -> screen
        is Screen.Trash -> screen
        is Screen.BatchRename -> screen.copy(serial = n)
        is Screen.HexViewer -> screen.copy(serial = n)
    }
}