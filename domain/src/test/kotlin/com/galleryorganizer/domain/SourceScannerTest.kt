package com.galleryorganizer.domain

import com.galleryorganizer.contract.FsResult
import com.galleryorganizer.contract.SourceDiscoveryGateway
import com.galleryorganizer.contract.SourceDiscoveryPage
import com.galleryorganizer.contract.SourceFileRef
import com.galleryorganizer.contract.SourceMediaMetadata
import com.galleryorganizer.contract.StorageFailure
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SourceScannerTest {
    @Test
    fun `scanner aggregates keyset pages and completes full coverage`() = runBlocking {
        val gateway = QueueGateway(
            FsResult.Success(page(1, 2, next = 2)),
            FsResult.Success(page(3, next = null)),
        )

        val result = DefaultScanner(gateway).scan(request(ScanCoverage.FULL, pageSize = 2))

        val completed = assertIs<SourceScanResult.Completed>(result)
        assertEquals(2, completed.pageCount)
        assertEquals(3, completed.mediaCount)
        assertEquals(3, completed.snapshot.batches.single().imageCount)
        assertTrue(completed.snapshot.canOrganizeWholeBatch)
        assertEquals(listOf(null, 2L), gateway.afterIds)
    }

    @Test
    fun `partial coverage never enables whole batch organization`() = runBlocking {
        val result = DefaultScanner(
            QueueGateway(FsResult.Success(page(1, next = null))),
        ).scan(request(ScanCoverage.PARTIAL_USER_SELECTED))

        val completed = assertIs<SourceScanResult.Completed>(result)
        assertFalse(completed.snapshot.canOrganizeWholeBatch)
        assertTrue(completed.snapshot.canImportExplicitSelection)
    }

    @Test
    fun `provider failure discards incomplete pages`() = runBlocking {
        val result = DefaultScanner(
            QueueGateway(
                FsResult.Success(page(1, next = 1)),
                FsResult.Failure(StorageFailure.AccessDenied),
            ),
        ).scan(request(ScanCoverage.FULL, pageSize = 1))

        val failure = assertIs<SourceScanResult.Failed>(result).failure
        assertEquals(StorageOperation.SOURCE_ENUMERATION, failure.operation)
        assertEquals(StorageFailure.AccessDenied, failure.failure)
    }

    @Test
    fun `invalid page contracts fail closed`() = runBlocking {
        val oversized = DefaultScanner(
            QueueGateway(FsResult.Success(page(1, 2, next = 2))),
        ).scan(request(ScanCoverage.FULL, pageSize = 1))
        val stalled = DefaultScanner(
            QueueGateway(FsResult.Success(page(1, next = 0))),
        ).scan(request(ScanCoverage.FULL, pageSize = 1))

        assertIs<SourceScanResult.Failed>(oversized)
        assertIs<SourceScanResult.Failed>(stalled)
        Unit
    }

    @Test
    fun `runtime grants map to scan coverage by Android generation`() {
        assertEquals(
            ScanCoverage.FULL,
            scanCoverageFor(32, setOf(MediaReadGrant.READ_EXTERNAL_STORAGE)),
        )
        assertEquals(
            ScanCoverage.FULL,
            scanCoverageFor(33, setOf(MediaReadGrant.READ_MEDIA_IMAGES)),
        )
        assertEquals(
            ScanCoverage.PARTIAL_USER_SELECTED,
            scanCoverageFor(34, setOf(MediaReadGrant.VISUAL_USER_SELECTED)),
        )
        assertEquals(ScanCoverage.UNKNOWN, scanCoverageFor(35, emptySet()))
    }

    private fun request(
        coverage: ScanCoverage,
        pageSize: Int = 500,
    ) = SourceScanRequest(
        rootRelativePath = "Download",
        pageSize = pageSize,
        config = SourceDiscoveryConfig(
            organizerRootRelativePath = "Download/GalleryOrganizer",
            protectedDirectoryPaths = emptySet(),
            monitoredFirstLevelDirectories = setOf("Trip"),
            coverage = coverage,
            completed = false,
        ),
    )

    private fun page(vararg ids: Long, next: Long?) = SourceDiscoveryPage(
        media = ids.map {
            SourceMediaMetadata(
                mediaId = it,
                ref = SourceFileRef("media:$it"),
                relativePath = "Download/Trip/",
                displayName = "$it.jpg",
                sizeBytes = 100,
                modifiedAtEpochMillis = 200,
                mimeType = "image/jpeg",
            )
        },
        nextAfterId = next,
    )

    private class QueueGateway(
        vararg results: FsResult<SourceDiscoveryPage>,
    ) : SourceDiscoveryGateway {
        private val queue = ArrayDeque(results.toList())
        val afterIds = mutableListOf<Long?>()

        override suspend fun discoverPage(
            rootRelativePath: String,
            afterId: Long?,
            limit: Int,
        ): FsResult<SourceDiscoveryPage> {
            afterIds += afterId
            return queue.removeFirst()
        }
    }
}
