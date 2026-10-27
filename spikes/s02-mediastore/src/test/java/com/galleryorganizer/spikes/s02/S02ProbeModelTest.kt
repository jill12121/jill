package com.galleryorganizer.spikes.s02

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    }

    @Test
    fun `pagination requires bounded monotonic duplicate-free pages`() {
        listOf(0, 1, 499, 500, 501, 1001).forEach { size ->
            assertTrue(PaginationEvidenceValidator.isValid(pagesFor(size)))
        }
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
            PageEvidence(index, ids.size, ids.first(), ids.last(), 1, 0)
        }

    private fun passingResult() = S02ProbeResult(
        permissionState = MediaPermissionState.FULL,
        totalVisibleRows = 501,
        pages = listOf(
            PageEvidence(0, 500, 1, 500, 10, 0),
            PageEvidence(1, 1, 501, 501, 2, 0),
        ),
        cleanupState = CleanupAuthorizationState.GRANTED_REMOVED,
        authorizationBatchSizes = listOf(1),
    )
}
