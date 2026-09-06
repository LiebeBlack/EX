package com.apex.files.data.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device OCR via ML Kit's bundled Latin model (works without Google Play
 * Services and without any network access). Images larger than [MAX_DIM] are
 * downscaled before recognition to bound memory and latency. The recognizer is
 * not thread-safe, so calls are serialized by the indexer.
 */
class OcrEngine(context: Context) {

    companion object {
        private const val MAX_DIM = 2048
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Recognizes an image file; returns "" on any failure (never throws). */
    suspend fun recognize(file: File): String = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeDownscaled(file) ?: return@runCatching ""
            try {
                recognizeBitmap(bitmap)
            } finally {
                bitmap.recycle()
            }
        }.getOrDefault("")
    }

    /** Recognizes a Bitmap (e.g. a rendered PDF page). Never throws. */
    suspend fun recognizeBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            Tasks.await(recognizer.process(image)).text
        }.getOrDefault("")
    }

    private fun decodeDownscaled(file: File, maxDim: Int = MAX_DIM): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        if (decoded.width <= maxDim && decoded.height <= maxDim) return decoded
        // Sample sizes are powers of two; shrink the remainder preserving ratio.
        val scale = maxDim.toFloat() / maxOf(decoded.width, decoded.height)
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != decoded) decoded.recycle()
        return scaled
    }
}