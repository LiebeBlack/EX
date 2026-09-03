package com.apex.files.core

import kotlin.math.roundToLong

/**
 * Computes a smoothed transfer speed (bytes/sec) using an exponentially
 * weighted moving average of instantaneous byte deltas. Pure JVM, testable.
 */
class SpeedTracker(private val windowMs: Long = 1500L) {

    private var lastBytes: Long = 0L
    private var lastTime: Long = 0L
    private var smoothed: Double = 0.0
    private var initialized = false

    /** Feed cumulative bytes; returns the current smoothed speed in bytes/sec. */
    fun update(cumulativeBytes: Long, nowMillis: Long = System.currentTimeMillis()): Long {
        if (!initialized) {
            lastBytes = cumulativeBytes
            lastTime = nowMillis
            initialized = true
            smoothed = 0.0
            return 0L
        }
        val dt = nowMillis - lastTime
        val db = cumulativeBytes - lastBytes
        lastBytes = cumulativeBytes
        lastTime = nowMillis
        if (dt <= 0) return smoothed.roundToLong()
        val instant = db * 1000.0 / dt
        smoothed = if (smoothed <= 0.0) instant else (smoothed * 0.7 + instant * 0.3)
        return smoothed.roundToLong()
    }

    fun reset() {
        initialized = false
        smoothed = 0.0
        lastBytes = 0L
        lastTime = 0L
    }
}

/** Formats a byte count as "12.4 MB/s" using the shared size formatter. */
fun formatSpeed(bytesPerSec: Long): String {
    val bps = bytesPerSec.coerceAtLeast(0)
    return when {
        bps < 1024 -> "$bps B/s"
        bps < 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f KB/s", bps / 1024.0)
        bps < 1024L * 1024 * 1024 ->
            String.format(java.util.Locale.US, "%.1f MB/s", bps / (1024.0 * 1024.0))
        else -> String.format(java.util.Locale.US, "%.2f GB/s", bps / (1024.0 * 1024.0 * 1024.0))
    }
}