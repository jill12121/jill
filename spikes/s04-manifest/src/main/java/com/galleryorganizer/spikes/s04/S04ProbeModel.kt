package com.galleryorganizer.spikes.s04

import java.security.MessageDigest

enum class AtomicInterruption {
    BEFORE_PROCESS_KILL,
    AFTER_PROCESS_KILL,
    BEFORE_RENAME,
    AFTER_RENAME,
}

enum class RecoveryCandidate { FINAL, TMP, BAK, NONE }

enum class S04PendingPhase { PREPARING, READY, OBSERVED }

enum class S04SingleDeviceOutcome { PASS, PENDING, FAIL }

enum class ChildQueryStatus { FOUND, ABSENT, QUERY_FAILED }

object ChildQueryPolicy {
    fun classify(matchCount: Int?): ChildQueryStatus = when {
        matchCount == null -> ChildQueryStatus.QUERY_FAILED
        matchCount == 0 -> ChildQueryStatus.ABSENT
        matchCount > 0 -> ChildQueryStatus.FOUND
        else -> error("matchCount must not be negative")
    }
}

enum class UniqueChildResolution { ABSENT, UNIQUE, AMBIGUOUS, QUERY_FAILED }

object UniqueChildPolicy {
    fun resolve(status: ChildQueryStatus, matchCount: Int): UniqueChildResolution = when {
        status == ChildQueryStatus.QUERY_FAILED -> UniqueChildResolution.QUERY_FAILED
        status == ChildQueryStatus.ABSENT -> UniqueChildResolution.ABSENT
        matchCount == 1 -> UniqueChildResolution.UNIQUE
        else -> UniqueChildResolution.AMBIGUOUS
    }
}

enum class OverwriteCleanupAction { READY, DELETE_OWNED, RETRY_QUERY }

object OverwriteCleanupPolicy {
    fun choose(
        queryStatus: ChildQueryStatus,
        ownedFixtureCount: Int,
    ): OverwriteCleanupAction = when {
        queryStatus == ChildQueryStatus.QUERY_FAILED -> OverwriteCleanupAction.RETRY_QUERY
        ownedFixtureCount > 0 -> OverwriteCleanupAction.DELETE_OWNED
        else -> OverwriteCleanupAction.READY
    }
}

enum class PreparingRootRecovery {
    USE_PERSISTED_ROOT,
    DELETE_UNIQUE_EXACT_MATCH,
    CLEAR_PENDING,
    RETRY_QUERY,
    REFUSE_AMBIGUOUS,
}

object PreparingRootRecoveryPolicy {
    fun choose(
        persistedRootAvailable: Boolean,
        queryStatus: ChildQueryStatus,
        matchCount: Int,
    ): PreparingRootRecovery = when {
        persistedRootAvailable -> PreparingRootRecovery.USE_PERSISTED_ROOT
        queryStatus == ChildQueryStatus.QUERY_FAILED -> PreparingRootRecovery.RETRY_QUERY
        queryStatus == ChildQueryStatus.ABSENT -> PreparingRootRecovery.CLEAR_PENDING
        matchCount == 1 -> PreparingRootRecovery.DELETE_UNIQUE_EXACT_MATCH
        else -> PreparingRootRecovery.REFUSE_AMBIGUOUS
    }
}

enum class PreparingDeleteConfirmation { COMPLETE, RETRY, REFUSE_AMBIGUOUS }

object PreparingDeleteConfirmationPolicy {
    fun choose(
        queryStatus: ChildQueryStatus,
        matchCount: Int,
    ): PreparingDeleteConfirmation = when {
        queryStatus == ChildQueryStatus.ABSENT -> PreparingDeleteConfirmation.COMPLETE
        queryStatus == ChildQueryStatus.QUERY_FAILED -> PreparingDeleteConfirmation.RETRY
        matchCount == 1 -> PreparingDeleteConfirmation.RETRY
        else -> PreparingDeleteConfirmation.REFUSE_AMBIGUOUS
    }
}

enum class PendingRecordStatus { NONE, VALID, INCOMPATIBLE }

