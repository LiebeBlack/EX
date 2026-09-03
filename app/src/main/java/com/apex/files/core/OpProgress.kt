package com.apex.files.core

/** Long-running file operations surfaced by the Operation Center. */
enum class OpType {
    COPY, MOVE, DELETE, COMPRESS, EXTRACT, BENCHMARK
}

/**
 * Immutable snapshot of a running operation.
 *
 * [bytesTotal]/[filesTotal] may be null when the total is unknown
 * (e.g. a delete that discovers children while walking).
 */
data class OpProgress(
    val type: OpType,
    val bytesDone: Long = 0L,
    val bytesTotal: Long? = null,
    val filesDone: Int = 0,
    val filesTotal: Int? = null,
    val currentName: String = "",
    val speedBytesPerSec: Long = 0L,
) {
    val fraction: Float
        get() = if (bytesTotal != null && bytesTotal > 0L) {
            (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
        } else {
            -1f
        }

    val label: String
        get() = when (type) {
            OpType.COPY -> "Copiando"
            OpType.MOVE -> "Moviendo"
            OpType.DELETE -> "Eliminando"
            OpType.COMPRESS -> "Comprimiendo"
            OpType.EXTRACT -> "Extrayendo"
            OpType.BENCHMARK -> "Probando"
        }
}