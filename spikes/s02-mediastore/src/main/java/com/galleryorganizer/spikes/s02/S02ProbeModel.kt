package com.galleryorganizer.spikes.s02

enum class MediaPermissionState {
    FULL,
    PARTIAL_USER_SELECTED,
    NOT_GRANTED,
    DENIED,
}

enum class CleanupAuthorizationState {
    NOT_RUN,
    PENDING,
    GRANTED_REMOVED,
    GRANTED_STILL_PRESENT,
    DENIED,
    REQUEST_FAILED,
}

enum class S02SingleDeviceOutcome { PASS, PARTIAL, PENDING, FAIL }

data class PageEvidence(
    val pageIndex: Int,
    val rowCount: Int,
    val firstId: Long,
    val lastId: Long,
    val pageMillis: Long,
    val duplicateCount: Int,
)

object PaginationEvidenceValidator {
    const val PAGE_SIZE = 500

    fun isValid(pages: List<PageEvidence>): Boolean {
        var previousLastId = 0L
        return pages.withIndex().all { (expectedIndex, page) ->
            val valid = page.pageIndex == expectedIndex &&
                page.rowCount in 1..PAGE_SIZE &&
                page.firstId > previousLastId &&
                page.lastId >= page.firstId &&
                page.duplicateCount == 0
            previousLastId = page.lastId
            valid
        }
    }
}

object CleanupAuthorizationChunker {
    const val MAX_BATCH_SIZE = 300

    fun chunk(ids: List<Long>): List<List<Long>> = ids.chunked(MAX_BATCH_SIZE)
}

data class S02ProbeResult(
    val permissionState: MediaPermissionState,
    val totalVisibleRows: Int,
    val pages: List<PageEvidence>,
    val cleanupState: CleanupAuthorizationState,
    val authorizationBatchSizes: List<Int>,
    val exceptionClass: String? = null,
) {
    val outcome: S02SingleDeviceOutcome
        get() = when {
            exceptionClass != null ||
                permissionState == MediaPermissionState.NOT_GRANTED ||
                permissionState == MediaPermissionState.DENIED ||
                totalVisibleRows == 0 ||
                !PaginationEvidenceValidator.isValid(pages) ||
                (
                    cleanupState == CleanupAuthorizationState.GRANTED_REMOVED &&
                        authorizationBatchSizes.isEmpty()
                    ) ||
                authorizationBatchSizes.any { it !in 1..CleanupAuthorizationChunker.MAX_BATCH_SIZE } ||
                cleanupState == CleanupAuthorizationState.GRANTED_STILL_PRESENT ||
                cleanupState == CleanupAuthorizationState.DENIED ||
                cleanupState == CleanupAuthorizationState.REQUEST_FAILED ->
                S02SingleDeviceOutcome.FAIL

            cleanupState == CleanupAuthorizationState.NOT_RUN ||
                cleanupState == CleanupAuthorizationState.PENDING ->
                S02SingleDeviceOutcome.PENDING

            permissionState == MediaPermissionState.PARTIAL_USER_SELECTED ->
                S02SingleDeviceOutcome.PARTIAL

            else -> S02SingleDeviceOutcome.PASS
        }

    fun toJson(): String = buildString {
        append("""{"permissionState":"$permissionState","totalVisibleRows":$totalVisibleRows""")
        append(""","pages":[""")
        append(
            pages.joinToString(",") {
                """{"pageIndex":${it.pageIndex},"rowCount":${it.rowCount},""" +
                    """"firstId":${it.firstId},"lastId":${it.lastId},""" +
                    """"pageMillis":${it.pageMillis},"duplicateCount":${it.duplicateCount}}"""
            },
        )
        append("""]""")
        append(""","cleanupState":"$cleanupState","authorizationBatchSizes":[""")
        append(authorizationBatchSizes.joinToString(","))
        append("]")
        append(""","exceptionClass":${exceptionClass?.let { "\"$it\"" } ?: "null"}""")
        append(""","decisionS02SingleDevice":"$outcome"}""")
    }
}
