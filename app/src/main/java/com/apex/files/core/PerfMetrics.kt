package com.apex.files.core

import com.apex.files.BuildConfig

/**
 * Always-on, allocation-light performance metrics for the heaviest internal
 * operations (directory listing, index rebuild/persist, semantic indexing,
 * search). No services, no threads, no I/O: samples live in a fixed 32-slot
 * ring buffer and are only written to logcat via println on debug builds.
 *
 * This is deliberately internal (user-invisible) diagnostics, consistent with
 * the app's zero-background / zero-telemetry guarantees: nothing leaves the
 * process and the buffer is bounded (a few KB at most).
 */
object PerfMetrics {

    /** One recorded operation. [atElapsedMillis] is process-relative uptime. */
    data class Sample(
        val tag: String,
        val durationMillis: Long,
        val count: Int?,
        val detail: String?,
        val atElapsedMillis: Long,
    )

    private const val CAPACITY = 32

    private val lock = Any()
    private val ring = arrayOfNulls<Sample>(CAPACITY)
    private var size = 0
    private var head = 0
    private val startNanos = System.nanoTime()

    /** Newest last. Fixed maximum of [CAPACITY] samples. */
    fun snapshot(): List<Sample> = synchronized(lock) {
        val out = ArrayList<Sample>(size)
        for (i in 0 until size) out.add(ring[(head + i) % CAPACITY]!!)
        out
    }

    fun clear() = synchronized(lock) {
        size = 0
        head = 0
        java.util.Arrays.fill(ring, null)
    }

    /** Records a completed operation. Never throws; costs one small object. */
    fun report(tag: String, durationMillis: Long, count: Int? = null, detail: String? = null) {
        val sample = Sample(
            tag = tag,
            durationMillis = durationMillis,
            count = count,
            detail = detail,
            atElapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L,
        )
        synchronized(lock) {
            if (size < CAPACITY) {
                ring[(head + size) % CAPACITY] = sample
                size++
            } else {
                ring[head] = sample
                head = (head + 1) % CAPACITY
            }
        }
        if (BuildConfig.DEBUG) {
            println(
                "[APEX-Perf] ${sample.tag} took ${sample.durationMillis} ms" +
                    (sample.count?.let { " · $it items" } ?: "") +
                    (sample.detail?.let { " · $it" } ?: ""),
            )
        }
    }

    /** Times a blocking [block] and reports it. */
    inline fun <T> time(
        tag: String,
        detail: String? = null,
        noinline countOf: ((T) -> Int)? = null,
        block: () -> T,
    ): T {
        val start = System.nanoTime()
        val result = block()
        val ms = (System.nanoTime() - start) / 1_000_000L
        report(tag, ms, count = countOf?.invoke(result), detail = detail)
        return result
    }

    /** Times a suspending [block] (caller is responsible for the dispatcher). */
    suspend inline fun <T> timeSuspend(
        tag: String,
        detail: String? = null,
        noinline countOf: ((T) -> Int)? = null,
        block: () -> T,
    ): T {
        val start = System.nanoTime()
        val result = block()
        val ms = (System.nanoTime() - start) / 1_000_000L
        report(tag, ms, count = countOf?.invoke(result), detail = detail)
        return result
    }
}
