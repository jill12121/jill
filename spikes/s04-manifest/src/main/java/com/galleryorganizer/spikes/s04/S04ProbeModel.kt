package com.galleryorganizer.spikes.s04

enum class AtomicInterruption {
    BEFORE_PROCESS_KILL,
    AFTER_PROCESS_KILL,
    BEFORE_RENAME,
    AFTER_RENAME,
}

enum class RecoveryCandidate { FINAL, TMP, BAK, NONE }

enum class S04PendingPhase { PREPARING, READY, OBSERVED }

enum class S04SingleDeviceOutcome { PASS, PENDING, FAIL }

data class StageObservation(
    val stage: AtomicInterruption,
    val processRestartObserved: Boolean,
    val parseableCandidateCount: Int,
    val recoveryCandidate: RecoveryCandidate,
    val cleanupSucceeded: Boolean,
)

object RecoverySelector {
    fun choose(parseable: Set<RecoveryCandidate>): RecoveryCandidate = when {
        RecoveryCandidate.FINAL in parseable -> RecoveryCandidate.FINAL
        RecoveryCandidate.TMP in parseable -> RecoveryCandidate.TMP
        RecoveryCandidate.BAK in parseable -> RecoveryCandidate.BAK
        else -> RecoveryCandidate.NONE
    }
}

data class S04ProbeResult(
    val observations: Map<AtomicInterruption, StageObservation>,
    val providerOverwriteBehavior: String,
    val exceptionClass: String? = null,
    val pendingStagePresent: Boolean = false,
) {
    val outcome: S04SingleDeviceOutcome
        get() {
            if (
                exceptionClass != null ||
                (
                    providerOverwriteBehavior != "NOT_RUN" &&
                        providerOverwriteBehavior !in ACCEPTED_OVERWRITE_BEHAVIORS
                    ) ||
                observations.values.any {
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
                """"cleanupSucceeded":${value.cleanupSucceeded}}"""
        }
        return """{"stageObservations":$stages,"providerOverwriteBehavior":""" +
            """"$providerOverwriteBehavior","exceptionClass":""" +
            (exceptionClass?.let { "\"$it\"" } ?: "null") +
            ""","pendingStagePresent":$pendingStagePresent""" +
            ""","decisionS04SingleDevice":"$outcome"}"""
    }

    private companion object {
        val ACCEPTED_OVERWRITE_BEHAVIORS = setOf(
            "OVERWROTE_EXISTING",
            "DUPLICATE_EXACT",
            "AUTO_RENAMED",
        )
    }
}
