package com.apex.files.tools

import java.io.File
import java.io.FileInputStream

/**
 * Zero-dependency EXIF extractor for JPEG files.
 *
 * Walks the JPEG segment table, finds the APP1 "Exif\0\0" block and decodes
 * the TIFF structure inside it (both little- and big-endian). Only the
 * fields a file manager actually shows are decoded: camera make/model,
 * orientation, software, timestamps, exposure, ISO, focal length, flash and
 * GPS coordinates. Image dimensions come from the JPEG SOF marker.
 *
 * Pure JVM — no Android classes — so the parser is covered by unit tests
 * that feed it a hand-built EXIF blob.
 */
object ExifReader {

    data class ExifData(
        val hasExif: Boolean = false,
        val make: String? = null,
        val model: String? = null,
        val orientation: Int? = null,
        val software: String? = null,
        val dateTime: String? = null,
        val dateTimeOriginal: String? = null,
        val exposureTime: Double? = null,
        val fNumber: Double? = null,
        val iso: Int? = null,
        val focalLength: Double? = null,
        val flash: Int? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val width: Int? = null,
        val height: Int? = null,
    ) {
        val hasLocation: Boolean get() = latitude != null && longitude != null
    }

    fun read(file: File): ExifData = try {
        FileInputStream(file).use { read(it.readBytes()) }
    } catch (e: Exception) {
        ExifData()
    }

