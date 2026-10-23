package com.galleryorganizer.spikes.s01

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S01ProbeResultTest {
    @Test
    fun `complete probe passes regardless of root classification`() {
        assertEquals(S01SingleDeviceOutcome.PASS, completeResult().outcome)
        assertEquals(
            S01SingleDeviceOutcome.PASS,
            completeResult().copy(rootKind = RootKind.PICTURES).outcome,
        )
    }

    @Test
    fun `core failure fails and unfinished manual checks are partial`() {
        assertEquals(
            S01SingleDeviceOutcome.FAIL,
            completeResult().copy(partWriteReadback = false).outcome,
        )
        assertEquals(
            S01SingleDeviceOutcome.PARTIAL,
            completeResult().copy(deviceRebootRetryPassed = false).outcome,
        )
    }

    @Test
    fun `json uses fixed safe schema and state round trips`() {
        val result = completeResult()
        val json = result.toJson()
        EXPECTED_JSON_FIELDS.forEach { assertTrue(json.contains("\"$it\":")) }
        assertTrue(json.contains("\"canSelectDownloadRoot\":\"promptMessage\""))
        listOf("absolutePath", "content://", "/storage/", "real-user-file")
            .forEach { assertFalse(json.contains(it, ignoreCase = true)) }
        assertEquals(result, S01ProbeResult.decode(result.encode()))
    }

    private fun completeResult() = S01ProbeResult(
        deviceModel = "test-device",
        androidSdk = 35,
        buildFingerprint = "test-fingerprint",
        rootKind = RootKind.DOWNLOAD,
        canSelectDownloadRoot = DownloadRootSelection.PROMPT_MESSAGE,
        organizerRootReady = true,
        partWriteReadback = true,
        renameSucceeded = true,
        renamedActualDisplayName = ".s01-probe.webp",
        renamedActualMime = "image/webp",
        finalReadback = true,
        cleanupSucceeded = true,
        collideCreate = "AUTO_RENAME",
        collideRename = "AUTO_RENAME",
        createDirIdempotent = "AUTO_RENAME",
        oldUriStillReadable = false,
        forceStopRetryPassed = true,
        deviceRebootRetryPassed = true,
        reinstallAdoptionRead = true,
        reinstallAdoptionWrite = true,
        reinstallAdoptionDelete = true,
        reinstallOrphanClass = "READ_WRITE_DELETE",
        writeMbps = 20.0,
        readMbps = 30.0,
        peakHeapMb = 80.0,
        seekSupported = true,
        enumerate2kMillis = 1000,
        createPerFileMicros = 500,
        enumerate5kMillis = 2500,
        flagsRoot = listOf("DIR_SUPPORTS_CREATE"),
        flagsFile = listOf("SUPPORTS_WRITE", "SUPPORTS_RENAME", "SUPPORTS_DELETE"),
    )

    private companion object {
        val EXPECTED_JSON_FIELDS = listOf(
            "deviceModel", "androidSdk", "buildFingerprint", "rootKind",
            "canSelectDownloadRoot", "organizerRootReady", "partWriteReadback",
            "renameSucceeded", "renamedActualDisplayName", "renamedActualMime",
            "finalReadback", "cleanupSucceeded", "collideCreate", "collideRename",
            "createDirIdempotent", "oldUriStillReadable", "forceStopRetryPassed",
            "deviceRebootRetryPassed", "reinstallAdoptionRead",
            "reinstallAdoptionWrite", "reinstallAdoptionDelete",
            "reinstallOrphanClass", "writeMbps", "readMbps", "peakHeapMb",
            "seekSupported", "enumerate2kMillis", "createPerFileMicros",
            "enumerate5kMillis", "flagsRoot", "flagsFile", "exceptionClass",
            "exceptionMaskedMessage", "decisionSingleDevice",
        )
    }
}
