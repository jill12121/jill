package com.galleryorganizer.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.galleryorganizer.contract.SourceFileRef
import com.galleryorganizer.domain.DiscoveredSourceBatch
import com.galleryorganizer.domain.DiscoveredSourceItem
import com.galleryorganizer.domain.ImportRecordState
import com.galleryorganizer.domain.ScanCoverage
import com.galleryorganizer.domain.SourceBatchKind
import com.galleryorganizer.domain.SourceDecision
import com.galleryorganizer.domain.SourceDecisionValue
import com.galleryorganizer.domain.SourceDiscoverySnapshot
import com.galleryorganizer.domain.SourcePageRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RoomSourceMetadataRepositoryTest {
    private lateinit var database: OrganizerMetadataDatabase
    private lateinit var repository: RoomSourceMetadataRepository
    private lateinit var dao: OrganizerMetadataDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrganizerMetadataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.metadataDao()
        repository = RoomSourceMetadataRepository(dao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `full snapshot replaces stale batch membership while partial snapshot merges`() =
        runBlocking {
            repository.persistSnapshot(snapshot(item(1), item(2)), 1)
            repository.persistSnapshot(
                snapshot(item(2), coverage = ScanCoverage.PARTIAL_USER_SELECTED),
                2,
            )
            assertEquals(2, repository.loadBatches(setOf(BATCH)).single().items.size)

            repository.persistSnapshot(snapshot(item(2)), 3)
            assertEquals(
                listOf("mediastore:image:2"),
                repository.loadBatches(setOf(BATCH)).single().items.map { it.fileId },
            )
        }

    @Test
    fun `decision applies only to exact current source key`() = runBlocking {
        repository.persistSnapshot(snapshot(item(1)), 1)
        val initial = repository.loadBatches(setOf(BATCH)).single().items.single()
        repository.saveDecision(
            SourceDecision(initial.sourceKey, SourceDecisionValue.REVIEW, 10),
        )
        assertEquals(
            SourceDecisionValue.REVIEW,
            repository.loadBatches(setOf(BATCH)).single().items.single().decision,
        )

        repository.persistSnapshot(snapshot(item(1, sizeBytes = 101)), 2)
        assertNull(repository.loadBatches(setOf(BATCH)).single().items.single().decision)
    }

    @Test
    fun `imported requires matching locator and durable state`() = runBlocking {
        repository.persistSnapshot(snapshot(item(1)), 1)
        val source = repository.loadBatches(setOf(BATCH)).single().items.single()
        dao.insertImportRecord(importRecord(source.fileId, source.sourceKey, "COPIED"))
        assertFalse(repository.loadBatches(setOf(BATCH)).single().items.single().imported)

        dao.insertImportRecord(
            importRecord(
                source.fileId,
                source.sourceKey,
                ImportRecordState.COMMITTED.name,
                importId = "committed",
            ),
        )
        assertTrue(repository.loadBatches(setOf(BATCH)).single().items.single().imported)

        repository.persistSnapshot(snapshot(item(1, modifiedAt = 3000)), 2)
        assertFalse(repository.loadBatches(setOf(BATCH)).single().items.single().imported)
    }

    @Test
    fun `source pages are bounded monotonic and isolated by batch`() = runBlocking {
        repository.persistSnapshot(
            snapshot(*Array(105) { item(it.toLong() + 1) }),
            1,
        )
        repository.persistSnapshot(
            snapshot(
                item(999),
                coverage = ScanCoverage.PARTIAL_USER_SELECTED,
                batchKey = "directory:other",
            ),
            2,
        )

        val first = repository.loadPage(SourcePageRequest(BATCH, null, 100))
        val second = repository.loadPage(
            SourcePageRequest(BATCH, first.nextCursor?.fileId, 100),
        )

        assertEquals(100, first.items.size)
        assertEquals(5, second.items.size)
        assertTrue(first.nextCursor != null)
        assertNull(second.nextCursor)
        assertTrue(first.items.last().fileId < second.items.first().fileId)
        assertTrue((first.items + second.items).all { it.batchKey == BATCH })
    }

    @Test
    fun `batch summaries are bounded and count current memberships`() = runBlocking {
        repository.persistSnapshot(snapshot(item(1), item(2)), 1)
        repository.persistSnapshot(
            snapshot(
                item(999),
                coverage = ScanCoverage.PARTIAL_USER_SELECTED,
                batchKey = "directory:other",
            ),
            2,
        )

        val summaries = repository.loadBatchSummaries()

        assertEquals(
            listOf("directory:other" to 1, BATCH to 2),
            summaries.map { it.batchKey to it.imageCount },
        )
    }

    private fun snapshot(
        vararg items: DiscoveredSourceItem,
        coverage: ScanCoverage = ScanCoverage.FULL,
        batchKey: String = BATCH,
    ) = SourceDiscoverySnapshot(
        batches = listOf(
            DiscoveredSourceBatch(
                stableKey = batchKey,
                displayName = "set",
                kind = SourceBatchKind.DIRECTORY,
                items = items.toList(),
                isMonitored = true,
                canOrganizeWholeBatch = coverage == ScanCoverage.FULL,
            ),
        ),
        coverage = coverage,
        canOrganizeWholeBatch = coverage == ScanCoverage.FULL,
        canImportExplicitSelection = true,
    )

    private fun item(
        id: Long,
        sizeBytes: Long = 100,
        modifiedAt: Long = 2000,
    ) = DiscoveredSourceItem(
        mediaId = id,
        ref = SourceFileRef("mediastore:image:$id"),
        relativePath = "Download/set",
        displayName = "$id.jpg",
        sizeBytes = sizeBytes,
        modifiedAtEpochMillis = modifiedAt,
        mimeType = "image/jpeg",
        subgroupRelativePath = null,
    )

    private fun importRecord(
        sourceFileId: String,
        sourceLocator: String,
        state: String,
        importId: String = state,
    ) = ImportRecordEntity(
        importId = importId,
        sourceFileId = sourceFileId,
        targetFileId = "target-$importId",
        imageId = "image-$importId",
        disposition = "COPY_KEEP_SOURCE",
        sourceLocator = sourceLocator,
        sha256 = "sha",
        state = state,
        verifiedAtEpochMillis = 1,
    )

    companion object {
        private const val BATCH = "directory:set"
    }
}