    fun read(bytes: ByteArray): ExifData {
        if (bytes.size < 4 || (bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xD8) {
            return ExifData() // Not a JPEG.
        }
        var exif: ExifData? = null
        var width: Int? = null
        var height: Int? = null

        var i = 2
        val size = bytes.size
        while (i + 4 <= size) {
            if ((bytes[i].toInt() and 0xFF) != 0xFF) { i++; continue } // Padding fill byte.
            val marker = bytes[i + 1].toInt() and 0xFF
            i += 2
            if (marker == 0xD9 || marker == 0xDA) break // EOI / SOS → pixel data starts.
            if (marker == 0x01 || (marker in 0xD0..0xD7)) continue // Standalone markers.
            if (i + 2 > size) break
            val len = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            if (len < 2) break
            val dataStart = i + 2
            val dataEnd = (i + len).coerceAtMost(size)
            when (marker) {
                0xE1 -> if (exif == null && dataEnd - dataStart >= 12 && isExifHeader(bytes, dataStart)) {
                    exif = parseTiff(bytes, dataStart + 6, dataEnd)
                }
                in 0xC0..0xCF -> if (marker !in setOf(0xC4, 0xC8, 0xCC) && dataEnd - dataStart >= 5) {
                    // SOF: precision(1) + height(2) + width(2), big-endian.
                    height = u16be(bytes, dataStart + 1)
                    width = u16be(bytes, dataStart + 3)
                }
                else -> Unit
            }
            i = dataEnd
        }

        val e = exif ?: return ExifData()
        return e.copy(width = e.width ?: width, height = e.height ?: height)
    }

    private fun isExifHeader(bytes: ByteArray, start: Int): Boolean {
        val header = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
        if (start + header.size > bytes.size) return false
        for (j in header.indices) if (bytes[start + j] != header[j]) return false
        return true
    }

    // ------------------------------------------------------------ TIFF

    private fun parseTiff(bytes: ByteArray, start: Int, end: Int): ExifData {
        if (end - start < 8) return ExifData()
        val little = bytes[start].toInt() == 0x49 && bytes[start + 1].toInt() == 0x49
        val magic = u16(bytes, start + 2, little)
        if (magic != 42) return ExifData()
        val ifd0 = u32(bytes, start + 4, little)

        val d = TiffDecoder(bytes, start, end, little)
        val ifd0Values = d.ifd(ifd0)
        if (ifd0Values == null) return ExifData()

        var make = d.ascii(ifd0Values[0x010F])
        var model = d.ascii(ifd0Values[0x0110])
        val orientation = d.short(ifd0Values[0x0112])
        val software = d.ascii(ifd0Values[0x0131])
        val dateTime = d.ascii(ifd0Values[0x0132])
        val exifIfdOffset = d.long(ifd0Values[0x8769])
        val gpsIfdOffset = d.long(ifd0Values[0x8825])

        var exposureTime: Double? = null
        var fNumber: Double? = null
        var iso: Int? = null
        var dateTimeOriginal: String? = null
        var flash: Int? = null
        var focalLength: Double? = null

        if (exifIfdOffset != null) {
            val values = d.ifd(exifIfdOffset)
            if (values != null) {
                exposureTime = d.rational(values[0x829A])
                fNumber = d.rational(values[0x829D])
                iso = d.short(values[0x8827])
                dateTimeOriginal = d.ascii(values[0x9003])
                flash = d.short(values[0x9209])
                focalLength = d.rational(values[0x920A])
            }
        }

        var latitude: Double? = null
        var longitude: Double? = null
        if (gpsIfdOffset != null) {
            val values = d.ifd(gpsIfdOffset)
            if (values != null) {
                val latRef = d.ascii(values[0x0001])?.trim()?.uppercase()
                val lonRef = d.ascii(values[0x0003])?.trim()?.uppercase()
                val lat = d.gpsCoordinate(values[0x0002])
                val lon = d.gpsCoordinate(values[0x0004])
                if (lat != null) latitude = if (latRef == "S") -lat else lat
                if (lon != null) longitude = if (lonRef == "W") -lon else lon
            }
        }

        if (make.isNullOrBlank() && model.isNullOrBlank() && orientation == null && software.isNullOrBlank() &&
            dateTime.isNullOrBlank() && exposureTime == null && fNumber == null && iso == null &&
            latitude == null
        ) {
            return ExifData() // APP1 existed but held no usable metadata.
        }
        return ExifData(
            hasExif = true,
            make = make?.takeIf { it.isNotBlank() },
            model = model?.takeIf { it.isNotBlank() },
            orientation = orientation,
            software = software?.takeIf { it.isNotBlank() },
            dateTime = dateTime?.takeIf { it.isNotBlank() },
            dateTimeOriginal = dateTimeOriginal?.takeIf { it.isNotBlank() },
            exposureTime = exposureTime,
            fNumber = fNumber,
            iso = iso,
            focalLength = focalLength,
            flash = flash,
            latitude = latitude,
            longitude = longitude,
        )
    }

    /** Reads one IFD (a tag→raw-value map) or null when malformed. */
    private class TiffDecoder(
        private val bytes: ByteArray,
        private val tiffStart: Int,
        private val tiffEnd: Int,
        private val little: Boolean,
    ) {
        fun u16(at: Int): Int = u16(bytes, tiffStart + at, little)

        fun u32(at: Int): Long = u32(bytes, tiffStart + at, little)

        fun ifd(offset: Long): Map<Int, Long>? {
            val size = (tiffEnd - tiffStart).toLong()
            if (offset < 0 || offset + 2 > size) return null
            val count = u16(offset.toInt())
            if (count > 512 || offset + 2 + count * 12L > size) return null
            val out = HashMap<Int, Long>(count)
            for (e in 0 until count) {
                val base = offset.toInt() + 2 + e * 12
                val tag = u16(base)
                val type = u16(base + 2)
                val valueCount = u32(base + 4)
                val raw = u32(base + 8)
                out[tag] = valueCount shl 32 or (raw and 0xFFFFFFFFL)
            }
            return out
        }

        /** Encoded value: high 32 bits = component count, low 32 bits = value/offset. */
        private fun valueOrOffset(enc: Long): Long = enc and 0xFFFFFFFFL

        private fun componentCount(enc: Long): Int = (enc ushr 32).toInt()

        fun ascii(enc: Long?): String? {
            if (enc == null) return null
            val count = componentCount(enc)
            if (count <= 0 || count > 1_000_000) return null
            val raw = valueOrOffset(enc)
            val data: ByteArray? = if (count <= 4) {
                // Short strings live inline in the 4-byte value field, stored
                // in the TIFF byte order (first char in the first byte).
                ByteArray(count) { i ->
                    val shift = if (little) 8 * i else 8 * (count - 1 - i)
                    ((raw ushr shift) and 0xFF).toByte()
                }
            } else {
                val at = raw.toInt()
                if (at < 0 || at + count > tiffEnd - tiffStart) return null
                bytes.copyOfRange(tiffStart + at, tiffStart + at + count)
            } ?: return null
            var str = String(data, Charsets.US_ASCII).trim { it <= ' ' }
            str = str.trimEnd('\u0000')
            return str.takeIf { it.isNotEmpty() }
        }

        fun short(enc: Long?): Int? {
            if (enc == null) return null
            val count = componentCount(enc)
            val at = valueOrOffset(enc)
            if (count == 1) return (at and 0xFFFF).toInt()
            val off = at.toInt()
            return if (off + 2 <= tiffEnd - tiffStart) u16(off) else null
        }

        fun long(enc: Long?): Long? {
            if (enc == null) return null
            return valueOrOffset(enc)
        }

        fun rational(enc: Long?): Double? {
            if (enc == null) return null
            val at = valueOrOffset(enc).toInt()
            if (at + 8 > tiffEnd - tiffStart) return null
            val num = u32(at)
            val den = u32(at + 4)
            return if (den == 0L) null else num.toDouble() / den
        }

        /** GPS coordinate: three rationals (degrees, minutes, seconds). */
        fun gpsCoordinate(enc: Long?): Double? {
            if (enc == null) return null
            val at = valueOrOffset(enc).toInt()
            if (at + 24 > tiffEnd - tiffStart) return null
            val d = u32(at).toDouble() / u32(at + 4).coerceAtLeast(1)
            val m = u32(at + 8).toDouble() / u32(at + 12).coerceAtLeast(1)
            val s = u32(at + 16).toDouble() / u32(at + 20).coerceAtLeast(1)
            return d + m / 60.0 + s / 3600.0
        }
    }

    // ------------------------------------------------------------ helpers

    private fun u16be(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    private fun u16(bytes: ByteArray, at: Int, little: Boolean): Int =
        if (little) {
            (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
        } else {
            u16be(bytes, at)
        }

    private fun u32(bytes: ByteArray, at: Int, little: Boolean): Long =
        if (little) {
            (bytes[at].toLong() and 0xFF) or
                ((bytes[at + 1].toLong() and 0xFF) shl 8) or
                ((bytes[at + 2].toLong() and 0xFF) shl 16) or
                ((bytes[at + 3].toLong() and 0xFF) shl 24)
        } else {
            (bytes[at].toLong() and 0xFF shl 24) or
                ((bytes[at + 1].toLong() and 0xFF) shl 16) or
                ((bytes[at + 2].toLong() and 0xFF) shl 8) or
                (bytes[at + 3].toLong() and 0xFF)
        }
}