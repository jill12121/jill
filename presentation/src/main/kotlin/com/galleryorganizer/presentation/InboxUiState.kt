package com.galleryorganizer.presentation

import com.galleryorganizer.domain.SourceDecisionValue

enum class InboxLoadState {
    STORAGE_DEFERRED,
}

enum class InboxDraftStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETE,
}

data class InboxPreviewItem(
    val sourceId: String,
    val label: String,
    val detail: String,
)

data class InboxUiState(
    val previewItems: List<InboxPreviewItem>,
    val decisionsBySourceId: Map<String, SourceDecisionValue>,
    val loadState: InboxLoadState,
) {
    val undecidedCount: Int
        get() = previewItems.count { it.sourceId !in decisionsBySourceId }

    val hasDraft: Boolean
        get() = decisionsBySourceId.isNotEmpty()

    val isDraftComplete: Boolean
        get() = previewItems.isNotEmpty() && undecidedCount == 0

    val draftStatus: InboxDraftStatus
        get() = when {
            decisionsBySourceId.isEmpty() -> InboxDraftStatus.NOT_STARTED
            isDraftComplete -> InboxDraftStatus.COMPLETE
            else -> InboxDraftStatus.IN_PROGRESS
        }

    fun decisionCount(decision: SourceDecisionValue): Int =
        previewItems.count { decisionsBySourceId[it.sourceId] == decision }

    companion object {
        fun initial(): InboxUiState = InboxUiState(
            previewItems = listOf(
                InboxPreviewItem(
                    sourceId = "preview-camera",
                    label = "Camera photo",
                    detail = "Example from the Camera scan scope",
                ),
                InboxPreviewItem(
                    sourceId = "preview-screenshot",
                    label = "Screenshot",
                    detail = "Example requiring review before archive",
                ),
                InboxPreviewItem(
                    sourceId = "preview-download",
                    label = "Downloaded image",
                    detail = "Example from the Downloads scan scope",
                ),
            ),
            decisionsBySourceId = emptyMap(),
            loadState = InboxLoadState.STORAGE_DEFERRED,
        )
    }
}

sealed interface InboxEvent {
    data class SetDecision(
        val sourceId: String,
        val decision: SourceDecisionValue,
    ) : InboxEvent

    data class ClearDecision(val sourceId: String) : InboxEvent
    data object ClearDraft : InboxEvent
}

object InboxReducer {
    fun reduce(state: InboxUiState, event: InboxEvent): InboxUiState = when (event) {
        is InboxEvent.SetDecision -> {
            if (state.previewItems.none { it.sourceId == event.sourceId }) {
                state
            } else {
                state.copy(
                    decisionsBySourceId =
                    state.decisionsBySourceId + (event.sourceId to event.decision),
                )
            }
        }
        is InboxEvent.ClearDecision -> state.copy(
            decisionsBySourceId = state.decisionsBySourceId - event.sourceId,
        )
        InboxEvent.ClearDraft -> state.copy(decisionsBySourceId = emptyMap())
    }
}
