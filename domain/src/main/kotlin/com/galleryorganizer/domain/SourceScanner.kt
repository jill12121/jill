package com.galleryorganizer.domain

import com.galleryorganizer.contract.FsResult
import com.galleryorganizer.contract.SourceDiscoveryGateway
import com.galleryorganizer.contract.SourceMediaMetadata
import com.galleryorganizer.contract.StorageFailure

data class SourceScanRequest(
    val rootRelativePath: String,
    val pageSize: Int = 500,
    val config: SourceDiscoveryConfig,
)

sealed interface SourceScanResult {
    data class Completed(
        val snapshot: SourceDiscoverySnapshot,
        val pageCount: Int,
        val mediaCount: Int,
    ) : SourceScanResult

    data class Failed(
        val failure: DomainFailure.Storage,
    ) : SourceScanResult
}

class DefaultScanner(
    private val gateway: SourceDiscoveryGateway,
) : Scanner {
    override suspend fun scan(request: SourceScanRequest): SourceScanResult {
        if (request.rootRelativePath.isBlank() || request.pageSize !in 1..MAX_PAGE_SIZE) {
            return invalidProviderContract()
        }
        val media = mutableListOf<SourceMediaMetadata>()
        var afterId: Long? = null
        var pageCount = 0
        while (true) {
            if (pageCount >= MAX_PAGE_COUNT) return invalidProviderContract()
            val page = when (
                val result = gateway.discoverPage(
                    request.rootRelativePath,
                    afterId,
                    request.pageSize,
                )
            ) {
                is FsResult.Success -> result.value
                is FsResult.Failure -> return SourceScanResult.Failed(
                    DomainFailure.Storage(StorageOperation.SOURCE_ENUMERATION, result.failure),
                )
            }
            pageCount++
            val startId = afterId ?: 0L
            val ids = page.media.map(SourceMediaMetadata::mediaId)
            val strictlyIncreasing = ids.zipWithNext().all { (left, right) -> right > left }
            val next = page.nextAfterId
            val valid = page.media.size <= request.pageSize &&
                ids.all { it > startId } &&
                strictlyIncreasing &&
                if (next == null) {
                    page.media.size < request.pageSize
                } else {
                    page.media.size == request.pageSize &&
                        page.media.lastOrNull()?.mediaId == next &&
                        next > startId
                }
            if (!valid) return invalidProviderContract()
            media += page.media
            if (next == null) break
            afterId = next
        }
        val snapshot = SourceDiscoveryPolicy.build(
            media,
            request.config.copy(completed = true),
        )
        return SourceScanResult.Completed(snapshot, pageCount, media.size)
    }

    private fun invalidProviderContract() = SourceScanResult.Failed(
        DomainFailure.Storage(
            StorageOperation.SOURCE_ENUMERATION,
            StorageFailure.ProviderUnavailable(retryable = false),
        ),
    )

    companion object {
        const val MAX_PAGE_SIZE = 500
        const val MAX_PAGE_COUNT = 1_000
    }
}

fun scanCoverageFor(
    apiLevel: Int,
    granted: Set<MediaReadGrant>,
): ScanCoverage {
    permissionMatrixFor(apiLevel)
    return when {
        apiLevel <= 32 && MediaReadGrant.READ_EXTERNAL_STORAGE in granted -> ScanCoverage.FULL
        apiLevel >= 33 && MediaReadGrant.READ_MEDIA_IMAGES in granted -> ScanCoverage.FULL
        apiLevel >= 34 && MediaReadGrant.VISUAL_USER_SELECTED in granted ->
            ScanCoverage.PARTIAL_USER_SELECTED
        else -> ScanCoverage.UNKNOWN
    }
}
