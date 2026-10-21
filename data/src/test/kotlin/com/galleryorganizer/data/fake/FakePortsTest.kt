package com.galleryorganizer.data.fake

import com.galleryorganizer.contract.*
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakePortsTest {
    @Test
    fun `source reader results are predictable and read only`() = runTest {
        val ref = SourceFileRef("source-1")
        val deniedRef = SourceFileRef("denied")
        val fingerprint = FileFingerprint(6, 100, "image/jpeg")
        val reader = FakeSourceReader(
            entries = listOf(
                FakeSourceEntry(ref, "a.jpg", fingerprint, "abcdef".encodeToByteArray()),
                FakeSourceEntry(deniedRef, "denied.jpg", fingerprint, byteArrayOf(),
                    StorageFailure.AccessDenied, StorageFailure.ProviderUnavailable()),
            ),
        )
        assertEquals(FsResult.Success(fingerprint), reader.stat(ref))
        val batch = assertIs<FsResult.Success<SourceBatch>>(reader.enumerate(ScanScope("all"), null).single())
        assertEquals(ref, batch.value.files.first { it.ref == ref }.ref)
        val source = assertIs<FsResult.Success<*>>(reader.open(ref)).value as FakeByteSource
        assertContentEquals("abcdef".encodeToByteArray(), source.readAll())
        assertEquals(FsResult.Failure(StorageFailure.NotFound), reader.stat(SourceFileRef("missing")))
        assertEquals(FsResult.Failure(StorageFailure.AccessDenied), reader.stat(deniedRef))
        assertEquals(FsResult.Failure(StorageFailure.ProviderUnavailable()), reader.open(deniedRef))
    }

    @Test
    fun `workspace and manifest expose only explicit completed state`() = runTest {
        val workspace = FakeWorkspaceGateway()
        val partPath = RelativePath("album/image.jpg.part")
        val finalPath = RelativePath("album/image.jpg")
        val digest = Sha256("digest-6")
        assertIs<FsResult.Success<Unit>>(workspace.ensureDirectory(RelativePath("album")))
        val part = assertIs<FsResult.Success<PartInfo>>(
            workspace.writePart(partPath, FakeByteSource("abcdef".encodeToByteArray()), FakeDigestSink(digest)),
        ).value
        assertNull(workspace.finalBytes(finalPath))
        val final = assertIs<FsResult.Success<FinalFile>>(
            workspace.promotePart(part, finalPath),
        ).value
        assertEquals(FinalFile(finalPath, 6), final)
        assertTrue(!final.path.value.endsWith(".part"))
        assertNull(workspace.partBytes(partPath))
        assertNull(workspace.finalBytes(partPath))
        assertContentEquals("abcdef".encodeToByteArray(), workspace.finalBytes(finalPath))
        assertEquals(FsResult.Success(digest), workspace.readBackDigest(final))
        val trashed = assertIs<FsResult.Success<FinalFile>>(
            workspace.moveToTrash(final, RelativePath("trash/image"))).value
        assertNull(workspace.finalBytes(finalPath))
        assertContentEquals("abcdef".encodeToByteArray(), workspace.trashBytes(trashed.path))
        val restored = assertIs<FsResult.Success<FinalFile>>(
            workspace.restoreFromTrash(trashed, RelativePath("album/restored"))).value
        assertContentEquals("abcdef".encodeToByteArray(), workspace.finalBytes(restored.path))
        val disposable = assertIs<FsResult.Success<PartInfo>>(workspace.writePart(
            RelativePath("album/delete.part"), FakeByteSource("x".encodeToByteArray()),
            FakeDigestSink(Sha256("digest-x")))).value
        assertIs<FsResult.Success<Unit>>(workspace.deletePart(disposable))
        assertNull(workspace.partBytes(disposable.path))
        val operationId = OperationId("op-1")
        val manifest = FakeManifestStore()
        manifest.writeImporting(ImportManifest(operationId, "manifest"))
        assertEquals(FakeManifestState.IMPORTING, manifest.state(operationId))
        val recovery = assertIs<FsResult.Success<RecoveryManifest?>>(manifest.loadRecovery(operationId))
        assertEquals("manifest", recovery.value?.content)
        manifest.commit(operationId)
        assertEquals(FakeManifestState.COMMITTED, manifest.state(operationId))
    }

    @Test
    fun `promotion rejects identical and part final targets without changing state`() = runTest {
        val workspace = FakeWorkspaceGateway()
        val partPath = RelativePath("Albums/album-1/img-1.jpg.part")
        val bytes = "source".encodeToByteArray()
        val part = writePart(workspace, partPath, bytes)

        assertEquals(
            FsResult.Failure(StorageFailure.Conflict),
            workspace.promotePart(part, partPath),
        )
        assertContentEquals(bytes, workspace.partBytes(partPath))
        assertNull(workspace.finalBytes(partPath))

        val invalidTarget = RelativePath("Albums/album-1/other.jpg.part")
        assertEquals(
            FsResult.Failure(StorageFailure.Conflict),
            workspace.promotePart(part, invalidTarget),
        )
        assertContentEquals(bytes, workspace.partBytes(partPath))
        assertNull(workspace.finalBytes(invalidTarget))
    }

    @Test
    fun `write part rejects occupied part final and trash paths without consuming source`() = runTest {
        val workspace = FakeWorkspaceGateway()
        val occupiedPart = RelativePath("album/occupied-part.jpg.part")
        val occupiedFinal = RelativePath("album/occupied-final.jpg")
        val occupiedTrash = RelativePath("trash/occupied.jpg")
        val existingBytes = "existing".encodeToByteArray()

        writePart(workspace, occupiedPart, existingBytes)
        createFinal(
            workspace,
            RelativePath("album/final-source.jpg.part"),
            occupiedFinal,
            existingBytes,
        )
        val trashSource = createFinal(
            workspace,
            RelativePath("album/trash-source.jpg.part"),
            RelativePath("album/trash-source.jpg"),
            existingBytes,
        )
        assertIs<FsResult.Success<FinalFile>>(workspace.moveToTrash(trashSource, occupiedTrash))

        listOf(occupiedPart, occupiedFinal, occupiedTrash).forEach { target ->
            val incoming = FakeByteSource("incoming".encodeToByteArray())
            assertEquals(
                FsResult.Failure(StorageFailure.Conflict),
                workspace.writePart(target, incoming, FakeDigestSink(Sha256("incoming"))),
            )
            assertContentEquals("incoming".encodeToByteArray(), incoming.readAll())
        }

        assertContentEquals(existingBytes, workspace.partBytes(occupiedPart))
        assertContentEquals(existingBytes, workspace.finalBytes(occupiedFinal))
        assertContentEquals(existingBytes, workspace.trashBytes(occupiedTrash))
    }

    @Test
    fun `promotion conflict preserves source part and existing final`() = runTest {
        val workspace = FakeWorkspaceGateway()
        val finalTarget = RelativePath("album/existing.jpg")
        val existingBytes = "existing-final".encodeToByteArray()
        createFinal(
            workspace,
            RelativePath("album/existing.jpg.part"),
            finalTarget,
            existingBytes,
        )
        val sourcePath = RelativePath("album/new.jpg.part")
        val sourceBytes = "new-part".encodeToByteArray()
        val sourcePart = writePart(workspace, sourcePath, sourceBytes)

        assertEquals(
            FsResult.Failure(StorageFailure.Conflict),
            workspace.promotePart(sourcePart, finalTarget),
        )
        assertContentEquals(sourceBytes, workspace.partBytes(sourcePath))
        assertContentEquals(existingBytes, workspace.finalBytes(finalTarget))
    }

    @Test
    fun `trash and restore conflicts preserve both source and target`() = runTest {
        val workspace = FakeWorkspaceGateway()
        val trashTarget = RelativePath("trash/occupied.jpg")
        val occupiedTrashBytes = "occupied-trash".encodeToByteArray()
        val occupiedTrashSource = createFinal(
            workspace,
            RelativePath("album/occupied-trash.jpg.part"),
            RelativePath("album/occupied-trash.jpg"),
            occupiedTrashBytes,
        )
        assertIs<FsResult.Success<FinalFile>>(
            workspace.moveToTrash(occupiedTrashSource, trashTarget),
        )

        val moveSourcePath = RelativePath("album/move-source.jpg")
        val moveSourceBytes = "move-source".encodeToByteArray()
        val moveSource = createFinal(
            workspace,
            RelativePath("album/move-source.jpg.part"),
            moveSourcePath,
            moveSourceBytes,
        )
        assertEquals(
            FsResult.Failure(StorageFailure.Conflict),
            workspace.moveToTrash(moveSource, trashTarget),
        )
        assertContentEquals(moveSourceBytes, workspace.finalBytes(moveSourcePath))
        assertContentEquals(occupiedTrashBytes, workspace.trashBytes(trashTarget))

        val restoreTarget = RelativePath("album/restore-target.jpg")
        val restoreTargetBytes = "existing-final".encodeToByteArray()
        createFinal(
            workspace,
            RelativePath("album/restore-target.jpg.part"),
            restoreTarget,
            restoreTargetBytes,
        )
        assertEquals(
            FsResult.Failure(StorageFailure.Conflict),
            workspace.restoreFromTrash(FinalFile(trashTarget, occupiedTrashBytes.size.toLong()), restoreTarget),
        )
        assertContentEquals(occupiedTrashBytes, workspace.trashBytes(trashTarget))
        assertContentEquals(restoreTargetBytes, workspace.finalBytes(restoreTarget))
    }

    @Test
    fun `deleter records denial without claiming source or trash deletion`() = runTest {
        val deleter = FakePermanentDeleter(authorizationFailure = StorageFailure.AccessDenied)
        val ref = SourceFileRef("source-1")
        assertEquals(FsResult.Failure(StorageFailure.AccessDenied),
            deleter.requestSourceCleanupAuthorization(listOf(ref)))
        assertEquals(listOf(listOf(ref)), deleter.authorizationRequests)
        assertTrue(deleter.deletedTrashEntries.isEmpty())
        assertIs<FsResult.Success<Unit>>(deleter.deleteTrash(TrashEntry("trash-1")))
        assertEquals(listOf(TrashEntry("trash-1")), deleter.deletedTrashEntries)
    }

    @Test
    fun `fault plans are deterministic and preserve partial state`() = runTest {
        suspend fun runThrow(): Pair<List<FakeOperationRecord>, Set<RelativePath>> {
            val log = FakeOperationLog()
            val workspace = FakeWorkspaceGateway(
                FaultInjector(FaultPlan(2, FaultMode.THROW), log))
            workspace.ensureDirectory(RelativePath("one"))
            assertFailsWith<InjectedFaultException> {
                workspace.ensureDirectory(RelativePath("two"))
            }
            return log.records to workspace.directories
        }
        val deterministic = runThrow()
        assertEquals(deterministic, runThrow())
        assertEquals(listOf(FakeOperationOutcome.COMPLETED, FakeOperationOutcome.THREW),
            deterministic.first.map(FakeOperationRecord::outcome))
        val partialWorkspace = FakeWorkspaceGateway(
            FaultInjector(FaultPlan(1, FaultMode.PARTIAL_WRITE)))
        val path = RelativePath("album/image.part")
        assertFailsWith<InjectedPartialWriteException> {
            partialWorkspace.writePart(
                path,
                FakeByteSource("abcdef".encodeToByteArray(), chunkSize = 3),
                FakeDigestSink(Sha256("unused")),
            )
        }
        assertContentEquals("abc".encodeToByteArray(), partialWorkspace.partBytes(path))
        assertNull(partialWorkspace.finalBytes(path))
        val partialManifest = FakeManifestStore(
            FaultInjector(FaultPlan(1, FaultMode.PARTIAL_WRITE)))
        val operationId = OperationId("partial-op")
        assertFailsWith<InjectedPartialWriteException> {
            partialManifest.writeImporting(ImportManifest(operationId, "manifest"))
        }
        assertEquals(FakeManifestState.PARTIAL_IMPORT, partialManifest.state(operationId))
    }

    @Test
    fun `returned failures use deterministic operation index and are not logged completed`() = runTest {
        val log = FakeOperationLog()
        val workspace = FakeWorkspaceGateway(
            FaultInjector(
                FaultPlan(2, FaultMode.ReturnFailure(StorageFailure.InsufficientSpace)),
                log,
            ),
        )
        assertIs<FsResult.Success<Unit>>(workspace.ensureDirectory(RelativePath("album")))

        val result = workspace.writePart(
            RelativePath("album/image.jpg.part"),
            FakeByteSource("content".encodeToByteArray()),
            FakeDigestSink(Sha256("digest")),
        )

        assertEquals(FsResult.Failure(StorageFailure.InsufficientSpace), result)
        assertEquals(
            listOf(FakeOperationOutcome.COMPLETED, FakeOperationOutcome.RETURNED_FAILURE),
            log.records.map(FakeOperationRecord::outcome),
        )
    }

    @Test
    fun `partial write retry reports conflict and preserves partial bytes`() = runTest {
        val workspace = FakeWorkspaceGateway(
            FaultInjector(FaultPlan(1, FaultMode.PARTIAL_WRITE)),
        )
        val partPath = RelativePath("album/image.jpg.part")
        assertFailsWith<InjectedPartialWriteException> {
            workspace.writePart(
                partPath,
                FakeByteSource("abcdef".encodeToByteArray(), chunkSize = 3),
                FakeDigestSink(Sha256("unused")),
            )
        }
        val retrySource = FakeByteSource("replacement".encodeToByteArray())

        assertEquals(
            FsResult.Failure(StorageFailure.Conflict),
            workspace.writePart(partPath, retrySource, FakeDigestSink(Sha256("replacement"))),
        )
        assertContentEquals("abc".encodeToByteArray(), workspace.partBytes(partPath))
        assertContentEquals("replacement".encodeToByteArray(), retrySource.readAll())
        assertNull(workspace.finalBytes(RelativePath("album/image.jpg")))
    }

    @Test
    fun `manifest faults never commit and simulated kill blocks later operations`() = runTest {
        val operationId = OperationId("op-1")
        val manifest = FakeManifestStore(FaultInjector(FaultPlan(2, FaultMode.THROW)))
        manifest.writeImporting(ImportManifest(operationId, "manifest"))
        assertFailsWith<InjectedFaultException> { manifest.commit(operationId) }
        assertEquals(FakeManifestState.IMPORTING, manifest.state(operationId))
        val log = FakeOperationLog()
        val reader = FakeSourceReader(
            entries = emptyList(),
            faultInjector = FaultInjector(FaultPlan(1, FaultMode.SIMULATED_PROCESS_KILL), log),
        )
        assertFailsWith<SimulatedProcessKillException> { reader.stat(SourceFileRef("one")) }
        assertFailsWith<SimulatedProcessKillException> { reader.stat(SourceFileRef("two")) }
        assertEquals(1, log.records.size)
        assertEquals(FakeOperationOutcome.PROCESS_KILLED, log.records.single().outcome)
    }

    private suspend fun writePart(
        workspace: FakeWorkspaceGateway,
        path: RelativePath,
        bytes: ByteArray,
    ): PartInfo = assertIs<FsResult.Success<PartInfo>>(
        workspace.writePart(path, FakeByteSource(bytes), FakeDigestSink(Sha256("digest-${path.value}"))),
    ).value

    private suspend fun createFinal(
        workspace: FakeWorkspaceGateway,
        partPath: RelativePath,
        finalPath: RelativePath,
        bytes: ByteArray,
    ): FinalFile = assertIs<FsResult.Success<FinalFile>>(
        workspace.promotePart(writePart(workspace, partPath, bytes), finalPath),
    ).value
}
