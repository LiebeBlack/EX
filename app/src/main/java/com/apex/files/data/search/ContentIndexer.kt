package com.apex.files.data.search

import com.apex.files.data.fs.CategoryEngine
import com.apex.files.data.fs.Paths
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Incrementally fills [ContentIndex] by walking the volume roots (same
 * exclusion/hidden rules as the search index) and extracting content text:
 * plain-text/code files are read directly (cheap), images go through OCR,
 * PDFs through their native text layer with an OCR fallback.
 *
 * Only files whose lastModified changed since their indexed entry are
 * re-processed. The walk is bounded by [ContentIndex.MAX_ENTRIES] and each run
 * processes at most [DEFAULT_CHUNK] files so background indexing stays gentle
 * on battery; a full index builds over several app launches.
 */
class ContentIndexer(
    private val index: ContentIndex,
    private val ocr: OcrEngine,
    private val pdf: PdfTextExtractor,
) {

    companion object {
        const val DEFAULT_CHUNK = 500
        private const val MAX_DEPTH = 12

        /** Files whose raw text can be indexed directly (no OCR). */
        private val TEXT_EXTS = setOf(
            "txt", "md", "log", "json", "xml", "html", "htm", "csv", "ini", "cfg",
            "conf", "properties", "sql", "kt", "kts", "java", "py", "js", "ts",
            "tsx", "jsx", "c", "cpp", "cc", "h", "hpp", "cs", "go", "rs", "rb",
            "php", "swift", "sh", "bat", "ps1", "yml", "yaml", "toml", "gradle",
            "css", "scss", "vue", "svelte", "dart", "lua", "pl", "r",
        )

        private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")
    }

    data class ChunkResult(val processed: Int, val more: Boolean)

    /** Processes up to [maxFiles] pending files. Blocking: call on IO. */
    suspend fun indexChunk(
        maxFiles: Int = DEFAULT_CHUNK,
        showHidden: Boolean,
        ocrEnabled: Boolean,
    ): ChunkResult = withContext(Dispatchers.IO) {
        val candidates = collectCandidates(showHidden)
        var processed = 0
        for (f in candidates) {
            if (processed >= maxFiles) break
            currentCoroutineContext().ensureActive()
            extractAndPut(f, ocrEnabled)
            processed++
            if (processed % 100 == 0) index.save()
        }
        index.save()
        ChunkResult(processed, more = processed < candidates.size)
    }

/** Indexes everything pending in one walk (user-triggered "Reindexar"). */
    suspend fun indexAll(showHidden: Boolean, ocrEnabled: Boolean) = withContext(Dispatchers.IO) {
        val candidates = collectCandidates(showHidden)
        var processed = 0
        for (f in candidates) {
            currentCoroutineContext().ensureActive()
            extractAndPut(f, ocrEnabled)
            processed++
            if (processed % 100 == 0) index.save()
        }
        index.save()
    }

    /** Removes stale entries for paths that no longer exist (cheap prune). */
    fun pruneMissing(showHidden: Boolean) {
        val existing = java.util.HashSet<String>()
        for (root in roots()) collectPaths(root, showHidden, 0, existing)
        for (entry in index.entries()) {
            if (entry.path !in existing) index.remove(entry.path)
        }
        index.save()
    }

    private suspend fun extractAndPut(f: File, ocrEnabled: Boolean) {
        val text = extract(f, ocrEnabled)
        index.put(f.absolutePath, f.lastModified(), SpanishNormalizer.normalize(text))
    }

    private suspend fun extract(f: File, ocrEnabled: Boolean): String = runCatching {
        val ext = CategoryEngine.extensionOf(f.name)
        when {
            ext in TEXT_EXTS -> readTextHead(f)
            ext in IMAGE_EXTS && ocrEnabled -> ocr.recognize(f)
            ext == "pdf" -> pdf.extract(f, ocrEnabled)
            else -> ""
        }
    }.getOrDefault("")

    /** Reads at most MAX_TEXT+1 bytes of [f] as UTF-8 (never the whole file). */
    private fun readTextHead(f: File): String {
        if (f.length() <= 0L) return ""
        return FileInputStream(f).use { input ->
            val buf = ByteArray(ContentIndex.MAX_TEXT_PER_FILE + 64)
            val n = input.read(buf)
            if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
        }
    }

    // ------------------------------------------------------------- walking

    private fun roots(): List<File> {
        val out = ArrayList<File>()
        val internal = Paths.internalRoot()
        if (internal.exists()) out.add(internal)
        for (f in Paths.removableRoots()) out.add(f)
        return out
    }

    /** Files that need (re-)indexing, cheapest kinds first. */
    private fun collectCandidates(showHidden: Boolean): List<File> {
        val out = ArrayList<File>()
        for (root in roots()) {
            walk(root, showHidden, 0, out)
            if (out.size + index.size >= ContentIndex.MAX_ENTRIES) break
        }
        out.sortBy { priority(it) }
        return out
    }

    private fun priority(f: File): Int {
        val ext = CategoryEngine.extensionOf(f.name)
        return when {
            ext in TEXT_EXTS -> 0
            ext == "pdf" -> 1
            ext in IMAGE_EXTS -> 2
            else -> 3
        }
    }

    private fun walk(dir: File, showHidden: Boolean, depth: Int, out: MutableList<File>) {
        if (out.size + index.size >= ContentIndex.MAX_ENTRIES || depth > MAX_DEPTH) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (out.size + index.size >= ContentIndex.MAX_ENTRIES) break
            if (Paths.isExcluded(child)) continue
            if (!showHidden && child.name.startsWith(".")) continue
            if (child.isDirectory) {
                walk(child, showHidden, depth + 1, out)
            } else {
                val existing = index.get(child.absolutePath)
                if (existing == null || existing.lastModified != child.lastModified()) {
                    out.add(child)
                }
            }
        }
    }

    private fun collectPaths(dir: File, showHidden: Boolean, depth: Int, out: MutableSet<String>) {
        if (depth > MAX_DEPTH) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (Paths.isExcluded(child)) continue
            if (!showHidden && child.name.startsWith(".")) continue
            if (child.isDirectory) {
                collectPaths(child, showHidden, depth + 1, out)
            } else {
                out.add(child.absolutePath)
            }
        }
    }
}