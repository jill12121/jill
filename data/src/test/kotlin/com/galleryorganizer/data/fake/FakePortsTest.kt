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
        val path = RelativePath("album/image.part")
        val digest = Sha256("digest-6")
        assertIs<FsResult.Success<Unit>>(workspace.ensureDirectory(RelativePath("album")))
        val part = assertIs<FsResult.Success<PartInfo>>(
            workspace.writePart(path, FakeByteSource("abcdef".encodeToByteArray()), FakeDigestSink(digest)),
        ).value
        assertNull(workspace.finalBytes(path))
        val final = assertIs<FsResult.Success<FinalFile>>(workspace.promotePart(part)).value
        assertContentEquals("abcdef".encodeToByteArray(), workspace.finalBytes(path))
        assertEquals(FsResult.Success(digest), workspace.readBackDigest(final))
        val trashed = assertIs<FsResult.Success<FinalFile>>(
            workspace.moveToTrash(final, RelativePath("trash/image"))).value
        assertNull(workspace.finalBytes(path))
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
}
