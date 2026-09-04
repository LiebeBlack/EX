package com.apex.files

import com.apex.files.data.fs.OpResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpResultTest {

    @Test
    fun `empty result is ok`() {
        val r = OpResult()
        assertTrue(r.ok)
        assertEquals(0, r.errors)
        assertNull(r.firstError)
    }

    @Test
    fun `recordError keeps the first message`() {
        val r = OpResult().recordError("primero").recordError("segundo")
        assertEquals(2, r.errors)
        assertEquals("primero", r.firstError)
        assertFalse(r.ok)
    }

    @Test
    fun `plus accumulates counters`() {
        val a = OpResult(bytesDone = 100L, filesDone = 2, errors = 1, firstError = "x")
        val b = OpResult(bytesDone = 50L, filesDone = 1, skipped = 3)
        val sum = a + b
        assertEquals(150L, sum.bytesDone)
        assertEquals(3, sum.filesDone)
        assertEquals(1, sum.errors)
        assertEquals("x", sum.firstError)
        assertEquals(3, sum.skipped)
    }

    @Test
    fun `first error wins over a later one`() {
        val r = OpResult().recordError("primero") + OpResult().recordError("segundo")
        assertEquals("primero", r.firstError)
    }
}
