package com.apex.files.data.fs

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dynamic byte formatting (B/KB/MB/GB/TB) with fixed Spanish-independent decimals. */
object SizeFormatter {

    fun format(bytes: Long): String {
        val b = bytes.coerceAtLeast(0)
        return when {
            b < 1024L -> "$b B"
            b < 1024L * 1024 -> String.format(Locale.US, "%.1f KB", b / 1024.0)
            b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0))
            b < 1024L * 1024 * 1024 * 1024 ->
                String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f TB", b / (1024.0 * 1024.0 * 1024.0 * 1024.0))
        }
    }
}

/** Relative-friendly date formatting in Spanish, stable across devices. */
object DateFormatter {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale("es", "ES"))
    private val dayMonthFmt = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))
    private val fullFmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("es", "ES"))

    fun format(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "—"
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDateTime()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime()
        return when {
            date.toLocalDate() == now.toLocalDate() -> timeFmt.format(date)
            date.toLocalDate() == now.toLocalDate().minusDays(1) -> "Ayer, " + timeFmt.format(date)
            date.year == now.year -> dayMonthFmt.format(date)
            else -> fullFmt.format(date)
        }
    }
}