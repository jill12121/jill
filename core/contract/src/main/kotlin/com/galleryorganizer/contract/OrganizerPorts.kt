package com.galleryorganizer.contract

import kotlinx.coroutines.flow.Flow

sealed interface FsResult<out T> {
    data class Success<T>(val value: T) : FsResult<T>
    data class Failure(val failure: StorageFailure) : FsResult<Nothing>
}

sealed interface StorageFailure {
    val retryable: Boolean

    data object AccessDenied : StorageFailure {
        override val retryable = false
    }

    data object NotFound : StorageFailure {
        override val retryable = false
    }

    data object Conflict : StorageFailure {
        override val retryable = false
    }

    data object InsufficientSpace : StorageFailure {
        override val retryable = true
    }

    data class ProviderUnavailable(
        override val retryable: Boolean = true,
    ) : StorageFailure
}

@JvmInline
value class SourceFileRef(val stableKey: String)

@JvmInline
value class RelativePath(val value: String)

@JvmInline
value class ScanScope(val value: String)

@JvmInline
value class ScanCursor(val value: String)

@JvmInline
value class Sha256(val value: String)

@JvmInline
value class OperationId(val value: String)

data class SourceFile(
    val ref: SourceFileRef,
    val displayName: String,
    val sizeBytes: Long,
)

data class SourceBatch(
    val files: List<SourceFile>,
    val nextCursor: ScanCursor?,
)

data class FileFingerprint(
    val sizeBytes: Long,
    val modifiedAtEpochMillis: Long,
    val mimeType: String?,
)

data class ByteChunk(val bytes: ByteArray)

interface ByteSource {
    suspend fun readChunk(maxBytes: Int): FsResult<ByteChunk?>
    suspend fun close(): FsResult<Unit>
}

interface DigestSink {
    suspend fun update(chunk: ByteChunk): FsResult<Unit>
    suspend fun finish(): FsResult<Sha256>
}

data class WorkspaceCapabilities(
    val supportsPartRename: Boolean,
    val supportsReadBack: Boolean,
)

data class PartInfo(
    val path: RelativePath,
    val sizeBytes: Long,
)

data class FinalFile(
    val path: RelativePath,
    val sizeBytes: Long,
)

data class TrashEntry(val id: String)

data class DeleteAuthorizationRequest(val requestId: String)

data class ImportManifest(
    val operationId: OperationId,
    val content: String,
)

data class RecoveryManifest(
    val operationId: OperationId,
    val content: String,
)

/**
 * Source access is read-only. Android, SAF, and MediaStore adapters stay out
 * of the codebase until their device gates are approved.
 */
interface SourceReader {
    suspend fun stat(ref: SourceFileRef): FsResult<FileFingerprint>
    fun enumerate(scope: ScanScope, cursor: ScanCursor?): Flow<FsResult<SourceBatch>>
    suspend fun open(ref: SourceFileRef): FsResult<ByteSource>
}

interface WorkspaceGateway {
    suspend fun capabilities(): FsResult<WorkspaceCapabilities>
    suspend fun ensureDirectory(path: RelativePath): FsResult<Unit>
    suspend fun writePart(
        target: RelativePath,
        source: ByteSource,
        digest: DigestSink,
    ): FsResult<PartInfo>

    suspend fun promotePart(part: PartInfo): FsResult<FinalFile>
    suspend fun readBackDigest(file: FinalFile): FsResult<Sha256>
    suspend fun deletePart(part: PartInfo): FsResult<Unit>
    suspend fun moveToTrash(file: FinalFile, target: RelativePath): FsResult<FinalFile>
    suspend fun restoreFromTrash(file: FinalFile, target: RelativePath): FsResult<FinalFile>
}

interface PermanentDeleter {
    suspend fun deleteTrash(entry: TrashEntry): FsResult<Unit>
    suspend fun requestSourceCleanupAuthorization(
        refs: List<SourceFileRef>,
    ): FsResult<DeleteAuthorizationRequest>
}

interface ManifestStore {
    suspend fun writeImporting(manifest: ImportManifest): FsResult<Unit>
    suspend fun commit(operationId: OperationId): FsResult<Unit>
    suspend fun loadRecovery(operationId: OperationId): FsResult<RecoveryManifest?>
}
