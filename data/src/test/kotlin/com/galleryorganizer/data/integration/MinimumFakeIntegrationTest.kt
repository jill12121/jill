package com.galleryorganizer.data.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.galleryorganizer.contract.*
import com.galleryorganizer.data.fake.*
import com.galleryorganizer.data.room.*
import com.galleryorganizer.domain.*
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class MinimumFakeIntegrationTest {
    private lateinit var database: OrganizerMetadataDatabase
    private lateinit var dao: OrganizerMetadataDao
    private lateinit var harness: MinimumFakeIntegrationHarness

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrganizerMetadataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.metadataDao()
        harness = MinimumFakeIntegrationHarness(database, dao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `source decision only applies to its exact fingerprint key`() {
        val original = sourceKey(size = 100, modified = 200)
        dao.insertSourceDecision(SourceDecisionEntity(original, "KEEP", 1))

        assertEquals("KEEP", harness.findSourceDecision(original)?.decision)
        listOf(sourceKey(size = 101, modified = 200), sourceKey(size = 100, modified = 201))
            .forEach { changed ->
                assertFalse(SourceDecision(original, SourceDecisionValue.KEEP, 1).appliesTo(changed))
                assertNull(harness.findSourceDecision(changed))
                assertNotEquals(original, changed)
            }
        assertEquals("KEEP", harness.findSourceDecision(original)?.decision)
    }

    @Test
    fun `committed import excludes source and target but not unrelated file`() {
        dao.insertFileFact(fileFact("source-file", "inbox/source.jpg"))
        dao.insertFileFact(fileFact("target-file", "album/target.jpg"))
        dao.insertFileFact(fileFact("other-file", "inbox/other.jpg"))
        dao.insertImportRecord(importRecord())

        assertEquals(setOf("source-file", "target-file"), harness.importedLinkageIds())
        assertFalse(harness.isOrdinaryDuplicateCandidate("source-file"))
        assertFalse(harness.isOrdinaryDuplicateCandidate("target-file"))
        assertTrue(harness.isOrdinaryDuplicateCandidate("other-file"))
        assertNotEquals(
            dao.findFileFact("source-file")?.relativePath,
            dao.findFileFact("target-file")?.relativePath,
        )
    }

    @Test
    fun `source access denial maps to partial access without forging full scan`() = runTest {
        val selected = SourceFileRef("visible-1")
        val reader = FakeSourceReader(emptyList(), enumerationFailure = StorageFailure.AccessDenied)
        val scan = PartialScanSnapshot(setOf(selected))

        val result = reader.enumerate(ScanScope("selected"), null).single()
        scan.observe(result)

        assertEquals(
            DomainErrorCode.PARTIAL_MEDIA_ACCESS,
            DomainFailure.Storage(
                StorageOperation.SOURCE_ENUMERATION,
                assertIs<FsResult.Failure>(result).failure,
            ).userActionCode(),
        )
        assertEquals(setOf(selected), scan.visibleRefs)
        assertFalse(scan.completed)
    }

    @Test
    fun `insufficient workspace prevents final manifest and committed metadata`() = runTest {
        val partPath = RelativePath("album/image.jpg.part")
        val finalPath = RelativePath("album/image.jpg")
        val operationId = OperationId("storage-full")
        val workspace = FakeWorkspaceGateway(writeFailure = StorageFailure.InsufficientSpace)
        val manifest = FakeManifestStore()

        val result = workspace.writePart(
            partPath,
            FakeByteSource("content".encodeToByteArray()),
            FakeDigestSink(Sha256("digest")),
        )

        assertEquals(DomainErrorCode.STORAGE_FULL, result.userActionCode(StorageOperation.WORKSPACE_WRITE))
        assertNull(workspace.partBytes(partPath))
        assertNull(workspace.finalBytes(finalPath))
        assertEquals(FakeManifestState.NONE, manifest.state(operationId))
        assertNull(dao.findImportRecord("import-1"))
    }

    @Test
    fun `occupied target states preserve source and reject committed metadata`() = runTest {
        listOf(OccupiedState.PART, OccupiedState.FINAL, OccupiedState.TRASH).forEach { state ->
            val workspace = FakeWorkspaceGateway()
            val target = RelativePath("album/occupied-${state.name.lowercase()}.jpg")
            val occupied = "occupied".encodeToByteArray()
            harness.occupy(workspace, target, state, occupied)
            val sourcePath = RelativePath("album/source-${state.name.lowercase()}.part")
            val source = harness.writePart(workspace, sourcePath, "source".encodeToByteArray())

            val result = workspace.promotePart(source, target)

            assertEquals(DomainErrorCode.TARGET_CONFLICT, result.userActionCode(StorageOperation.WORKSPACE_WRITE))
            assertContentEquals("source".encodeToByteArray(), workspace.partBytes(sourcePath))
            assertContentEquals(occupied, harness.occupiedBytes(workspace, target, state))
            assertNull(dao.findImportRecord("import-${state.name}"))
        }
    }

    @Test
    fun `cleanup denial preserves committed archive metadata and source`() = runTest {
        dao.insertFileFact(fileFact("source-file", "inbox/source.jpg"))
        dao.insertCommittedImportMetadata(
            ImageAssetEntity("image-1", 1, "operation-1"),
            fileFact("target-file", "album/target.jpg"),
            AssetFileEntity("image-1", "target-file", "ALBUM_COPY"),
            AlbumMembershipEntity("album-1", "image-1", 0),
            importRecord(),
        )
        val deleter = FakePermanentDeleter(authorizationFailure = StorageFailure.AccessDenied)

        val result = deleter.requestSourceCleanupAuthorization(listOf(SourceFileRef("source-file")))

        assertEquals(
            DomainErrorCode.SOURCE_DELETE_AUTH_DENIED,
            result.userActionCode(StorageOperation.SOURCE_CLEANUP_AUTH),
        )
        assertNotNull(dao.findImageAsset("image-1"))
        assertNotNull(dao.findFileFact("source-file"))
        assertNotNull(dao.findFileFact("target-file"))
        assertNotNull(dao.findAssetFile("image-1", "target-file"))
        assertNotNull(dao.findAlbumMembership("album-1", "image-1"))
        assertEquals("COMMITTED", dao.findImportRecord("import-1")?.state)
        assertTrue(deleter.deletedTrashEntries.isEmpty())
    }

    @Test
    fun `partial copy interruption never creates a completed state`() = runTest {
        val operationId = OperationId("interrupted-copy")
        val partPath = RelativePath("album/interrupted.jpg.part")
        val finalPath = RelativePath("album/interrupted.jpg")
        val log = FakeOperationLog()
        val workspace = FakeWorkspaceGateway(
            FaultInjector(FaultPlan(1, FaultMode.PARTIAL_WRITE), log),
        )
        val manifest = FakeManifestStore()

        assertFailsWith<InjectedPartialWriteException> {
            workspace.writePart(
                partPath,
                FakeByteSource("abcdef".encodeToByteArray()),
                FakeDigestSink(Sha256("digest")),
            )
        }

        assertContentEquals("abc".encodeToByteArray(), workspace.partBytes(partPath))
        assertNull(workspace.finalBytes(finalPath))
        assertEquals(FakeManifestState.NONE, manifest.state(operationId))
        assertNull(dao.findImportRecord("import-1"))
        assertEquals(FakeOperationOutcome.PARTIAL_WRITE, log.records.single().outcome)
    }

    @Test
    fun `pre manifest interruption produces a pure in memory recovery snapshot`() = runTest {
        val operationId = OperationId("pre-manifest")
        val partPath = RelativePath("album/recovery.jpg.part")
        val workspace = FakeWorkspaceGateway()
        val manifest = FakeManifestStore()
        val journal = InMemoryJournalHarness()
        journal.start(operationId)
        journal.completeStep(operationId, 0, "COPY_PART")
        harness.writePart(workspace, partPath, "recoverable".encodeToByteArray())

        val snapshot = recoverySnapshot(operationId, journal, workspace, manifest, partPath)

        assertEquals(operationId, snapshot.operationId)
        assertEquals(InMemoryJournalState.EXECUTING, journal.snapshot(operationId).state)
        assertEquals(1, snapshot.watermark)
        assertEquals(listOf(0), snapshot.completedSteps.map { it.index })
        assertTrue(snapshot.partPresent)
        assertFalse(snapshot.finalPresent)
        assertEquals(FakeManifestState.NONE, snapshot.manifestState)
        assertFalse(snapshot.diskJournalCreated)
        assertFalse(snapshot.diskManifestCreated)
    }

    @Test
    fun `in memory journal preserves watermark and completed steps across process kill`() = runTest {
        val operationId = OperationId("journal-kill")
        val log = FakeOperationLog()
        val journal = InMemoryJournalHarness(
            FaultInjector(FaultPlan(3, FaultMode.SIMULATED_PROCESS_KILL), log),
        )

        journal.start(operationId)
        journal.completeStep(operationId, 0, "COPY_PART")
        assertFailsWith<IllegalArgumentException> {
            journal.completeStep(operationId, 0, "COPY_PART")
        }
        assertFailsWith<SimulatedProcessKillException> {
            journal.completeStep(operationId, 1, "PROMOTE_PART")
        }

        val snapshot = journal.snapshot(operationId)
        assertEquals(InMemoryJournalState.EXECUTING, snapshot.state)
        assertEquals(1, snapshot.watermark)
        assertEquals(listOf(0), snapshot.completedSteps.map { it.index })
        assertFalse(snapshot.diskJournalCreated)
        assertEquals(FakeOperationOutcome.PROCESS_KILLED, log.records.last().outcome)
    }

    private fun sourceKey(size: Long, modified: Long) =
        "DOWNLOAD:root-1:inbox/image.jpg:$size:$modified"

    private fun fileFact(fileId: String, path: String) = FileFactEntity(
        fileId, "DOWNLOAD", "root-1", path, path.substringAfterLast('/'),
        100, 200, "MILLIS", "image/jpeg", "SUPPORTED", 1, "H64", "quick", null, fileId,
    )

    private fun importRecord() = ImportRecordEntity(
        "import-1", "source-file", "target-file", "image-1", "COPY_KEEP_SOURCE",
        "locator", "sha256", "COMMITTED", 1,
    )

    private fun DomainFailure.userActionCode() =
        (resolve() as FailureResolution.UserAction).code

    private fun FsResult<*>.userActionCode(operation: StorageOperation) =
        DomainFailure.Storage(operation, assertIs<FsResult.Failure>(this).failure).userActionCode()
}
