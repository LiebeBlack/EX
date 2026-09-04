package com.apex.files.data.fs

/**
 * Honest outcome of a file operation. Repositories count partial failures
 * instead of reporting blind success, so the UI can summarize what really
 * happened ("3 errores", "2 omitidos", …).
 */
data class OpResult(
    val bytesDone: Long = 0L,
    val filesDone: Int = 0,
    val errors: Int = 0,
    val firstError: String? = null,
    val skipped: Int = 0,
    val cancelled: Boolean = false,
) {
    val ok: Boolean get() = errors == 0 && !cancelled

    operator fun plus(other: OpResult): OpResult = OpResult(
        bytesDone = bytesDone + other.bytesDone,
        filesDone = filesDone + other.filesDone,
        errors = errors + other.errors,
        firstError = firstError ?: other.firstError,
        skipped = skipped + other.skipped,
        cancelled = cancelled || other.cancelled,
    )

    fun recordError(message: String): OpResult = copy(
        errors = errors + 1,
        firstError = firstError ?: message,
    )
}
