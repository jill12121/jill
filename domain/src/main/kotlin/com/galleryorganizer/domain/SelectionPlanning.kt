package com.galleryorganizer.domain

import com.galleryorganizer.contract.FileFingerprint

data class DraftSelectionAction(
    val fileId: String,
    val action: SourceDecisionValue,
    val displayOrder: Int? = null,
    val isCover: Boolean? = null,
)

data class SelectionDraft(
    val draftId: String,
    val scopeId: String,
    val revision: Long,
    val actions: List<DraftSelectionAction>,
)

data class FrozenSelectionPlan(
    val draftId: String,
    val plan: OrganizerPlan,
    val selection: SelectionSnapshot,
)

enum class SelectionPlanningFailure {
    INVALID_SELECTION,
    ACTIVE_DRAFT_EXISTS,
    DRAFT_NOT_FOUND,
    STALE_REVISION,
    INVALID_PLAN,
    PERSISTENCE_CONFLICT,
}

sealed interface SelectionPlanningResult<out T> {
    data class Success<T>(val value: T) : SelectionPlanningResult<T>
    data class Rejected(val failure: SelectionPlanningFailure) : SelectionPlanningResult<Nothing>
}

interface SelectionDraftRepository {
    suspend fun create(draft: SelectionDraft): Boolean
    suspend fun find(draftId: String): SelectionDraft?
    suspend fun replace(draft: SelectionDraft, expectedRevision: Long): Boolean
    suspend fun savePlan(frozen: FrozenSelectionPlan): Boolean
}

class DefaultSelectionPlanner(
    private val repository: SelectionDraftRepository,
) {
    suspend fun createDraft(
        draftId: String,
        batch: PersistedSourceBatch,
        actions: List<DraftSelectionAction>,
    ): SelectionPlanningResult<SelectionDraft> {
        val draft = SelectionDraft(draftId, batch.stableKey, 1, actions)
        if (!draft.isValidFor(batch)) return rejected(SelectionPlanningFailure.INVALID_SELECTION)
        return if (repository.create(draft)) {
            SelectionPlanningResult.Success(draft)
        } else {
            rejected(SelectionPlanningFailure.ACTIVE_DRAFT_EXISTS)
        }
    }

    suspend fun reviseDraft(
        draftId: String,
        expectedRevision: Long,
        batch: PersistedSourceBatch,
        actions: List<DraftSelectionAction>,
    ): SelectionPlanningResult<SelectionDraft> {
        val current = repository.find(draftId)
            ?: return rejected(SelectionPlanningFailure.DRAFT_NOT_FOUND)
        if (current.revision != expectedRevision) {
            return rejected(SelectionPlanningFailure.STALE_REVISION)
        }
        val revised = current.copy(revision = current.revision + 1, actions = actions)
        if (revised.scopeId != batch.stableKey || !revised.isValidFor(batch)) {
            return rejected(SelectionPlanningFailure.INVALID_SELECTION)
        }
        return if (repository.replace(revised, expectedRevision)) {
            SelectionPlanningResult.Success(revised)
        } else {
            rejected(SelectionPlanningFailure.STALE_REVISION)
        }
    }

    suspend fun freezePlan(
        draftId: String,
        batch: PersistedSourceBatch,
        plan: OrganizerPlan,
    ): SelectionPlanningResult<FrozenSelectionPlan> {
        val draft = repository.find(draftId)
            ?: return rejected(SelectionPlanningFailure.DRAFT_NOT_FOUND)
        val selectedIds = draft.actions.filter { it.action == SourceDecisionValue.KEEP }
            .map(DraftSelectionAction::fileId).toSet()
        val selected = batch.items.filter { it.fileId in selectedIds }
        val imports = (plan.payload as? OrgPayload)?.imports
        val selectedById = selected.associateBy(PersistedSourceItem::fileId)
        val valid = draft.scopeId == batch.stableKey &&
            plan.type == PlanType.ORG &&
            plan.snapshot.draftRevision == draft.revision &&
            selectedIds.isNotEmpty() &&
            selected.size == selectedIds.size &&
            selected.none(PersistedSourceItem::imported) &&
            selected.all { it.scanGeneration == plan.snapshot.scanGeneration } &&
            imports?.size == selectedIds.size &&
            imports?.all {
                val item = selectedById[it.source.ref.stableKey]
                item != null && it.source.fingerprint == FileFingerprint(
                    item.sizeBytes,
                    item.modifiedAtEpochMillis,
                    item.mimeType,
                )
            } == true &&
            PlanSchemas.validate(plan).isEmpty()
        if (!valid) return rejected(SelectionPlanningFailure.INVALID_PLAN)
        val selection = SelectionSnapshotMaterializer.materialize(
            plan.snapshot.scanGeneration,
            draft.revision,
            selected.map { SelectionCandidate(it.sourceKey, it.displayName) },
            selected.mapTo(mutableSetOf(), PersistedSourceItem::sourceKey),
        )
        val frozen = FrozenSelectionPlan(draftId, plan, selection)
        return if (repository.savePlan(frozen)) {
            SelectionPlanningResult.Success(frozen)
        } else {
            rejected(SelectionPlanningFailure.PERSISTENCE_CONFLICT)
        }
    }

    private fun SelectionDraft.isValidFor(batch: PersistedSourceBatch): Boolean {
        if (draftId.isBlank() || scopeId.isBlank() || actions.isEmpty()) return false
        val available = batch.items.associateBy(PersistedSourceItem::fileId)
        return actions.map(DraftSelectionAction::fileId).distinct().size == actions.size &&
            actions.count { it.isCover == true } <= 1 &&
            actions.all { action ->
                val item = available[action.fileId]
                item != null &&
                    (action.displayOrder == null || action.displayOrder >= 0) &&
                    !(action.action == SourceDecisionValue.KEEP && item.imported) &&
                    (action.action == SourceDecisionValue.KEEP ||
                        action.displayOrder == null && action.isCover == null)
            }
    }

    private fun rejected(failure: SelectionPlanningFailure) =
        SelectionPlanningResult.Rejected(failure)
}