object PendingRecordPolicy {
    fun classify(
        hasAnyPendingField: Boolean,
        allRequiredFieldsPresent: Boolean,
        valuesValid: Boolean,
    ): PendingRecordStatus = when {
        !hasAnyPendingField -> PendingRecordStatus.NONE
        allRequiredFieldsPresent && valuesValid -> PendingRecordStatus.VALID
        else -> PendingRecordStatus.INCOMPATIBLE
    }
}

object RunResetPolicy {
    fun canReset(pendingStatus: PendingRecordStatus): Boolean =
        pendingStatus == PendingRecordStatus.NONE
}

object IncompatiblePendingCleanupPolicy {
    fun canDelete(
        hasTreeAndToken: Boolean,
        uniqueExactRoot: Boolean,
        ownedMarkerVerified: Boolean,
    ): Boolean = hasTreeAndToken && uniqueExactRoot && ownedMarkerVerified
}

data class S04ProbeProvenance(
    val schemaVersion: Int,
    val probeVersion: String,
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
            model.isNotBlank() &&
            sdkInt >= 30 &&
            manufacturer.isNotBlank() &&
            securityPatch.matches(Regex("""\d{4}-\d{2}-\d{2}""")) &&
            appVersionName.isNotBlank() &&
            appVersionCode >= 0 &&
            fingerprintSha256.matches(Regex("""[0-9a-f]{64}"""))

    fun toJson(): String =
        """{"schemaVersion":$schemaVersion,"probeVersion":${probeVersion.jsonString()},""" +
            """"model":${model.jsonString()},"sdkInt":$sdkInt,""" +
            """"manufacturer":${manufacturer.jsonString()},""" +
            """"securityPatch":${securityPatch.jsonString()},""" +
            """"appVersionName":${appVersionName.jsonString()},""" +
            """"appVersionCode":$appVersionCode,""" +
            """"fingerprintSha256":"$fingerprintSha256"}"""

    companion object {
        const val SCHEMA_VERSION = 1
        const val PROBE_VERSION = "S04.3"

        fun fromRawFingerprint(
            model: String,
            sdkInt: Int,
            manufacturer: String,
            securityPatch: String,
            appVersionName: String,
            appVersionCode: Long,
            rawFingerprint: String,
        ) = S04ProbeProvenance(
            SCHEMA_VERSION,
            PROBE_VERSION,
            model,
            sdkInt,
            manufacturer,
            securityPatch,
            appVersionName,
            appVersionCode,
            sha256(rawFingerprint),
        )
    }
}

data class StageObservation(
    val stage: AtomicInterruption,
    val processRestartObserved: Boolean,
    val parseableCandidateCount: Int,
    val recoveryCandidate: RecoveryCandidate,
    val cleanupSucceeded: Boolean,
    val runId: String,
    val treeUriSha256: String,
)

object RecoverySelector {
    fun choose(parseable: Set<RecoveryCandidate>): RecoveryCandidate = when {
        RecoveryCandidate.FINAL in parseable -> RecoveryCandidate.FINAL
        RecoveryCandidate.TMP in parseable -> RecoveryCandidate.TMP
        RecoveryCandidate.BAK in parseable -> RecoveryCandidate.BAK
        else -> RecoveryCandidate.NONE
    }
}

object ProviderOverwriteClassifier {
    fun classify(
        exactNameGenerations: List<Int>,
        renamedDisplayName: String?,
        renamedGeneration: Int?,
    ): String = when {
        exactNameGenerations == listOf(2) -> "OVERWROTE_EXISTING"
        exactNameGenerations.sorted() == listOf(1, 2) -> "DUPLICATE_EXACT"
        exactNameGenerations == listOf(1) &&
            renamedDisplayName != OVERWRITE_NAME &&
            renamedGeneration == 2 ->
            "AUTO_RENAMED"

        else -> "INCONCLUSIVE"
    }

    const val OVERWRITE_NAME = ".s04-overwrite.json"

    fun isClosed(value: String): Boolean = value in CLOSED_BEHAVIORS

