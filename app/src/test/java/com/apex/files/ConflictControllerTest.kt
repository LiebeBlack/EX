package com.apex.files

import com.apex.files.data.fs.Conflict
import com.apex.files.data.fs.ConflictController
import com.apex.files.data.fs.ConflictDecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictControllerTest {

    private val dirConflict = Conflict(name = "Fotos", destPath = "/sdcard/Fotos", isDir = true)
    private val fileConflict = Conflict(
        name = "a.txt",
        destPath = "/sdcard/a.txt",
        isDir = false,
        existingSize = 10L,
        existingModified = 100L,
    )

    /** Waits until [controller] surfaces a conflict to the dialog layer. */
    private suspend fun awaitPending(controller: ConflictController): Conflict {
        withTimeout(5_000) {
            while (controller.pending.value == null) yield()
        }
        return controller.pending.value!!
    }

    @Test
    fun `resolve suspends until the dialog answers`() = runBlocking {
        val controller = ConflictController()
        val decision = CompletableDeferred<ConflictDecision>()
        launch { decision.complete(controller.resolve(fileConflict)) }

        // The conflict is exposed to the UI while unresolved.
        assertEquals(fileConflict, awaitPending(controller))
        assertTrue(controller.isPending())

        controller.answer(ConflictDecision.KEEP_BOTH)
        assertEquals(ConflictDecision.KEEP_BOTH, decision.await())

        // Answered: the pending conflict clears for the next collision.
        assertNull(controller.pending.value)
        assertFalse(controller.isPending())
        Unit
    }

    @Test
    fun `apply to all answers subsequent conflicts without a dialog`() = runBlocking {
        val controller = ConflictController()
        controller.answer(ConflictDecision.OVERWRITE, applyToAll = true)

        // Auto-resolved: never surfaces a pending conflict.
        assertEquals(ConflictDecision.OVERWRITE, controller.resolve(dirConflict))
        assertNull(controller.pending.value)
        assertFalse(controller.isPending())
        Unit
    }

    @Test
    fun `resetApplyAll clears the remembered decision`() = runBlocking {
        val controller = ConflictController()
        controller.answer(ConflictDecision.SKIP, applyToAll = true)
        controller.resetApplyAll()

        val decision = CompletableDeferred<ConflictDecision>()
        launch { decision.complete(controller.resolve(dirConflict)) }
        assertEquals(dirConflict, awaitPending(controller))
        controller.answer(ConflictDecision.SKIP)
        assertEquals(ConflictDecision.SKIP, decision.await())
        Unit
    }

    @Test
    fun `second concurrent resolve keeps both and never stacks a second dialog`() = runBlocking {
        val controller = ConflictController()
        val first = CompletableDeferred<ConflictDecision>()
        launch { first.complete(controller.resolve(fileConflict)) }
        assertEquals(fileConflict, awaitPending(controller))

        // While one dialog is pending a racing collision is auto-resolved.
        assertEquals(ConflictDecision.KEEP_BOTH, controller.resolve(dirConflict))
        assertEquals(fileConflict, controller.pending.value)

        controller.answer(ConflictDecision.OVERWRITE)
        assertEquals(ConflictDecision.OVERWRITE, first.await())
        Unit
    }

    @Test
    fun `cancelling the operation clears any remembered apply-to-all choice`() = runBlocking {
        val controller = ConflictController()

        // Skip-all is remembered while the operation runs.
        val first = CompletableDeferred<ConflictDecision>()
        launch { first.complete(controller.resolve(fileConflict)) }
        assertEquals(fileConflict, awaitPending(controller))
        controller.answer(ConflictDecision.SKIP, applyToAll = true)
        assertEquals(ConflictDecision.SKIP, first.await())
        assertEquals(ConflictDecision.SKIP, controller.resolve(dirConflict))

        // An explicit cancellation (dismiss) wipes the stored choice: the next
        // collision must surface a dialog again instead of auto-skipping.
        controller.dismiss()
        val second = CompletableDeferred<ConflictDecision>()
        launch { second.complete(controller.resolve(dirConflict)) }
        assertEquals(dirConflict, awaitPending(controller))
        controller.answer(ConflictDecision.OVERWRITE)
        assertEquals(ConflictDecision.OVERWRITE, second.await())
        Unit
    }

    @Test
    fun `auto-resolve keeps answering while apply-to-all stays active`() = runBlocking {
        val controller = ConflictController()
        controller.answer(ConflictDecision.OVERWRITE, applyToAll = true)
        repeat(5) { i ->
            val resolved = controller.resolve(
                Conflict(name = "f$i", destPath = "/sdcard/f$i", isDir = false),
            )
            assertEquals(ConflictDecision.OVERWRITE, resolved)
        }
        assertFalse(controller.isPending())
        Unit
    }

    @Test
    fun `dismiss with nothing pending is a safe no-op`() = runBlocking {
        val controller = ConflictController()
        controller.dismiss()
        assertNull(controller.pending.value)
        assertFalse(controller.isPending())

        // And a pending resolve still works normally afterwards.
        val decision = CompletableDeferred<ConflictDecision>()
        launch { decision.complete(controller.resolve(fileConflict)) }
        assertEquals(fileConflict, awaitPending(controller))
        controller.answer(ConflictDecision.OVERWRITE)
        assertEquals(ConflictDecision.OVERWRITE, decision.await())
        Unit
    }
}
