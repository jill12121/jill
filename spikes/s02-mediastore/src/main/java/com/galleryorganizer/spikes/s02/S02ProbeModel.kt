package com.galleryorganizer.spikes.s02

import java.security.MessageDigest

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
    GRANTED_QUERY_FAILED,
    DENIED,
    REQUEST_FAILED,
}

enum class CleanupFixtureOwnership { APP_OWNED, EXTERNAL_HELPER_OWNED }

enum class S02SingleDeviceOutcome { PASS, PARTIAL, PENDING, FAIL }

data class S02ProbeProvenance(
    val schemaVersion: Int,
    val probeVersion: String,
    val runId: String,
    val model: String,
    val sdkInt: Int,
    val manufacturer: String,
    val securityPatch: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val fingerprintSha256: String,
) {
    val isValid: Boolean
        get() = schemaVersion == SCHEMA_VERSION &&
            probeVersion == PROBE_VERSION &&
            runId.isNotBlank() &&
            model.isNotBlank() &&
            sdkInt >= 30 &&
            manufacturer.isNotBlank() &&
            securityPatch.matches(Regex("""\d{4}-\d{2}-\d{2}""")) &&
            appVersionName.isNotBlank() &&
            appVersionCode >= 0 &&
            fingerprintSha256.matches(Regex("""[0-9a-f]{64}"""))

    fun toJson(): String =
        """{"schemaVersion":$schemaVersion,"probeVersion":${probeVersion.jsonString()},""" +
            """"runId":${runId.jsonString()},"model":${model.jsonString()},""" +
            """"sdkInt":$sdkInt,"manufacturer":${manufacturer.jsonString()},""" +
            """"securityPatch":${securityPatch.jsonString()},""" +
            """"appVersionName":${appVersionName.jsonString()},""" +
            """"appVersionCode":$appVersionCode,""" +
            """"fingerprintSha256":"$fingerprintSha256"}"""

    companion object {
        const val SCHEMA_VERSION = 1
        const val PROBE_VERSION = "S02.3"

        fun fromRawFingerprint(
            runId: String,
            model: String,
            sdkInt: Int,
            manufacturer: String,
            securityPatch: String,
            appVersionName: String,
            appVersionCode: Long,
            rawFingerprint: String,
        ) = S02ProbeProvenance(
            SCHEMA_VERSION,
            PROBE_VERSION,
            runId,
            model,
            sdkInt,
            manufacturer,
            securityPatch,
            appVersionName,
            appVersionCode,
            MessageDigest.getInstance("SHA-256")
                .digest(rawFingerprint.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) },
        )
    }
}

data class PageEvidence(
    val pageIndex: Int,
    val rowCount: Int,
    val firstId: Long,
    val lastId: Long,
    val pageMillis: Long,
    val duplicateCount: Int,
    val strictlyIncreasing: Boolean = true,
    val queryArgsHonored: Boolean = true,
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
                page.duplicateCount == 0 &&
                page.strictlyIncreasing &&
                page.queryArgsHonored
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
    val cleanupFixtureOwnership: CleanupFixtureOwnership =
        CleanupFixtureOwnership.APP_OWNED,
    val provenance: S02ProbeProvenance,
    val exceptionClass: String? = null,
) {
    val outcome: S02SingleDeviceOutcome
        get() = when {
            !provenance.isValid ||
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
                cleanupState == CleanupAuthorizationState.GRANTED_QUERY_FAILED ||
                cleanupState == CleanupAuthorizationState.DENIED ||
                cleanupState == CleanupAuthorizationState.REQUEST_FAILED ->
                S02SingleDeviceOutcome.FAIL

            cleanupState == CleanupAuthorizationState.NOT_RUN ||
                cleanupState == CleanupAuthorizationState.PENDING ->
                S02SingleDeviceOutcome.PENDING

            cleanupFixtureOwnership == CleanupFixtureOwnership.APP_OWNED ->
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
                    """"pageMillis":${it.pageMillis},"duplicateCount":${it.duplicateCount},""" +
                    """"strictlyIncreasing":${it.strictlyIncreasing},""" +
                    """"queryArgsHonored":${it.queryArgsHonored}}"""
            },
        )
        append("""]""")
        append(""","cleanupState":"$cleanupState","authorizationBatchSizes":[""")
        append(authorizationBatchSizes.joinToString(","))
        append("]")
        append(""","cleanupFixtureOwnership":"$cleanupFixtureOwnership"""")
        append(""","provenance":${provenance.toJson()}""")
        append(""","exceptionClass":${exceptionClass?.let { "\"$it\"" } ?: "null"}""")
        append(""","decisionS02SingleDevice":"$outcome"}""")
    }
}

private fun String.jsonString(): String = buildString {
    append('"')
    this@jsonString.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u%04x".format(character.code))
            } else {
                append(character)
            }
        }
    }
    append('"')
}