    private val CLOSED_BEHAVIORS = setOf(
        "OVERWROTE_EXISTING",
        "DUPLICATE_EXACT",
        "AUTO_RENAMED",
    )
}

object OverwriteEvidencePolicy {
    fun accumulate(previous: String, current: String): String = when {
        previous != "NOT_RUN" && !ProviderOverwriteClassifier.isClosed(previous) -> previous
        !ProviderOverwriteClassifier.isClosed(current) -> current
        else -> current
    }
}

object OverwriteFixtureOwnership {
    const val MARKER = "S04_OVERWRITE"

    fun isOwned(
        displayName: String,
        marker: String?,
        generation: Int?,
    ): Boolean =
        marker == MARKER ||
            (
                displayName == ProviderOverwriteClassifier.OVERWRITE_NAME ||
                    displayName == "${ProviderOverwriteClassifier.OVERWRITE_NAME}.tmp"
                ) && generation in 1..2
}

data class S04ProbeResult(
    val observations: Map<AtomicInterruption, StageObservation>,
    val providerOverwriteBehavior: String,
    val exceptionClass: String? = null,
    val pendingStagePresent: Boolean = false,
    val runId: String? = null,
    val treeUriSha256: String? = null,
    val provenance: S04ProbeProvenance? = null,
) {
    val outcome: S04SingleDeviceOutcome
        get() {
            if (exceptionClass != null) return S04SingleDeviceOutcome.FAIL
            val hasEvidence = observations.isNotEmpty() ||
                providerOverwriteBehavior != "NOT_RUN" ||
                runId != null ||
                treeUriSha256 != null ||
                provenance != null
            if (!hasEvidence) return S04SingleDeviceOutcome.PENDING
            if (
                runId.isNullOrBlank() ||
                treeUriSha256?.matches(Regex("""[0-9a-f]{64}""")) != true ||
                provenance?.isValid != true ||
                (
                    providerOverwriteBehavior != "NOT_RUN" &&
                        !ProviderOverwriteClassifier.isClosed(providerOverwriteBehavior)
                    ) ||
                observations.values.any {
                    it.runId != runId ||
                        it.treeUriSha256 != treeUriSha256 ||
                        !it.processRestartObserved ||
                        it.parseableCandidateCount < 1 ||
                        it.recoveryCandidate == RecoveryCandidate.NONE ||
                        !it.cleanupSucceeded
                }
            ) {
                return S04SingleDeviceOutcome.FAIL
            }
            if (pendingStagePresent) return S04SingleDeviceOutcome.PENDING
            return if (
                observations.keys == AtomicInterruption.entries.toSet() &&
                providerOverwriteBehavior != "NOT_RUN"
            ) {
                S04SingleDeviceOutcome.PASS
            } else {
                S04SingleDeviceOutcome.PENDING
            }
        }

    fun toJson(): String {
        val stages = observations.entries.joinToString(prefix = "{", postfix = "}") {
            val value = it.value
            """"${it.key}":{"processRestartObserved":${value.processRestartObserved},""" +
                """"parseableCandidateCount":${value.parseableCandidateCount},""" +
                """"recoveryCandidate":"${value.recoveryCandidate}",""" +
                """"cleanupSucceeded":${value.cleanupSucceeded},""" +
                """"runId":${value.runId.jsonString()},""" +
                """"treeUriSha256":"${value.treeUriSha256}"}"""
        }
        return """{"stageObservations":$stages,"providerOverwriteBehavior":""" +
            """"$providerOverwriteBehavior","exceptionClass":""" +
            (exceptionClass?.let { "\"$it\"" } ?: "null") +
            ""","pendingStagePresent":$pendingStagePresent""" +
            ""","runId":${runId?.jsonString() ?: "null"}""" +
            ""","treeUriSha256":${treeUriSha256?.jsonString() ?: "null"}""" +
            ""","provenance":${provenance?.toJson() ?: "null"}""" +
            ""","decisionS04SingleDevice":"$outcome"}"""
    }
}

fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

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
