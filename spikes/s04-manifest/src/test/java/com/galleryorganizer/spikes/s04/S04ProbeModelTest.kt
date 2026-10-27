package com.galleryorganizer.spikes.s04

import kotlin.test.Test
import kotlin.test.assertEquals

class S04ProbeModelTest {
    @Test
    fun `all four restarted recoverable stages are required to pass`() {
        val observations = AtomicInterruption.entries.associateWith {
            StageObservation(
                it,
                processRestartObserved = true,
                parseableCandidateCount = 1,
                recoveryCandidate = RecoveryCandidate.FINAL,
                cleanupSucceeded = true,
            )
        }

        assertEquals(
            S04SingleDeviceOutcome.PASS,
            S04ProbeResult(observations, "AUTO_RENAMED").outcome,
        )
        assertEquals(
            S04SingleDeviceOutcome.PENDING,
            S04ProbeResult(observations - AtomicInterruption.AFTER_RENAME, "AUTO_RENAMED").outcome,
        )
    }

    @Test
    fun `missing candidate restart or cleanup fails`() {
        val complete = AtomicInterruption.entries.associateWith {
            StageObservation(
                it,
                processRestartObserved = true,
                parseableCandidateCount = 1,
                recoveryCandidate = RecoveryCandidate.FINAL,
                cleanupSucceeded = true,
            )
        }
        listOf(
            complete + (
                AtomicInterruption.BEFORE_RENAME to
                    complete.getValue(AtomicInterruption.BEFORE_RENAME).copy(
                        parseableCandidateCount = 0,
                        recoveryCandidate = RecoveryCandidate.NONE,
                    )
                ),
            complete + (
                AtomicInterruption.BEFORE_RENAME to
                    complete.getValue(AtomicInterruption.BEFORE_RENAME).copy(
                        processRestartObserved = false,
                    )
                ),
            complete + (
                AtomicInterruption.BEFORE_RENAME to
                    complete.getValue(AtomicInterruption.BEFORE_RENAME).copy(
                        cleanupSucceeded = false,
                    )
                ),
        ).forEach {
            assertEquals(
                S04SingleDeviceOutcome.FAIL,
                S04ProbeResult(it, "AUTO_RENAMED").outcome,
            )
        }
    }

    @Test
    fun `recovery candidate prefers final then tmp then bak`() {
        assertEquals(
            RecoveryCandidate.FINAL,
            RecoverySelector.choose(setOf(RecoveryCandidate.BAK, RecoveryCandidate.FINAL)),
        )
        assertEquals(
            RecoveryCandidate.TMP,
            RecoverySelector.choose(setOf(RecoveryCandidate.BAK, RecoveryCandidate.TMP)),
        )
        assertEquals(
            RecoveryCandidate.BAK,
            RecoverySelector.choose(setOf(RecoveryCandidate.BAK)),
        )
        assertEquals(RecoveryCandidate.NONE, RecoverySelector.choose(emptySet()))
    }
}
