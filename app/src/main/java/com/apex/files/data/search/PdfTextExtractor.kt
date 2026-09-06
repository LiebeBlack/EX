package com.apex.files.data.search

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Extracts the native text layer of a PDF with pdfbox-android. When the PDF is
 * scanned (no text layer) and OCR is enabled, renders up to [MAX_OCR_PAGES]
 * pages and recognizes them with [OcrEngine]. Never throws — failures produce
 * an empty string and the file is simply not searchable by content.
 */
class PdfTextExtractor(private val ocr: OcrEngine) {

    companion object {
        private const val MAX_OCR_PAGES = 10
        private const val MAX_TEXT = ContentIndex.MAX_TEXT_PER_FILE
        private const val RENDER_SCALE = 1.5f
    }

    /** @param ocrEnabled when false, scanned PDFs are skipped (no OCR). */
    suspend fun extract(file: File, ocrEnabled: Boolean): String = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(file).use { doc ->
                val native = PDFTextStripper().getText(doc)
                if (native.isNotBlank()) return@use native.take(MAX_TEXT)
                if (!ocrEnabled) return@use ""
                val renderer = PDFRenderer(doc)
                val sb = StringBuilder()
                for (i in 0 until minOf(doc.numberOfPages, MAX_OCR_PAGES)) {
                    val page = renderer.renderImage(i, RENDER_SCALE)
                    try {
                        val text = ocr.recognizeBitmap(page)
                        if (text.isNotBlank()) {
                            sb.append(text).append(' ')
                            if (sb.length >= MAX_TEXT) break
                        }
                    } finally {
                        page.recycle()
                    }
                }
                sb.toString().take(MAX_TEXT)
            }
        }.getOrDefault("")
    }
}