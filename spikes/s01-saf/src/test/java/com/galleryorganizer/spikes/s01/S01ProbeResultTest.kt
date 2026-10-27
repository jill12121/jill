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

    @Test
    fun `raw build fingerprint is never stored or exported`() {
        val secret = "secret/vendor/carrier/release-id/incremental:user/test-keys"
        val result = completeResult().copy(
            buildFingerprintHash = S01ProbeResult.redactFingerprint(secret),
        )
        val json = result.toJson()
        assertFalse(json.contains("buildFingerprint\""))
        assertFalse(json.contains(secret))
        assertFalse(json.contains("test-keys"))
        assertTrue(json.contains("\"buildFingerprintHash\":\"sha256:"))
        assertEquals(
            "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            S01ProbeResult.redactFingerprint("abc"),
        )
    }

    @Test
    fun `json exports manufacturer and security patch`() {
        val json = completeResult().copy(
            manufacturer = "test-maker",
            securityPatch = "2026-10-05",
        ).toJson()
        assertTrue(json.contains("\"manufacturer\":\"test-maker\""))
        assertTrue(json.contains("\"securityPatch\":\"2026-10-05\""))
    }

    @Test
    fun `json escapes control characters instead of emitting raw bytes`() {
        val json = completeResult().copy(deviceModel = "a\tb\u0000c").toJson()
        assertTrue(json.contains("\"deviceModel\":\"a\\tb\\u0000c\""))
        assertFalse(json.contains("\t"))
        assertFalse(json.contains("\u0000"))
    }

    private fun completeResult() = S01ProbeResult(
        deviceModel = "test-device",
        androidSdk = 35,
        manufacturer = "test-maker",
        securityPatch = "2026-10-05",
        buildFingerprintHash = S01ProbeResult.redactFingerprint("test-fingerprint"),
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
            "deviceModel", "androidSdk", "manufacturer", "securityPatch",
            "buildFingerprintHash", "rootKind",
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
