package com.galleryorganizer.data.room

import android.content.Context
import com.galleryorganizer.domain.IMPORTED_STATES
import com.galleryorganizer.domain.PersistedSourceBatch
import com.galleryorganizer.domain.PersistedSourceBatchSummary
import com.galleryorganizer.domain.PersistedSourceItem
import com.galleryorganizer.domain.PersistedSourcePage
import com.galleryorganizer.domain.ScanCoverage
import com.galleryorganizer.domain.SourceDecision
import com.galleryorganizer.domain.SourceDecisionValue
import com.galleryorganizer.domain.SourceDiscoverySnapshot
import com.galleryorganizer.domain.SourceMetadataRepository
import com.galleryorganizer.domain.SourcePageRequest
import com.galleryorganizer.domain.sourceKeyFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun createSourceMetadataRepository(context: Context): SourceMetadataRepository {
    val database = organizerMetadataDatabase(context)
    return RoomSourceMetadataRepository(database.metadataDao())
}

class RoomSourceMetadataRepository(
    private val dao: OrganizerMetadataDao,
) : SourceMetadataRepository {
    override suspend fun persistSnapshot(
        snapshot: SourceDiscoverySnapshot,
        scanGeneration: Long,
    ) = withContext(Dispatchers.IO) {
        require(scanGeneration > 0)
        val items = snapshot.batches.flatMap { batch ->
            batch.items.map { batch.stableKey to it }
        }
        dao.persistSourceSnapshot(
            fileFacts = items.map { (_, item) ->
                FileFactEntity(
                    fileId = item.ref.stableKey,
                    kind = "DOWNLOAD",
                    rootId = "MEDIASTORE",
                    relativePath = "${item.relativePath.trimEnd('/')}/${item.displayName}",
                    displayName = item.displayName,
                    sizeBytes = item.sizeBytes,
                    mtimeNormalized = item.modifiedAtEpochMillis,
                    mtimePrecision = "SECONDS",
                    mimeType = item.mimeType,
                    decodeState = "NOT_CHECKED",
                    scanGeneration = scanGeneration,
                    fingerprintMethod = "SIZE_MTIME",
                    quickFingerprint = null,
                    sha256 = null,
                    contentKey = item.ref.stableKey,
                )
            },
            batchMembers = items.map { (batchKey, item) ->
                BatchMemberEntity(batchKey, item.ref.stableKey)
            },
            replaceCompletedSnapshot = snapshot.coverage == ScanCoverage.FULL &&
                snapshot.canOrganizeWholeBatch,
        )
    }

    override suspend fun loadBatches(
        batchKeys: Set<String>,
    ): List<PersistedSourceBatch> = withContext(Dispatchers.IO) {
        if (batchKeys.isEmpty()) return@withContext emptyList()
        dao.findBatchFileFacts(batchKeys)
            .groupBy(BatchFileFactRow::batchId)
            .map { (batchKey, rows) ->
                PersistedSourceBatch(
                    stableKey = batchKey,
                    items = rows.map { row ->
                        val path = row.relativePath.substringBeforeLast('/', "")
                        val sourceKey = sourceKeyFor(
                            path,
                            row.displayName,
                            row.sizeBytes,
                            row.mtimeNormalized,
                        )
                        PersistedSourceItem(
                            batchKey = batchKey,
                            fileId = row.fileId,
                            sourceKey = sourceKey,
                            displayName = row.displayName,
                            scanGeneration = row.scanGeneration,
                            sizeBytes = row.sizeBytes,
                            modifiedAtEpochMillis = row.mtimeNormalized,
                            mimeType = row.mimeType,
                            decision = dao.findSourceDecision(sourceKey)?.decision
                                ?.let(SourceDecisionValue::valueOf),
                            imported = dao.findImportsBySourceFileId(row.fileId).any {
                                it.sourceLocator == sourceKey &&
                                    it.state in IMPORTED_STATES.map(Enum<*>::name)
                            },
                        )
                    },
                )
            }
    }

    override suspend fun loadPage(
        request: SourcePageRequest,
    ): PersistedSourcePage = withContext(Dispatchers.IO) {
        val items = dao.findBatchFileFactsPage(
            batchId = request.batchKey,
            afterFileId = request.afterFileId,
            limit = request.limit + 1,
        ).map(::toPersistedSourceItem)
        PersistedSourcePage.from(items, request.limit)
    }

    override suspend fun loadBatchSummaries(): List<PersistedSourceBatchSummary> =
        withContext(Dispatchers.IO) {
            dao.findBatchSummaries().map {
                PersistedSourceBatchSummary(it.batchKey, it.imageCount)
            }
        }

    override suspend fun saveDecision(decision: SourceDecision) = withContext(Dispatchers.IO) {
        require(decision.sourceKey.isNotBlank() && decision.updatedAtEpochMillis > 0)
        dao.saveSourceDecision(
            SourceDecisionEntity(
                decision.sourceKey,
                decision.decision.name,
                decision.updatedAtEpochMillis,
            ),
        )
    }

    private fun toPersistedSourceItem(row: BatchFileFactRow): PersistedSourceItem {
        val path = row.relativePath.substringBeforeLast('/', "")
        val sourceKey = sourceKeyFor(
            path,
            row.displayName,
            row.sizeBytes,
            row.mtimeNormalized,
        )
        return PersistedSourceItem(
            batchKey = row.batchId,
            fileId = row.fileId,
            sourceKey = sourceKey,
            displayName = row.displayName,
            scanGeneration = row.scanGeneration,
            sizeBytes = row.sizeBytes,
            modifiedAtEpochMillis = row.mtimeNormalized,
            mimeType = row.mimeType,
            decision = dao.findSourceDecision(sourceKey)?.decision
                ?.let(SourceDecisionValue::valueOf),
            imported = dao.findImportsBySourceFileId(row.fileId).any {
                it.sourceLocator == sourceKey &&
                    it.state in IMPORTED_STATES.map(Enum<*>::name)
            },
        )
    }
}
