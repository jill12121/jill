package com.galleryorganizer.presentation

import com.galleryorganizer.domain.SourceDecisionValue

enum class InboxLoadState {
    STORAGE_DEFERRED,
    IDLE,
    LOADING,
    READY,
    EMPTY,
    ERROR,
    APPENDING,
}

enum class InboxDraftStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETE,
}

data class InboxDecisionSummary(
    val keepCount: Int,
    val reviewCount: Int,
    val trashCount: Int,
    val undecidedCount: Int,
    val status: InboxDraftStatus,
) {
    val decidedCount: Int
        get() = keepCount + reviewCount + trashCount
}

data class InboxPreviewItem(
    val sourceId: String,
    val sourceKey: String = sourceId,
    val label: String,
    val detail: String,
    val persistedDecision: SourceDecisionValue? = null,
)

data class InboxBatchItem(
    val batchKey: String,
    val label: String,
    val imageCount: Int,
)

data class InboxDecisionReviewItem(
    val item: InboxPreviewItem,
    val decision: SourceDecisionValue,
)

data class InboxUiState(
    val batches: List<InboxBatchItem> = emptyList(),
    val selectedBatchKey: String? = null,
    val previewItems: List<InboxPreviewItem>,
    val decisionsBySourceId: Map<String, SourceDecisionValue>,
    val loadState: InboxLoadState,
    val nextCursorFileId: String? = null,
) {
    val hasNextPage: Boolean
        get() = nextCursorFileId != null

    val decisionSummary: InboxDecisionSummary
        get() {
            val undecided = previewItems.count {
                it.sourceId !in decisionsBySourceId
            }
            val status = when {
                decisionsBySourceId.isEmpty() -> InboxDraftStatus.NOT_STARTED
                previewItems.isNotEmpty() && undecided == 0 ->
                    InboxDraftStatus.COMPLETE
                else -> InboxDraftStatus.IN_PROGRESS
            }
            return InboxDecisionSummary(
                keepCount = countDecision(SourceDecisionValue.KEEP),
                reviewCount = countDecision(SourceDecisionValue.REVIEW),
                trashCount = countDecision(SourceDecisionValue.TRASH),
                undecidedCount = undecided,
                status = status,
            )
        }

    val decisionReviewItems: List<InboxDecisionReviewItem>
        get() = previewItems.mapNotNull { item ->
            decisionsBySourceId[item.sourceId]?.let { decision ->
                InboxDecisionReviewItem(item, decision)
            }
        }

    val undecidedCount: Int
        get() = decisionSummary.undecidedCount

    val hasDraft: Boolean
        get() = decisionsBySourceId.isNotEmpty()

    val isDraftComplete: Boolean
        get() = decisionSummary.status == InboxDraftStatus.COMPLETE

    val draftStatus: InboxDraftStatus
        get() = decisionSummary.status

    fun decisionCount(decision: SourceDecisionValue): Int =
        when (decision) {
            SourceDecisionValue.KEEP -> decisionSummary.keepCount
            SourceDecisionValue.REVIEW -> decisionSummary.reviewCount
            SourceDecisionValue.TRASH -> decisionSummary.trashCount
        }

    private fun countDecision(decision: SourceDecisionValue): Int =
        previewItems.count { decisionsBySourceId[it.sourceId] == decision }

    companion object {
        fun production(): InboxUiState = InboxUiState(
            previewItems = emptyList(),
            decisionsBySourceId = emptyMap(),
            loadState = InboxLoadState.IDLE,
        )

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
    data class BatchesLoaded(val batches: List<InboxBatchItem>) : InboxEvent
    data class SelectBatch(val batchKey: String) : InboxEvent
    data class PageLoaded(
        val batchKey: String,
        val items: List<InboxPreviewItem>,
        val nextCursorFileId: String?,
        val append: Boolean,
    ) : InboxEvent
    data class PageFailed(val batchKey: String) : InboxEvent
    data object LoadNextPage : InboxEvent
    data object RetryPage : InboxEvent

    data class SetDecision(
        val sourceId: String,
        val decision: SourceDecisionValue,
    ) : InboxEvent

    data class ClearDecision(val sourceId: String) : InboxEvent
    data object ClearDraft : InboxEvent
}

object InboxReducer {
    const val MAX_LOADED_SOURCE_ITEMS = 96

    fun reduce(state: InboxUiState, event: InboxEvent): InboxUiState = when (event) {
        is InboxEvent.BatchesLoaded -> {
            val batches = event.batches.distinctBy(InboxBatchItem::batchKey)
            if (state.selectedBatchKey != null &&
                batches.none { it.batchKey == state.selectedBatchKey }
            ) {
                state.copy(
                    batches = batches,
                    selectedBatchKey = null,
                    previewItems = emptyList(),
                    decisionsBySourceId = emptyMap(),
                    loadState = InboxLoadState.IDLE,
                    nextCursorFileId = null,
                )
            } else {
                state.copy(
                    batches = batches,
                    loadState = if (batches.isEmpty()) {
                        InboxLoadState.EMPTY
                    } else {
                        state.loadState
                    },
                )
            }
        }
        is InboxEvent.SelectBatch -> {
            if (state.batches.none { it.batchKey == event.batchKey } ||
                state.selectedBatchKey == event.batchKey
            ) {
                state
            } else {
                state.copy(
                    selectedBatchKey = event.batchKey,
                    previewItems = emptyList(),
                    decisionsBySourceId = emptyMap(),
                    loadState = InboxLoadState.LOADING,
                    nextCursorFileId = null,
                )
            }
        }
        is InboxEvent.PageLoaded -> {
            if (event.batchKey != state.selectedBatchKey) {
                state
            } else {
                val items = if (event.append) {
                    (state.previewItems + event.items).distinctBy(InboxPreviewItem::sourceId)
                } else {
                    event.items
                }.takeLast(MAX_LOADED_SOURCE_ITEMS)
                val restored = items.mapNotNull { item ->
                    item.persistedDecision?.let { item.sourceId to it }
                }.toMap()
                state.copy(
                    previewItems = items,
                    decisionsBySourceId = restored + state.decisionsBySourceId
                        .filterKeys { id -> items.any { it.sourceId == id } },
                    loadState = if (items.isEmpty()) {
                        InboxLoadState.EMPTY
                    } else {
                        InboxLoadState.READY
                    },
                    nextCursorFileId = event.nextCursorFileId,
                )
            }
        }
        is InboxEvent.PageFailed ->
            if (event.batchKey == state.selectedBatchKey) {
                state.copy(loadState = InboxLoadState.ERROR)
            } else {
                state
            }
        InboxEvent.LoadNextPage ->
            if (state.loadState == InboxLoadState.READY && state.hasNextPage) {
                state.copy(loadState = InboxLoadState.APPENDING)
            } else {
                state
            }
        InboxEvent.RetryPage ->
            if (state.selectedBatchKey != null && state.loadState == InboxLoadState.ERROR) {
                state.copy(
                    loadState = if (state.previewItems.isEmpty()) {
                        InboxLoadState.LOADING
                    } else {
                        InboxLoadState.APPENDING
                    },
                )
            } else {
                state
            }
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
