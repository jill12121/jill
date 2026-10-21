package com.galleryorganizer.data.fake

import com.galleryorganizer.contract.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class FakeSourceEntry(
    val ref: SourceFileRef,
    val displayName: String,
    val fingerprint: FileFingerprint,
    val bytes: ByteArray,
    val statFailure: StorageFailure? = null,
    val openFailure: StorageFailure? = null,
)

class FakeByteSource(
    bytes: ByteArray,
    private val chunkSize: Int = bytes.size.coerceAtLeast(1),
) : ByteSource {
    private val content = bytes.copyOf()
    private var offset = 0
    override suspend fun readChunk(maxBytes: Int): FsResult<ByteChunk?> {
        require(maxBytes > 0)
        if (offset == content.size) return FsResult.Success(null)
        val end = minOf(content.size, offset + minOf(maxBytes, chunkSize))
        return FsResult.Success(ByteChunk(content.copyOfRange(offset, end))).also { offset = end }
    }
    override suspend fun close() = FsResult.Success(Unit)
    suspend fun readAll(): ByteArray {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val chunk = (readChunk(Int.MAX_VALUE) as FsResult.Success).value ?: break
            bytes += chunk.bytes.toList()
        }
        return bytes.toByteArray()
    }
}

class FakeDigestSink(private val digest: Sha256) : DigestSink {
    val chunks = mutableListOf<ByteChunk>()
    override suspend fun update(chunk: ByteChunk): FsResult<Unit> =
        FsResult.Success(Unit).also { chunks += ByteChunk(chunk.bytes.copyOf()) }
    override suspend fun finish(): FsResult<Sha256> = FsResult.Success(digest)
}

class FakeSourceReader(
    entries: List<FakeSourceEntry>,
    private val enumerationFailure: StorageFailure? = null,
    private val faultInjector: FaultInjector = FaultInjector(),
) : SourceReader {
    private val entriesByRef = entries.associateBy(FakeSourceEntry::ref)
    override suspend fun stat(ref: SourceFileRef): FsResult<FileFingerprint> =
        faultInjector.execute("source.stat") {
            val entry = entriesByRef[ref] ?: return@execute FsResult.Failure(StorageFailure.NotFound)
            entry.statFailure?.let { FsResult.Failure(it) } ?: FsResult.Success(entry.fingerprint)
        }
    override fun enumerate(scope: ScanScope, cursor: ScanCursor?): Flow<FsResult<SourceBatch>> = flow {
        emit(faultInjector.execute("source.enumerate") {
            enumerationFailure?.let { FsResult.Failure(it) } ?: FsResult.Success(
                SourceBatch(
                    entriesByRef.values.map {
                        SourceFile(it.ref, it.displayName, it.fingerprint.sizeBytes)
                    },
                    nextCursor = null,
                ),
            )
        })
    }
    override suspend fun open(ref: SourceFileRef): FsResult<ByteSource> =
        faultInjector.execute("source.open") {
            val entry = entriesByRef[ref] ?: return@execute FsResult.Failure(StorageFailure.NotFound)
            entry.openFailure?.let { FsResult.Failure(it) } ?: FsResult.Success(FakeByteSource(entry.bytes))
        }
}

private data class StoredFile(val bytes: ByteArray, val digest: Sha256?)

