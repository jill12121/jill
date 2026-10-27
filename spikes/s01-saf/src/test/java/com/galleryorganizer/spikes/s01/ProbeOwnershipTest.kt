package com.galleryorganizer.spikes.s01

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeOwnershipTest {
    private val ownership = ProbeOwnership.create(
        "0123456789abcdef0123456789abcdef",
    )

    @Test
    fun `unrelated root json and fixed sample name are never adoptable`() {
        assertNull(
            ProbeOwnership.validate(
                "GalleryOrganizer",
                """{"schema":"s01-probe","version":1}""".encodeToByteArray(),
                ".s01-reinstall.bin",
                "legacy".encodeToByteArray(),
            ),
        )
    }

    @Test
    fun `damaged marker is never adoptable`() {
        assertNull(
            ProbeOwnership.validate(
                ownership.directoryName,
                "damaged".encodeToByteArray(),
                ownership.sampleName,
                ownership.sampleBytes,
            ),
        )
    }

    @Test
    fun `token mismatch is never adoptable`() {
        val other = ProbeOwnership.create(
            "fedcba9876543210fedcba9876543210",
        )
        assertNull(
            ProbeOwnership.validate(
                ownership.directoryName,
                other.markerBytes,
                other.sampleName,
                other.sampleBytes,
            ),
        )
    }

    @Test
    fun `unsupported marker schema is never adoptable`() {
        val marker = ownership.markerBytes.decodeToString()
            .replace("schema=s01-owner-v1", "schema=s01-owner-v2")
            .encodeToByteArray()
        assertNull(
            ProbeOwnership.validate(
                ownership.directoryName,
                marker,
                ownership.sampleName,
                ownership.sampleBytes,
            ),
        )
    }

    @Test
    fun `replaced sample is never adoptable`() {
        assertNull(
            ProbeOwnership.validate(
                ownership.directoryName,
                ownership.markerBytes,
                ownership.sampleName,
                "replaced".encodeToByteArray(),
            ),
        )
    }

    @Test
    fun `matching marker token sample and checksum are adoptable`() {
        val validated = ProbeOwnership.validate(
            ownership.directoryName,
            ownership.markerBytes,
            ownership.sampleName,
            ownership.sampleBytes,
        )

        assertEquals(ownership, validated)
        assertTrue(validated!!.directoryName.startsWith(".s01-probe-"))
    }

    @Test
    fun `download child is not reported as direct download root selection`() {
        assertEquals(
            DownloadRootSelection.TRUE,
            SafProbeRules.downloadRootSelection("primary:Download"),
        )
        assertEquals(
            DownloadRootSelection.PROMPT_MESSAGE,
            SafProbeRules.downloadRootSelection("primary:Download/GalleryOrganizer"),
        )
    }

    @Test
    fun `cleanup ledger retains a created item when later work fails`() {
        val ledger = CleanupLedger<String>()

        runCatching {
            ledger.register("created.part")
            error("rename failed")
        }

        assertEquals(listOf("created.part"), ledger.cleanupOrder())
    }

    @Test
    fun `adoption delete candidate never reuses ownership sample`() {
        assertNotEquals(ownership.sampleName, ownership.adoptionDeleteName)
    }
}
