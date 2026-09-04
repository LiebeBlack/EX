package com.apex.files.data.fs

/** Minimal RFC-4180-style CSV writer for exporting query results. */
object Csv {

    /**
     * Serializes [columns] plus [rows] (null cells become empty fields).
     * Fields containing commas, quotes or line breaks are quoted and inner
     * quotes doubled; rows are CRLF-terminated.
     */
    fun toCsv(columns: List<String>, rows: List<List<String?>>): String {
        val sb = StringBuilder(rows.size * columns.size * 8 + 128)
        appendRow(sb, columns)
        for (row in rows) appendRow(sb, row)
        return sb.toString()
    }

    private fun appendRow(sb: StringBuilder, cells: List<String?>) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) sb.append(',')
            sb.append(escape(cell ?: ""))
        }
        sb.append("\r\n")
    }

    private fun escape(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
