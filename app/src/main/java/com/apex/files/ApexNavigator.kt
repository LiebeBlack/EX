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
    data class Category(val category: Category, override val serial: Int = 0) : Screen()
    data object Drives : Screen()
    data object Settings : Screen()
    data object Cleaner : Screen()
    data object Duplicates : Screen()
    data object Apk : Screen()
    data class SpaceAnalyzer(val location: Location, override val serial: Int = 0) : Screen()
    data object Benchmark : Screen()
    data class ImageViewer(val node: FileNode, override val serial: Int = 0) : Screen()
    data class TextViewer(val node: FileNode, override val serial: Int = 0) : Screen()
    data class PdfViewer(val node: FileNode, override val serial: Int = 0) : Screen()
    data class ArchiveViewer(val node: FileNode, override val serial: Int = 0) : Screen()
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
        is Screen.SpaceAnalyzer -> screen.copy(serial = n)
        is Screen.Benchmark -> screen
        is Screen.ImageViewer -> screen.copy(serial = n)
        is Screen.TextViewer -> screen.copy(serial = n)
        is Screen.PdfViewer -> screen.copy(serial = n)
        is Screen.ArchiveViewer -> screen.copy(serial = n)
    }
}