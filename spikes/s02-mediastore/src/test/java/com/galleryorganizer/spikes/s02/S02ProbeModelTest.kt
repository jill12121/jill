package com.galleryorganizer.spikes.s02

import com.galleryorganizer.spikes.s02.fixture.ExternalFixtureContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class S02ProbeModelTest {
    @Test
    fun `not granted denied empty and query failure cannot pass`() {
        assertEquals(
            S02SingleDeviceOutcome.FAIL,
            passingResult().copy(permissionState = MediaPermissionState.NOT_GRANTED).outcome,
        )
        assertEquals(
            S02SingleDeviceOutcome.FAIL,
            passingResult().copy(permissionState = MediaPermissionState.DENIED).outcome,
        )
        assertEquals(
            S02SingleDeviceOutcome.FAIL,
            passingResult().copy(totalVisibleRows = 0).outcome,
        )
        assertEquals(
            S02SingleDeviceOutcome.FAIL,
            passingResult().copy(exceptionClass = "QueryFailed").outcome,
        )
        assertEquals(
            S02SingleDeviceOutcome.FAIL,
            passingResult().copy(
                provenance = provenance().copy(runId = ""),
            ).outcome,
        )
    }

    @Test
    fun `provenance hashes fingerprint and records reproducible run fields`() {
        val provenance = S02ProbeProvenance.fromRawFingerprint(
            runId = "run-123",
            model = "model",
            sdkInt = 35,
            manufacturer = "maker",
            securityPatch = "2026-10-01",
            appVersionName = "0.3.1",
            appVersionCode = 31,
            rawFingerprint = "raw-fingerprint",
        )

        assertTrue(provenance.isValid)
        assertEquals(2, provenance.schemaVersion)
        assertEquals("S02.4", provenance.probeVersion)
        assertEquals(64, provenance.fingerprintSha256.length)
        assertFalse(provenance.toJson().contains("raw-fingerprint"))
        assertTrue(provenance.toJson().contains("run-123"))
    }

    @Test
    fun `view model retains probe run while activity UI is recreated`() {
        val viewModel = S02ProbeViewModel()
        val pending = S02ProbeRun(passingResult())

        viewModel.updateRun(pending)

        assertSame(pending, viewModel.run)
    }

    @Test
    fun `cleanup evidence and partial grant control final outcome`() {
        assertEquals(S02SingleDeviceOutcome.PASS, passingResult().outcome)
        assertEquals(
            S02SingleDeviceOutcome.PARTIAL,
            passingResult().copy(
                permissionState = MediaPermissionState.PARTIAL_USER_SELECTED,
            ).outcome,
        )
        assertEquals(
            S02SingleDeviceOutcome.PENDING,
            passingResult().copy(
                cleanupState = CleanupAuthorizationState.PENDING,
            ).outcome,
        )
        assertEquals(
            S02SingleDeviceOutcome.FAIL,
            passingResult().copy(
                cleanupState = CleanupAuthorizationState.GRANTED_STILL_PRESENT,
            ).outcome,
        )
        assertEquals(
            S02SingleDeviceOutcome.FAIL,
            passingResult().copy(authorizationBatchSizes = emptyList()).outcome,
        )
        assertEquals(
            S02SingleDeviceOutcome.FAIL,
            passingResult().copy(
                cleanupState = CleanupAuthorizationState.GRANTED_QUERY_FAILED,
            ).outcome,
        )
        assertEquals(
            S02SingleDeviceOutcome.PENDING,
            passingResult().copy(
                cleanupFixtureEvidence = null,
            ).outcome,
        )
    }

    @Test
    fun `external fixture requires exact helper provenance and token marker`() {
        val valid = externalEvidence()

        assertTrue(valid.isValid)
        assertFalse(valid.copy(ownerPackageName = "com.example.other").isValid)
        assertFalse(valid.copy(relativePath = "Pictures/Other/").isValid)
        assertFalse(valid.copy(mimeType = "text/html").isValid)
        assertFalse(valid.copy(displayName = ".s02-external-other.png").isValid)
        assertFalse(valid.copy(tokenMarkerMatches = false).isValid)
        assertEquals(
            S02SingleDeviceOutcome.FAIL,
            passingResult().copy(
                cleanupFixtureEvidence = valid.copy(tokenMarkerMatches = false),
            ).outcome,
        )
    }

    @Test
    fun `fixture contract accepts only canonical random token`() {
        val token = "123e4567-e89b-42d3-a456-426614174000"

        assertTrue(ExternalFixtureContract.isValidToken(token))
        assertEquals(".s02-external-$token.png", ExternalFixtureContract.displayName(token))
        assertFalse(ExternalFixtureContract.isValidToken("../escape"))
        assertFalse(ExternalFixtureContract.isValidToken(token.uppercase()))
    }

    @Test
    fun `pagination requires bounded monotonic duplicate-free honored pages`() {
        listOf(0, 1, 499, 500, 501, 1001).forEach { size ->
            assertTrue(PaginationEvidenceValidator.isValid(pagesFor(size)))
        }
        assertFalse(
            PaginationEvidenceValidator.isValid(
                listOf(PageEvidence(0, 1, 1, 1, 1, 0)),
            ),
        )
        assertFalse(
            PaginationEvidenceValidator.isValid(
                listOf(PageEvidence(0, 501, 1, 501, 10, 0)),
            ),
        )
        assertFalse(
            PaginationEvidenceValidator.isValid(
                listOf(
                    PageEvidence(0, 500, 1, 500, 10, 0),
                    PageEvidence(1, 1, 500, 500, 2, 1),
                ),
            ),
        )
        assertFalse(
            PaginationEvidenceValidator.isValid(
                listOf(
                    page(
                        pageIndex = 0,
                        rowCount = 2,
                        firstId = 1,
                        lastId = 2,
                        strictlyIncreasing = false,
                    ),
                ),
            ),
        )
        assertFalse(
            PaginationEvidenceValidator.isValid(
                listOf(
                    page(
                        pageIndex = 0,
                        rowCount = 2,
                        firstId = 1,
                        lastId = 2,
                        queryArgsHonored = false,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `authorization chunk boundaries never exceed 300`() {
        listOf(0, 1, 499, 500, 501, 1001).forEach { size ->
            val chunks = CleanupAuthorizationChunker.chunk((1L..size.toLong()).toList())
            assertEquals(size, chunks.sumOf(List<Long>::size))
            assertTrue(chunks.all { it.size in 1..300 })
        }
    }

    private fun pagesFor(size: Int): List<PageEvidence> =
        (1L..size.toLong()).toList().chunked(500).mapIndexed { index, ids ->
            page(index, ids.size, ids.first(), ids.last())
        }

    private fun page(
        pageIndex: Int,
        rowCount: Int,
        firstId: Long,
        lastId: Long,
        strictlyIncreasing: Boolean = true,
        queryArgsHonored: Boolean = true,
    ) = PageEvidence(
        pageIndex,
        rowCount,
        firstId,
        lastId,
        1,
        0,
        strictlyIncreasing,
        queryArgsHonored,
    )

    private fun passingResult() = S02ProbeResult(
        permissionState = MediaPermissionState.FULL,
        totalVisibleRows = 501,
        pages = listOf(
            page(0, 500, 1, 500),
            page(1, 1, 501, 501),
        ),
        cleanupState = CleanupAuthorizationState.GRANTED_REMOVED,
        authorizationBatchSizes = listOf(1),
        cleanupFixtureEvidence = externalEvidence(),
        provenance = provenance(),
    )

    private fun externalEvidence() = ExternalFixtureEvidence(
        ownerPackageName = ExternalFixtureContract.HELPER_PACKAGE,
        relativePath = ExternalFixtureContract.RELATIVE_PATH,
        mimeType = ExternalFixtureContract.MIME_TYPE,
        displayName = ExternalFixtureContract.displayName(
            "123e4567-e89b-42d3-a456-426614174000",
        ),
        token = "123e4567-e89b-42d3-a456-426614174000",
        tokenMarkerMatches = true,
    )

    private fun provenance() = S02ProbeProvenance.fromRawFingerprint(
        runId = "run-123",
        model = "model",
        sdkInt = 35,
        manufacturer = "maker",
        securityPatch = "2026-10-01",
        appVersionName = "0.3.1",
        appVersionCode = 31,
        rawFingerprint = "raw-fingerprint",
    )
}
