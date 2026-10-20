package com.galleryorganizer.domain

import com.galleryorganizer.contract.FileFingerprint
import com.galleryorganizer.contract.RelativePath
import com.galleryorganizer.contract.Sha256
import com.galleryorganizer.contract.SourceFileRef
import com.galleryorganizer.contract.StorageFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrganizerContractsTest {
    @Test
    fun `state machines match the complete transition contract`() {
        assertTransitionMatrix(
            DraftState.entries,
            mapOf(
                DraftState.EDITING to setOf(DraftState.STALE),
                DraftState.STALE to setOf(DraftState.ARCHIVED),
            ),
            OrganizerStateMachines::canTransition,
        )
        assertTransitionMatrix(
            PlanState.entries,
            mapOf(
                PlanState.CREATED to setOf(PlanState.READY, PlanState.STALE, PlanState.BLOCKED),
                PlanState.READY to setOf(PlanState.EXECUTING, PlanState.STALE, PlanState.BLOCKED),
                PlanState.EXECUTING to setOf(
                    PlanState.WAITING_FOR_USER_AUTH,
                    PlanState.COMPLETED,
                    PlanState.PARTIAL,
                    PlanState.FAILED,
                    PlanState.BLOCKED,
                ),
                PlanState.WAITING_FOR_USER_AUTH to setOf(
                    PlanState.READY,
                    PlanState.EXECUTING,
                    PlanState.PARTIAL,
                    PlanState.FAILED,
                    PlanState.BLOCKED,
                ),
            ),
            OrganizerStateMachines::canTransition,
        )
        assertTransitionMatrix(
            AlbumState.entries,
            mapOf(AlbumState.IMPORTING to setOf(AlbumState.COMMITTED, AlbumState.RECOVERY_REQUIRED)),
            OrganizerStateMachines::canTransition,
        )
        assertTransitionMatrix(
            ImportRecordState.entries,
            mapOf(
                ImportRecordState.PENDING_COPY to setOf(ImportRecordState.COPIED),
                ImportRecordState.COPIED to setOf(
                    ImportRecordState.COMMITTED,
                    ImportRecordState.COPIED_SOURCE_RETAINED,
                    ImportRecordState.CLEANUP_PENDING,
                    ImportRecordState.CLEANED,
                    ImportRecordState.CLEANUP_DENIED,
                    ImportRecordState.SOURCE_CHANGED,
                    ImportRecordState.FAILED,
                ),
                ImportRecordState.CLEANUP_PENDING to setOf(
                    ImportRecordState.CLEANED,
                    ImportRecordState.CLEANUP_DENIED,
                    ImportRecordState.SOURCE_CHANGED,
                    ImportRecordState.FAILED,
                ),
            ),
            OrganizerStateMachines::canTransition,
        )
        assertTransitionMatrix(
            TrashEntryState.entries,
            mapOf(
                TrashEntryState.ACTIVE to setOf(
                    TrashEntryState.EXPIRED,
                    TrashEntryState.DELETE_PLANNED,
                    TrashEntryState.RESTORED,
                ),
                TrashEntryState.EXPIRED to setOf(TrashEntryState.DELETE_PLANNED, TrashEntryState.RESTORED),
                TrashEntryState.DELETE_PLANNED to setOf(TrashEntryState.DELETED),
            ),
            OrganizerStateMachines::canTransition,
        )
    }

    @Test
    fun `plan schema validates typed payload and type-specific steps`() {
        val valid = OrganizerPlan(
            planId = "plan-1",
            type = PlanType.ORG,
            snapshot = PlanSnapshot(scanGeneration = 7, draftRevision = 3),
            createdAtEpochMillis = 1000,
            payload = OrgPayload(
                albumId = "album-1",
                immutableDir = RelativePath("Albums/2026-10-20__album-1"),
                imports = listOf(
                    PlannedImport(sourceLocator(), RelativePath("image-1.jpg"), ImportStrategy.COPY_KEEP_SOURCE),
                ),
            ),
            steps = steps(
                PlanStepType.CREATE_DIR,
                PlanStepType.WRITE_NOMEDIA,
                PlanStepType.WRITE_MANIFEST,
                PlanStepType.COPY_PART,
                PlanStepType.PROMOTE_PART,
                PlanStepType.COMMIT_MANIFEST,
            ),
        )
        assertEquals(emptyList(), PlanSchemas.validate(valid))

        val mismatched = valid.copy(
            type = PlanType.METADATA,
            steps = steps(PlanStepType.APPLY_MANIFEST_PATCH, PlanStepType.COMMIT_MANIFEST),
        )
        assertTrue(PlanSchemas.validate(mismatched).any { it.field == "payload" })

        val oversizedCleanup = valid.copy(
            type = PlanType.SOURCE_CLEANUP,
            payload = SourceCleanupPayload(listOf(List(301) { cleanupCandidate() })),
            steps = steps(
                PlanStepType.VERIFY_TARGET_SHA,
                PlanStepType.VERIFY_SOURCE_FINGERPRINT,
                PlanStepType.REQUEST_USER_AUTH,
                PlanStepType.REQUERY_SOURCE,
            ),
        )
        assertTrue(PlanSchemas.validate(oversizedCleanup).any { it.field == "payload.locatorChunks" })

        val invalidCommonFields = valid.copy(
            planId = "",
            snapshot = PlanSnapshot(scanGeneration = 0, draftRevision = 3),
            steps = valid.steps.mapIndexed { index, step ->
                step.copy(index = index + 1, targetId = "")
            },
        )
        assertEquals(
            setOf("planId", "snapshot.scanGeneration", "steps.index", "steps.targetId"),
            PlanSchemas.validate(invalidCommonFields).map { it.field }.toSet(),
        )
    }

    @Test
    fun `scan coverage gates destructive batch behavior independently from API level`() {
        assertTrue(ScanStatus(ScanCoverage.FULL, completed = true).canSweep())
        assertTrue(ScanStatus(ScanCoverage.FULL, completed = true).canOrganizeWholeBatch())
        assertFalse(ScanStatus(ScanCoverage.FULL, completed = false).canSweep())
        assertFalse(ScanStatus(ScanCoverage.PARTIAL_USER_SELECTED, completed = true).canSweep())
        assertTrue(ScanStatus(ScanCoverage.PARTIAL_USER_SELECTED, completed = true).canImportExplicitSelection())
        assertFalse(ScanStatus(ScanCoverage.UNKNOWN, completed = true).canImportExplicitSelection())

        assertTrue(permissionMatrixFor(34).supportsPartialSelection)
        assertFalse(permissionMatrixFor(33).supportsPartialSelection)
        assertFailsWith<IllegalArgumentException> { permissionMatrixFor(29) }
    }

    @Test
    fun `failure resolution uses operation context and preserves retryable provider failures`() {
        assertEquals(
            DomainErrorCode.PARTIAL_MEDIA_ACCESS,
            DomainFailure.Storage(StorageOperation.SOURCE_READ, StorageFailure.AccessDenied).userActionCode(),
        )
        assertEquals(
            DomainErrorCode.SOURCE_DELETE_AUTH_DENIED,
            DomainFailure.Storage(StorageOperation.SOURCE_CLEANUP_AUTH, StorageFailure.AccessDenied).userActionCode(),
        )
        assertEquals(
            DomainErrorCode.TRASH_RESTORE_CONFLICT,
            DomainFailure.Storage(StorageOperation.TRASH_RESTORE, StorageFailure.Conflict).userActionCode(),
        )
        assertEquals(DomainErrorCode.SIDECAR_MISMATCH, DomainFailure.SidecarMismatch.userActionCode())
        assertEquals(
            FailureResolution.Retry,
            DomainFailure.Storage(
                StorageOperation.SOURCE_ENUMERATION,
                StorageFailure.ProviderUnavailable(),
            ).resolve(),
        )
    }

    @Test
    fun `source decision is ignored when source fingerprint changes`() {
        val decision = SourceDecision(
            sourceKey = "download:root:a.jpg:100:200",
            decision = SourceDecisionValue.TRASH,
            updatedAtEpochMillis = 10,
        )
        assertTrue(decision.appliesTo("download:root:a.jpg:100:200"))
        assertFalse(decision.appliesTo("download:root:a.jpg:101:200"))
    }

    private fun sourceLocator() = SourceLocator(
        ref = SourceFileRef("source-1"),
        fingerprint = FileFingerprint(100, 200, "image/jpeg"),
    )

    private fun cleanupCandidate() = SourceCleanupCandidate(
        source = sourceLocator(),
        target = RelativePath("Albums/album-1/image-1.jpg"),
        expectedSha256 = Sha256("sha256"),
    )

    private fun steps(vararg types: PlanStepType): List<PlanStep> =
        types.mapIndexed { index, type -> PlanStep(index, type, "target-$index") }

    private fun DomainFailure.userActionCode(): DomainErrorCode =
        (resolve() as FailureResolution.UserAction).code

    private fun <S> assertTransitionMatrix(
        states: List<S>,
        allowed: Map<S, Set<S>>,
        canTransition: (S, S) -> Boolean,
    ) {
        states.forEach { from ->
            states.forEach { to ->
                assertEquals(
                    expected = to in allowed.orEmpty(from),
                    actual = canTransition(from, to),
                    message = "Unexpected transition: $from -> $to",
                )
            }
        }
    }

    private fun <S> Map<S, Set<S>>.orEmpty(key: S): Set<S> = get(key).orEmpty()
}