class FakeWorkspaceGateway(
    private val faultInjector: FaultInjector = FaultInjector(),
    private val workspaceCapabilities: WorkspaceCapabilities = WorkspaceCapabilities(true, true),
    private val writeFailure: StorageFailure? = null,
) : WorkspaceGateway {
    private val mutableDirectories = linkedSetOf<RelativePath>()
    private val parts = linkedMapOf<RelativePath, StoredFile>()
    private val finals = linkedMapOf<RelativePath, StoredFile>()
    private val trash = linkedMapOf<RelativePath, StoredFile>()
    val directories get() = mutableDirectories.toSet()
    fun partBytes(path: RelativePath): ByteArray? = parts[path]?.bytes?.copyOf()
    fun finalBytes(path: RelativePath): ByteArray? = finals[path]?.bytes?.copyOf()
    fun trashBytes(path: RelativePath): ByteArray? = trash[path]?.bytes?.copyOf()
    private fun isOccupied(path: RelativePath) =
        path in parts || path in finals || path in trash

    override suspend fun capabilities() =
        faultInjector.execute("workspace.capabilities") { FsResult.Success(workspaceCapabilities) }
    override suspend fun ensureDirectory(path: RelativePath) =
        faultInjector.execute("workspace.ensureDirectory") {
            mutableDirectories += path
            FsResult.Success(Unit)
        }
    override suspend fun writePart(
        target: RelativePath,
        source: ByteSource,
        digest: DigestSink,
    ): FsResult<PartInfo> {
        if (isOccupied(target)) return FsResult.Failure(StorageFailure.Conflict)
        return faultInjector.execute(
            name = "workspace.writePart",
            partialWrite = {
                val chunk = (source.readChunk(3) as? FsResult.Success)?.value
                if (chunk != null) {
                    digest.update(chunk)
                    parts[target] = StoredFile(chunk.bytes.copyOf(), null)
                }
            },
        ) {
            writeFailure?.let { return@execute FsResult.Failure(it) }
            val bytes = mutableListOf<Byte>()
            while (true) {
                when (val result = source.readChunk(64 * 1024)) {
                    is FsResult.Failure -> return@execute result
                    is FsResult.Success -> {
                        val chunk = result.value ?: break
                        digest.update(chunk).let { if (it is FsResult.Failure) return@execute it }
                        bytes += chunk.bytes.toList()
                    }
                }
            }
            val sha = when (val result = digest.finish()) {
                is FsResult.Failure -> return@execute result
                is FsResult.Success -> result.value
            }
            parts[target] = StoredFile(bytes.toByteArray(), sha)
            FsResult.Success(PartInfo(target, bytes.size.toLong()))
        }
    }

    override suspend fun promotePart(part: PartInfo, finalTarget: RelativePath): FsResult<FinalFile> {
        if (finalTarget.value.endsWith(".part") || isOccupied(finalTarget)) {
            return FsResult.Failure(StorageFailure.Conflict)
        }
        return faultInjector.execute("workspace.promotePart") {
            val stored = parts.remove(part.path) ?: return@execute FsResult.Failure(StorageFailure.NotFound)
            finals[finalTarget] = stored
            FsResult.Success(FinalFile(finalTarget, stored.bytes.size.toLong()))
        }
    }

    override suspend fun readBackDigest(file: FinalFile) =
        faultInjector.execute("workspace.readBackDigest") {
            finals[file.path]?.digest?.let { FsResult.Success(it) }
                ?: FsResult.Failure(StorageFailure.NotFound)
        }
    override suspend fun deletePart(part: PartInfo) =
        faultInjector.execute("workspace.deletePart") {
            if (parts.remove(part.path) == null) FsResult.Failure(StorageFailure.NotFound)
            else FsResult.Success(Unit)
        }
    override suspend fun moveToTrash(file: FinalFile, target: RelativePath) =
        if (isOccupied(target)) {
            FsResult.Failure(StorageFailure.Conflict)
        } else faultInjector.execute("workspace.moveToTrash") {
            val stored = finals.remove(file.path) ?: return@execute FsResult.Failure(StorageFailure.NotFound)
            trash[target] = stored
            FsResult.Success(FinalFile(target, stored.bytes.size.toLong()))
        }
    override suspend fun restoreFromTrash(file: FinalFile, target: RelativePath) =
        if (isOccupied(target)) {
            FsResult.Failure(StorageFailure.Conflict)
        } else faultInjector.execute("workspace.restoreFromTrash") {
            val stored = trash.remove(file.path) ?: return@execute FsResult.Failure(StorageFailure.NotFound)
            finals[target] = stored
            FsResult.Success(FinalFile(target, stored.bytes.size.toLong()))
        }
}

enum class FakeManifestState { NONE, PARTIAL_IMPORT, IMPORTING, COMMITTED }

class FakeManifestStore(
    private val faultInjector: FaultInjector = FaultInjector(),
) : ManifestStore {
    private val contents = mutableMapOf<OperationId, String>()
    private val states = mutableMapOf<OperationId, FakeManifestState>()
    fun state(operationId: OperationId) = states[operationId] ?: FakeManifestState.NONE
    override suspend fun writeImporting(manifest: ImportManifest): FsResult<Unit> =
        faultInjector.execute(
            name = "manifest.writeImporting",
            partialWrite = {
                contents[manifest.operationId] = manifest.content.take(manifest.content.length / 2)
                states[manifest.operationId] = FakeManifestState.PARTIAL_IMPORT
            },
        ) {
            contents[manifest.operationId] = manifest.content
            states[manifest.operationId] = FakeManifestState.IMPORTING
            FsResult.Success(Unit)
        }
    override suspend fun commit(operationId: OperationId) =
        faultInjector.execute("manifest.commit") {
            if (state(operationId) != FakeManifestState.IMPORTING) {
                FsResult.Failure(StorageFailure.NotFound)
            } else {
                states[operationId] = FakeManifestState.COMMITTED
                FsResult.Success(Unit)
            }
        }
    override suspend fun loadRecovery(operationId: OperationId): FsResult<RecoveryManifest?> =
        faultInjector.execute("manifest.loadRecovery") {
            FsResult.Success(contents[operationId]?.let { RecoveryManifest(operationId, it) })
        }
}

class FakePermanentDeleter(
    private val authorizationFailure: StorageFailure? = null,
    private val deletionFailure: StorageFailure? = null,
    private val faultInjector: FaultInjector = FaultInjector(),
) : PermanentDeleter {
    private val mutableAuthorizationRequests = mutableListOf<List<SourceFileRef>>()
    private val mutableDeleteRequests = mutableListOf<TrashEntry>()
    private val mutableDeletedEntries = mutableListOf<TrashEntry>()
    val authorizationRequests get() = mutableAuthorizationRequests.toList()
    val deleteRequests get() = mutableDeleteRequests.toList()
    val deletedTrashEntries get() = mutableDeletedEntries.toList()
    override suspend fun deleteTrash(entry: TrashEntry): FsResult<Unit> =
        faultInjector.execute(
            name = "deleter.deleteTrash",
            partialWrite = { mutableDeleteRequests += entry },
        ) {
            mutableDeleteRequests += entry
            deletionFailure?.let { FsResult.Failure(it) } ?: FsResult.Success(Unit).also {
                mutableDeletedEntries += entry
            }
        }
    override suspend fun requestSourceCleanupAuthorization(
        refs: List<SourceFileRef>,
    ): FsResult<DeleteAuthorizationRequest> =
        faultInjector.execute("deleter.requestAuthorization") {
            mutableAuthorizationRequests += refs.toList()
            authorizationFailure?.let { FsResult.Failure(it) }
                ?: FsResult.Success(DeleteAuthorizationRequest("auth-${mutableAuthorizationRequests.size}"))
        }
}
