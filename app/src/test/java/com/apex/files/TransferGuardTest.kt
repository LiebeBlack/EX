package com.apex.files

import com.apex.files.data.fs.TransferGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferGuardTest {

    @Test
    fun `same path is inside or self`() {
        assertTrue(TransferGuard.sameOrDescendant("/storage/emulated/0", "/storage/emulated/0"))
    }

    @Test
    fun `direct child is inside parent`() {
        assertTrue(TransferGuard.sameOrDescendant("/storage/emulated/0/Download", "/storage/emulated/0"))
        assertTrue(TransferGuard.sameOrDescendant("/storage/emulated/0/Download/A", "/storage/emulated/0/Download"))
    }

    @Test
    fun `prefix look-alike is not inside`() {
        // /storage/emulated/0/Download2 must NOT match inside /storage/emulated/0/Download
        assertFalse(TransferGuard.sameOrDescendant("/storage/emulated/0/Download2", "/storage/emulated/0/Download"))
    }

    @Test
    fun `siblings are not inside each other`() {
        assertFalse(TransferGuard.sameOrDescendant("/storage/emulated/0/Pictures", "/storage/emulated/0/Download"))
    }

    @Test
    fun `trailing separators are normalized`() {
        assertTrue(TransferGuard.sameOrDescendant("/storage/emulated/0/Download/", "/storage/emulated/0/Download"))
        assertTrue(TransferGuard.sameOrDescendant("/storage/emulated/0/Download", "/storage/emulated/0/Download/"))
    }

    @Test
    fun `saf document ids follow the same rule`() {
        assertTrue(TransferGuard.safInsideOrSelf("primary:Download/sub", "primary:Download"))
        assertTrue(TransferGuard.safInsideOrSelf("primary:Download", "primary:Download"))
        assertFalse(TransferGuard.safInsideOrSelf("primary:DownloadX", "primary:Download"))
        assertFalse(TransferGuard.safInsideOrSelf("primary:Pictures", "primary:Download"))
        assertFalse(TransferGuard.safInsideOrSelf(null, "primary:Download"))
        assertFalse(TransferGuard.safInsideOrSelf("primary:Download", null))
    }
}
