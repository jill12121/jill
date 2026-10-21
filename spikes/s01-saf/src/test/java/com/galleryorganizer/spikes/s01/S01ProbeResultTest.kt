package com.galleryorganizer.spikes.s01

import org.junit.Assert.assertEquals
import org.junit.Test

class S01ProbeResultTest {
    @Test
    fun `download with rename and restored grant is go`() = assertEquals(
        S01Decision.GO,
        passingResult().copy(savedGrantRestored = true).decision,
    )

    @Test
    fun `rename failure with readable part is limited go`() = assertEquals(
        S01Decision.LIMITED_GO,
        passingResult().copy(renameSucceeded = false, finalReadback = false).decision,
    )

    @Test
    fun `pictures fallback is limited go`() = assertEquals(
        S01Decision.LIMITED_GO,
        passingResult().copy(rootKind = RootKind.PICTURES).decision,
    )

    @Test
    fun `lost grant or unreadable write is no go`() {
        assertEquals(passingResult(), S01ProbeResult.decode(passingResult().encode()))
        assertEquals(
            S01Decision.NO_GO,
            passingResult().copy(permissionPersisted = false).decision,
        )
        assertEquals(
            S01Decision.NO_GO,
            passingResult().copy(partWriteReadback = false).decision,
        )
    }

    private fun passingResult() = S01ProbeResult(
        rootKind = RootKind.DOWNLOAD,
        permissionPersisted = true,
        organizerRootReady = true,
        partWriteReadback = true,
        renameSucceeded = true,
        finalReadback = true,
        cleanupSucceeded = true,
    )
}
