package com.apex.files.data.fs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CompletableDeferred

/**
 * A destination name collision detected mid-operation. Surfaced to the UI
 * one at a time so the user can decide per file.
 */
data class Conflict(
    val name: String,
    val destPath: String,
    val isDir: Boolean,
    val existingSize: Long = -1L,
    val existingModified: Long = 0L,
)

/** User resolution for a [Conflict]. */
enum class ConflictDecision {
    /** Replace/merge into the existing destination entry. */
    OVERWRITE,

    /** Leave the existing entry untouched and skip this source. */
    SKIP,

    /** Keep both: destination gets "name (1).ext". */
    KEEP_BOTH,

    /** Abort the whole operation. */
    CANCEL_OPERATION,
}

/** Thrown inside an operation when the user chooses [ConflictDecision.CANCEL_OPERATION]. */
class ConflictCancelledException : Exception("Operación cancelada")

/**
 * Bridges the suspend file-operation layer with the Compose dialog layer.
 * A repository calls [resolve] (on an IO thread) while a conflict is
 * pending; the UI observes [pending] and answers through [answer]. The
 * suspend call resumes as soon as a decision arrives.
 *
 * Thread-safe: [resolve] and [answer] may be called from different threads.
 */
class ConflictController {

    private val lock = Any()

    private val _pending = MutableStateFlow<Conflict?>(null)
    val pending: StateFlow<Conflict?> = _pending.asStateFlow()

    private var deferred: CompletableDeferred<ConflictDecision>? = null

    /** Called by the file layer when a destination name already exists. */
    suspend fun resolve(conflict: Conflict): ConflictDecision {
        val busy = synchronized(lock) { deferred != null || _pending.value != null }
        if (busy) return ConflictDecision.KEEP_BOTH
        val d = CompletableDeferred<ConflictDecision>()
        synchronized(lock) {
            deferred = d
            _pending.value = conflict
        }
        return try {
            d.await()
        } finally {
            synchronized(lock) {
                deferred = null
                _pending.value = null
            }
        }
    }

    /** Called by the dialog UI with the user's choice. */
    fun answer(decision: ConflictDecision) {
        synchronized(lock) { deferred?.complete(decision) }
    }

    /** Dismissed dialog (back press) aborts the pending operation safely. */
    fun dismiss() = answer(ConflictDecision.CANCEL_OPERATION)

    /** True when a conflict dialog is currently on screen. */
    fun isPending(): Boolean = synchronized(lock) { deferred != null }
}
