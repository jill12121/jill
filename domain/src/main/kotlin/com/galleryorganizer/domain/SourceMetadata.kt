package com.galleryorganizer.domain

data class PersistedSourceItem(
    val batchKey: String,
    val fileId: String,
    val sourceKey: String,
    val displayName: String,
    val scanGeneration: Long,
    val sizeBytes: Long,
    val modifiedAtEpochMillis: Long,
    val mimeType: String?,
    val decision: SourceDecisionValue?,
    val imported: Boolean,
)

data class PersistedSourceBatch(
    val stableKey: String,
    val items: List<PersistedSourceItem>,
)

data class PersistedSourceBatchSummary(
    val batchKey: String,
    val imageCount: Int,
)

@JvmInline
value class SourcePageCursor(val fileId: String) {
    init {
        require(fileId.isNotBlank())
    }
}

data class SourcePageRequest(
    val batchKey: String,
    val afterFileId: String?,
    val limit: Int,
) {
    init {
        require(batchKey.isNotBlank())
        require(afterFileId == null || afterFileId.isNotBlank())
        require(limit in 1..MAX_SOURCE_PAGE_SIZE)
    }
}

data class PersistedSourcePage(
    val items: List<PersistedSourceItem>,
    val nextCursor: SourcePageCursor?,
) {
    companion object {
        fun from(
            fetchedItems: List<PersistedSourceItem>,
            requestedLimit: Int,
        ): PersistedSourcePage {
            require(requestedLimit in 1..MAX_SOURCE_PAGE_SIZE)
            val items = fetchedItems.take(requestedLimit)
            return PersistedSourcePage(
                items = items,
                nextCursor = if (fetchedItems.size > requestedLimit) {
                    SourcePageCursor(items.last().fileId)
                } else {
                    null
                },
            )
        }
    }
}

interface SourceMetadataRepository {
    suspend fun persistSnapshot(
        snapshot: SourceDiscoverySnapshot,
        scanGeneration: Long,
    )

    suspend fun loadBatches(batchKeys: Set<String>): List<PersistedSourceBatch>

    suspend fun loadBatchSummaries(): List<PersistedSourceBatchSummary>

    suspend fun loadPage(request: SourcePageRequest): PersistedSourcePage

    suspend fun saveDecision(decision: SourceDecision)
}

fun DiscoveredSourceItem.sourceKey(): String = sourceKeyFor(
    relativePath = relativePath,
    displayName = displayName,
    sizeBytes = sizeBytes,
    modifiedAtEpochMillis = modifiedAtEpochMillis,
)

fun sourceKeyFor(
    relativePath: String,
    displayName: String,
    sizeBytes: Long,
    modifiedAtEpochMillis: Long,
): String = listOf(
    SOURCE_KIND,
    SOURCE_ROOT_ID,
    "${relativePath.trimEnd('/')}/$displayName",
    sizeBytes,
    modifiedAtEpochMillis,
).joinToString(":")

internal const val SOURCE_KIND = "DOWNLOAD"
internal const val SOURCE_ROOT_ID = "MEDIASTORE"
const val MAX_SOURCE_PAGE_SIZE = 100

val IMPORTED_STATES = setOf(
    ImportRecordState.COMMITTED,
    ImportRecordState.COPIED_SOURCE_RETAINED,
    ImportRecordState.CLEANUP_PENDING,
    ImportRecordState.CLEANED,
    ImportRecordState.CLEANUP_DENIED,
)
