package com.galleryorganizer.spikes.s04

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class S04ProbeModelTest {
    @Test
    fun `all four restarted recoverable stages are required to pass`() {
        val observations = AtomicInterruption.entries.associateWith {
            observation(it)
        }

        assertEquals(
            S04SingleDeviceOutcome.PASS,
            result(observations, "AUTO_RENAMED").outcome,
        )
        assertEquals(
            S04SingleDeviceOutcome.PENDING,
            result(
                observations - AtomicInterruption.AFTER_RENAME,
                "AUTO_RENAMED",
            ).outcome,
        )
        assertEquals(
            S04SingleDeviceOutcome.PENDING,
            result(
                observations,
                "AUTO_RENAMED",
                pendingStagePresent = true,
            ).outcome,
        )
    }

    @Test
    fun `missing candidate restart or cleanup fails`() {
        val complete = AtomicInterruption.entries.associateWith {
            observation(it)
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
                result(it, "AUTO_RENAMED").outcome,
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

    @Test
    fun `only closed provider overwrite outcomes can pass`() {
        val complete = AtomicInterruption.entries.associateWith {
            observation(it)
        }

        listOf("RETURNED_NULL", "UNKNOWN", "ERROR_IllegalStateException").forEach {
            assertEquals(
                S04SingleDeviceOutcome.FAIL,
                result(complete, it).outcome,
            )
        }
    }

    @Test
    fun `overwrite classification uses exact names and parsed generations`() {
        assertEquals(
            "OVERWROTE_EXISTING",
            ProviderOverwriteClassifier.classify(listOf(2), "manifest.json", 2),
        )
        assertEquals(
            "DUPLICATE_EXACT",
            ProviderOverwriteClassifier.classify(listOf(1, 2), "manifest.json", 2),
        )
        assertEquals(
            "AUTO_RENAMED",
            ProviderOverwriteClassifier.classify(listOf(1), "manifest (1).json", 2),
        )
        assertEquals(
            "INCONCLUSIVE",
            ProviderOverwriteClassifier.classify(listOf(1), "manifest.json", 1),
        )
    }

    @Test
    fun `mixed run or tree observations cannot pass`() {
        val complete = AtomicInterruption.entries.associateWith { observation(it) }
        val stage = AtomicInterruption.AFTER_RENAME

        assertEquals(
            S04SingleDeviceOutcome.FAIL,
            result(
                complete + (stage to observation(stage).copy(runId = "other-run")),
                "AUTO_RENAMED",
            ).outcome,
        )
        assertEquals(
            S04SingleDeviceOutcome.FAIL,
            result(
                complete + (stage to observation(stage).copy(treeUriSha256 = "b".repeat(64))),
                "AUTO_RENAMED",
            ).outcome,
        )
    }

    @Test
    fun `provenance hashes fingerprint without exporting raw value`() {
        val provenance = provenance()

        assertTrue(provenance.isValid)
        assertEquals(64, provenance.fingerprintSha256.length)
        assertFalse(provenance.toJson().contains("raw-fingerprint"))
    }

    @Test
    fun `null child query is retryable failure rather than absence`() {
        assertEquals(ChildQueryStatus.QUERY_FAILED, ChildQueryPolicy.classify(null))
        assertEquals(ChildQueryStatus.ABSENT, ChildQueryPolicy.classify(0))
        assertEquals(ChildQueryStatus.FOUND, ChildQueryPolicy.classify(1))
    }

    @Test
    fun `preparing cleanup only uses persisted or unique exact root`() {
        assertEquals(
            PreparingRootRecovery.USE_PERSISTED_ROOT,
            PreparingRootRecoveryPolicy.choose(
                persistedRootAvailable = true,
                queryStatus = ChildQueryStatus.QUERY_FAILED,
                matchCount = 0,
            ),
        )
        assertEquals(
            PreparingRootRecovery.DELETE_UNIQUE_EXACT_MATCH,
            PreparingRootRecoveryPolicy.choose(
                persistedRootAvailable = false,
                queryStatus = ChildQueryStatus.FOUND,
                matchCount = 1,
            ),
        )
        assertEquals(
            PreparingRootRecovery.CLEAR_PENDING,
            PreparingRootRecoveryPolicy.choose(
                persistedRootAvailable = false,
                queryStatus = ChildQueryStatus.ABSENT,
                matchCount = 0,
            ),
        )
        assertEquals(
            PreparingRootRecovery.RETRY_QUERY,
            PreparingRootRecoveryPolicy.choose(
                persistedRootAvailable = false,
                queryStatus = ChildQueryStatus.QUERY_FAILED,
                matchCount = 0,
            ),
        )
        assertEquals(
            PreparingRootRecovery.REFUSE_AMBIGUOUS,
            PreparingRootRecoveryPolicy.choose(
                persistedRootAvailable = false,
                queryStatus = ChildQueryStatus.FOUND,
                matchCount = 2,
            ),
        )
    }

    @Test
    fun `failed persisted delete confirms absence before completing cleanup`() {
        assertEquals(
            PreparingDeleteConfirmation.COMPLETE,
            PreparingDeleteConfirmationPolicy.choose(
                ChildQueryStatus.ABSENT,
                matchCount = 0,
            ),
        )
        assertEquals(
            PreparingDeleteConfirmation.RETRY,
            PreparingDeleteConfirmationPolicy.choose(
                ChildQueryStatus.QUERY_FAILED,
                matchCount = 0,
            ),
        )
        assertEquals(
            PreparingDeleteConfirmation.RETRY,
            PreparingDeleteConfirmationPolicy.choose(
                ChildQueryStatus.FOUND,
                matchCount = 1,
            ),
        )
        assertEquals(
            PreparingDeleteConfirmation.REFUSE_AMBIGUOUS,
            PreparingDeleteConfirmationPolicy.choose(
                ChildQueryStatus.FOUND,
                matchCount = 2,
            ),
        )
    }

    @Test
    fun `legacy or malformed pending is explicit and blocks run reset`() {
        assertEquals(
            PendingRecordStatus.NONE,
            PendingRecordPolicy.classify(
                hasAnyPendingField = false,
                allRequiredFieldsPresent = false,
                valuesValid = false,
            ),
        )
        assertEquals(
            PendingRecordStatus.VALID,
            PendingRecordPolicy.classify(
                hasAnyPendingField = true,
                allRequiredFieldsPresent = true,
                valuesValid = true,
            ),
        )
        assertEquals(
            PendingRecordStatus.INCOMPATIBLE,
            PendingRecordPolicy.classify(
                hasAnyPendingField = true,
                allRequiredFieldsPresent = false,
                valuesValid = true,
            ),
        )
        assertFalse(RunResetPolicy.canReset(PendingRecordStatus.VALID))
        assertFalse(RunResetPolicy.canReset(PendingRecordStatus.INCOMPATIBLE))
        assertTrue(RunResetPolicy.canReset(PendingRecordStatus.NONE))
    }

    private fun observation(stage: AtomicInterruption) = StageObservation(
        stage,
        processRestartObserved = true,
        parseableCandidateCount = 1,
        recoveryCandidate = RecoveryCandidate.FINAL,
        cleanupSucceeded = true,
        runId = RUN_ID,
        treeUriSha256 = TREE_HASH,
    )

    private fun result(
        observations: Map<AtomicInterruption, StageObservation>,
        overwrite: String,
        pendingStagePresent: Boolean = false,
    ) = S04ProbeResult(
        observations,
        overwrite,
        pendingStagePresent = pendingStagePresent,
        runId = RUN_ID,
        treeUriSha256 = TREE_HASH,
        provenance = provenance(),
    )

    private fun provenance() = S04ProbeProvenance.fromRawFingerprint(
        model = "model",
        sdkInt = 35,
        manufacturer = "maker",
        securityPatch = "2026-10-01",
        appVersionName = "0.3.1",
        appVersionCode = 31,
        rawFingerprint = "raw-fingerprint",
    )

    private companion object {
        const val RUN_ID = "run-123"
        val TREE_HASH = "a".repeat(64)
    }
}
