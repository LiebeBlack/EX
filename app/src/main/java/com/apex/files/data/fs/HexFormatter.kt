package com.apex.files.data.fs

/**
 * Pure hex-dump formatting for the built-in binary viewer. Formats a window
 * of bytes into classic editor lines: offset · hex bytes · printable ASCII.
 * Zero Android dependencies, unit-tested.
 */
object HexFormatter {

    const val BYTES_PER_LINE = 16

    /** e.g. "00000040  48 65 6C 6C 6F …  |Hello…|" */
    fun formatLine(offset: Long, bytes: ByteArray, bytesPerLine: Int = BYTES_PER_LINE): String {
        val hex = StringBuilder(bytesPerLine * 3)
        val ascii = StringBuilder(bytesPerLine)
        for (i in 0 until bytesPerLine) {
            if (i < bytes.size) {
                val b = bytes[i].toInt() and 0xFF
                if (i == bytesPerLine / 2) hex.append(' ')
                hex.append(hexNibble(b ushr 4)).append(hexNibble(b and 0xF)).append(' ')
                ascii.append(if (b in 0x20..0x7E) b.toChar() else '.')
            } else {
                if (i == bytesPerLine / 2) hex.append(' ')
                hex.append("   ")
                ascii.append(' ')
            }
        }
        return "${offsetHex(offset)}  $hex |$ascii|"
    }

    /** Column header aligned with [formatLine]: "Offset    00 … 0F   |ASCII|". */
    fun formatHeader(bytesPerLine: Int = BYTES_PER_LINE): String {
        val hex = StringBuilder(bytesPerLine * 3 + 1)
        for (i in 0 until bytesPerLine) {
            if (i == bytesPerLine / 2) hex.append(' ')
            hex.append(hexNibble(i ushr 4)).append(hexNibble(i)).append(' ')
        }
        return "Offset    $hex |" + "ASCII".padEnd(bytesPerLine) + "|"
    }

    /** Formats [bytes] (already at file offset [offset]) into consecutive lines. */
    fun formatWindow(offset: Long, bytes: ByteArray, bytesPerLine: Int = BYTES_PER_LINE): List<String> {
        val lines = ArrayList<String>((bytes.size + bytesPerLine - 1) / bytesPerLine)
        var at = offset
        var i = 0
        while (i < bytes.size) {
            val chunk = minOf(bytesPerLine, bytes.size - i)
            lines.add(formatLine(at, bytes.copyOfRange(i, i + chunk), bytesPerLine))
            i += chunk
            at += chunk
        }
        return lines
    }

    private fun hexNibble(v: Int): Char = if (v < 10) '0' + v else 'A' + (v - 10)

    private fun offsetHex(offset: Long): String {
        val hex = offset.toString(16).uppercase()
        return hex.padStart(8, '0')
    }
}