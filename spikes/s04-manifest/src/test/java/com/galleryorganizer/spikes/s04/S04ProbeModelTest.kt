package com.galleryorganizer.spikes.s04

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class S04ProbeModelTest {
    @Test
    fun `prepare failure exports its exact operation stage`() {
        val failed = S04ProbeResult(
            observations = emptyMap(),
            providerOverwriteBehavior = "NOT_RUN",
            exceptionClass = "IllegalStateException",
            exceptionStage = S04PrepareStage.CREATE_OWNER_MARKER,
        )

        assertEquals(S04SingleDeviceOutcome.FAIL, failed.outcome)
        assertTrue(
            failed.toJson().contains(
                """"exceptionStage":"CREATE_OWNER_MARKER"""",
            ),
        )
    }

    @Test
    fun `owner marker name matches json mime while legacy name remains readable`() {
        assertEquals(".s04-owner.json", S04OwnerMarkerPolicy.WRITE_NAME)
        assertTrue(S04OwnerMarkerPolicy.isReadableName(".s04-owner.json"))
        assertTrue(S04OwnerMarkerPolicy.isReadableName(".s04-owner"))
        assertFalse(S04OwnerMarkerPolicy.isReadableName(".s04-owner.json.json"))
    }

    @Test
    fun `overwrite temporary name ends with json and legacy name remains owned`() {
        assertEquals(
            ".s04-overwrite.tmp.json",
            ProviderOverwriteClassifier.OVERWRITE_TMP_NAME,
        )
        assertTrue(
            OverwriteFixtureOwnership.isOwned(
                ProviderOverwriteClassifier.OVERWRITE_TMP_NAME,
                marker = null,
                generation = 2,
            ),
        )
        assertTrue(
            OverwriteFixtureOwnership.isOwned(
                ".s04-overwrite.json.tmp",
                marker = null,
                generation = 2,
            ),
        )
    }

    @Test
    fun `overwrite failure includes its exact operation stage`() {
        assertEquals(
            "ERROR_CREATE_CANDIDATE_IllegalStateException",
            OverwriteFailure.describe(
                S04OverwriteStage.CREATE_CANDIDATE,
                "IllegalStateException",
            ),
        )
    }

    @Test
    fun `guide starts with the first missing cutoff`() {
        val result = S04ProbeResult(
            observations = emptyMap(),
            providerOverwriteBehavior = "NOT_RUN",
        )

        assertEquals(
            S04GuideAction.Prepare(AtomicInterruption.BEFORE_PROCESS_KILL),
            S04GuidePolicy.forResult(result, pendingReadyToObserve = false).primaryAction,
        )
        assertEquals(
            0,
            S04GuidePolicy.forResult(result, pendingReadyToObserve = false).completedStageCount,
        )
        assertFalse(
            S04GuidePolicy.forResult(result, pendingReadyToObserve = false).canReset,
        )
    }

    @Test
    fun `guide advances to the first missing cutoff`() {
        val observations = linkedMapOf(
            AtomicInterruption.BEFORE_PROCESS_KILL to
                observation(AtomicInterruption.BEFORE_PROCESS_KILL),
            AtomicInterruption.AFTER_PROCESS_KILL to
                observation(AtomicInterruption.AFTER_PROCESS_KILL),
        )

        val guide = S04GuidePolicy.forResult(
            result(observations, "AUTO_RENAMED"),
            pendingReadyToObserve = false,
        )

        assertEquals(
            S04GuideAction.Prepare(AtomicInterruption.BEFORE_RENAME),
            guide.primaryAction,
        )
        assertEquals(2, guide.completedStageCount)
        assertFalse(guide.canReset)
    }

    @Test
    fun `guide waits for restart before allowing observation`() {
        val pending = result(
            observations = mapOf(
                AtomicInterruption.BEFORE_PROCESS_KILL to
                    observation(AtomicInterruption.BEFORE_PROCESS_KILL),
            ),
            overwrite = "AUTO_RENAMED",
            pendingStagePresent = true,
        )

        assertEquals(
            S04GuideAction.ForceStopAndReopen,
            S04GuidePolicy.forResult(
                pending,
                pendingReadyToObserve = false,
            ).primaryAction,
        )
        assertEquals(
            S04GuideAction.ObserveAfterRestart,
            S04GuidePolicy.forResult(
                pending,
                pendingReadyToObserve = true,
            ).primaryAction,
        )
    }

    @Test
    fun `guide requires observation after restart while a cutoff is pending`() {
        val guide = S04GuidePolicy.forResult(
            result(
                observations = mapOf(
                    AtomicInterruption.BEFORE_PROCESS_KILL to
                        observation(AtomicInterruption.BEFORE_PROCESS_KILL),
                ),
                overwrite = "AUTO_RENAMED",
                pendingStagePresent = true,
            ),
            pendingReadyToObserve = true,
        )

        assertEquals(S04GuideAction.ObserveAfterRestart, guide.primaryAction)
        assertEquals(1, guide.completedStageCount)
        assertFalse(guide.canReset)
    }

    @Test
    fun `guide exposes final copy only after pass`() {
        val guide = S04GuidePolicy.forResult(
            result(
                AtomicInterruption.entries.associateWith(::observation),
                "AUTO_RENAMED",
            ),
            pendingReadyToObserve = false,
        )

        assertEquals(S04GuideAction.CopyPassResult, guide.primaryAction)
        assertEquals(4, guide.completedStageCount)
        assertTrue(guide.canReset)
    }

    @Test
    fun `guide preserves failed evidence for review`() {
        val guide = S04GuidePolicy.forResult(
            result(
                observations = mapOf(
                    AtomicInterruption.BEFORE_PROCESS_KILL to
                        observation(AtomicInterruption.BEFORE_PROCESS_KILL),
                ),
                overwrite = "INCONCLUSIVE",
            ),
            pendingReadyToObserve = false,
        )

        assertEquals(S04GuideAction.ReviewFailure, guide.primaryAction)
        assertEquals(1, guide.completedStageCount)
        assertTrue(guide.canReset)
    }

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
    fun `overwrite failure remains sticky across later stages`() {
        assertEquals(
            "AUTO_RENAMED",
            OverwriteEvidencePolicy.accumulate("NOT_RUN", "AUTO_RENAMED"),
        )
        assertEquals(
            "INCONCLUSIVE",
            OverwriteEvidencePolicy.accumulate("INCONCLUSIVE", "AUTO_RENAMED"),
        )
        assertEquals(
            "ERROR_IOException",
            OverwriteEvidencePolicy.accumulate("AUTO_RENAMED", "ERROR_IOException"),
        )
        assertEquals(
            "DUPLICATE_EXACT",
            OverwriteEvidencePolicy.accumulate("AUTO_RENAMED", "DUPLICATE_EXACT"),
        )
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
    fun `overwrite measurement starts only after owned fixtures are absent`() {
        assertEquals(
            OverwriteCleanupAction.RETRY_QUERY,
            OverwriteCleanupPolicy.choose(ChildQueryStatus.QUERY_FAILED, ownedFixtureCount = 0),
        )
        assertEquals(
            OverwriteCleanupAction.DELETE_OWNED,
            OverwriteCleanupPolicy.choose(ChildQueryStatus.FOUND, ownedFixtureCount = 2),
        )
        assertEquals(
            OverwriteCleanupAction.READY,
            OverwriteCleanupPolicy.choose(ChildQueryStatus.FOUND, ownedFixtureCount = 0),
        )
        assertEquals(
            OverwriteCleanupAction.READY,
            OverwriteCleanupPolicy.choose(ChildQueryStatus.ABSENT, ownedFixtureCount = 0),
        )
    }

    @Test
    fun `overwrite ownership accepts marker and legacy reserved names only`() {
        assertTrue(
            OverwriteFixtureOwnership.isOwned(
                "provider-generated-name.json",
                OverwriteFixtureOwnership.MARKER,
                2,
            ),
        )
        assertTrue(
            OverwriteFixtureOwnership.isOwned(
                ProviderOverwriteClassifier.OVERWRITE_NAME,
                marker = null,
                generation = 1,
            ),
        )
        assertFalse(
            OverwriteFixtureOwnership.isOwned(
                "manifest.json",
                marker = null,
                generation = 1,
            ),
        )
        assertFalse(
            OverwriteFixtureOwnership.isOwned(
                ProviderOverwriteClassifier.OVERWRITE_NAME,
                marker = null,
                generation = 3,
            ),
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
    fun `exact child lookup rejects ambiguity`() {
        assertEquals(
            UniqueChildResolution.QUERY_FAILED,
            UniqueChildPolicy.resolve(ChildQueryStatus.QUERY_FAILED, 0),
        )
        assertEquals(
            UniqueChildResolution.ABSENT,
            UniqueChildPolicy.resolve(ChildQueryStatus.ABSENT, 0),
        )
        assertEquals(
            UniqueChildResolution.UNIQUE,
            UniqueChildPolicy.resolve(ChildQueryStatus.FOUND, 1),
        )
        assertEquals(
            UniqueChildResolution.AMBIGUOUS,
            UniqueChildPolicy.resolve(ChildQueryStatus.FOUND, 2),
        )
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

    @Test
    fun `incompatible pending deletion requires complete verified ownership`() {
        assertTrue(
            IncompatiblePendingCleanupPolicy.canDelete(
                hasTreeAndToken = true,
                uniqueExactRoot = true,
                ownedMarkerVerified = true,
            ),
        )
        assertFalse(
            IncompatiblePendingCleanupPolicy.canDelete(
                hasTreeAndToken = false,
                uniqueExactRoot = true,
                ownedMarkerVerified = true,
            ),
        )
        assertFalse(
            IncompatiblePendingCleanupPolicy.canDelete(
                hasTreeAndToken = true,
                uniqueExactRoot = false,
                ownedMarkerVerified = true,
            ),
        )
        assertFalse(
            IncompatiblePendingCleanupPolicy.canDelete(
                hasTreeAndToken = true,
                uniqueExactRoot = true,
                ownedMarkerVerified = false,
            ),
        )
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
