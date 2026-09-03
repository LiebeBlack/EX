package com.apex.files.ui.screens.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Native PDF viewer engine (android.graphics.pdf.PdfRenderer, no libraries).
 * Pages are rendered lazily at display density into a small LRU cache.
 */
class PdfViewerViewModel(
    private val container: AppContainer,
    val node: FileNode,
) : ViewModel() {

    data class UiState(
        val pageCount: Int = 0,
        val loading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var renderer: PdfRenderer? = null
    private val mutex = Mutex()

    private val cache = object : LinkedHashMap<Int, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean = size > 5
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = container.fs.fileForReading(node)
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(pfd)
                renderer = r
                _state.update { it.copy(pageCount = r.pageCount, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Error", loading = false) }
            }
        }
    }

    /** Renders page [index] at ~[widthPx] wide, cached in a 5-page LRU. */
    suspend fun renderPage(index: Int, widthPx: Int, density: Float): Bitmap? = mutex.withLock {
        cache[index]?.let { return@withLock it }
        val r = renderer ?: return@withLock null
        if (index < 0 || index >= r.pageCount) return@withLock null
        val page = r.openPage(index)
        try {
            val scale = (widthPx.toFloat() / page.width.coerceAtLeast(1)) * density.coerceIn(1f, 2.5f)
            val w = (page.width * scale).toInt().coerceIn(1, 2048)
            val h = (page.height * scale).toInt().coerceIn(1, 4096)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            cache[index] = bitmap
            bitmap
        } finally {
            page.close()
        }
    }

    override fun onCleared() {
        renderer?.close()
        renderer = null
        cache.values.forEach { it.recycle() }
        cache.clear()
    }
}