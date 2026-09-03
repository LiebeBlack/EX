package com.apex.files.ui

import android.content.Context
import android.content.Intent
import com.apex.files.Navigator
import com.apex.files.Screen
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.FileKinds
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode

/**
 * Single file-opening policy shared by Explorer, Search and Category screens.
 *
 * - images → internal ImageViewer
 * - text-like → internal TextViewer
 * - pdf → internal PdfViewer
 * - archives → internal ArchiveViewer
 * - apks → external installer
 * - everything else (audio, video, documents, …) → external apps via
 *   ACTION_VIEW with the correct MIME and a read-URI grant
 *
 * Before this existed each screen routed unknown types (audio/video/pdf/apk)
 * into the *text* viewer, which is why media "refused to play".
 */
object NodeOpener {

    fun open(
        node: FileNode,
        container: AppContainer,
        navigator: Navigator,
        context: Context,
        onUnavailable: (String) -> Unit = {},
        imageContext: List<FileNode> = emptyList(),
    ) {
        // Every open lands in the Home "Recientes" list (files only).
        if (!node.isDir) container.recents.record(node)
        when {
            node.category == Category.IMAGE -> {
                // Gallery: pass the surrounding images so the viewer can
                // swipe left/right; without context the file opens alone.
                val images = if (imageContext.isEmpty()) listOf(node)
                else imageContext.filter { it.category == Category.IMAGE && !it.isDir }
                val index = images.indexOfFirst { it.path == node.path }.coerceAtLeast(0)
                navigator.push(Screen.ImageViewer(images, index))
            }
            FileKinds.isText(node) -> navigator.push(Screen.TextViewer(node))
            node.extension == "pdf" -> navigator.push(Screen.PdfViewer(node))
            container.archive.isSupported(node) -> navigator.push(Screen.ArchiveViewer(node))
            node.category == Category.APK -> launchExternal(
                node = node,
                container = container,
                context = context,
                mime = "application/vnd.android.package-archive",
                error = "No hay aplicación para instalar",
                onUnavailable = onUnavailable,
            )
            else -> launchExternal(
                node = node,
                container = container,
                context = context,
                mime = FileKinds.mimeOf(node),
                error = "No hay aplicación para este tipo de archivo",
                onUnavailable = onUnavailable,
            )
        }
    }

    private fun launchExternal(
        node: FileNode,
        container: AppContainer,
        context: Context,
        mime: String,
        error: String,
        onUnavailable: (String) -> Unit,
    ) {
        val uri = container.fs.shareUri(node)
        if (uri == null) {
            onUnavailable(error)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(intent, "Abrir con")) }
            .onFailure { onUnavailable(error) }
    }
}
