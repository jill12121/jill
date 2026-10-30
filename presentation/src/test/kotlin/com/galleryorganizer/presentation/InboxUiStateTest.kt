package com.galleryorganizer.presentation

import com.galleryorganizer.domain.SourceDecisionValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InboxUiStateTest {
    @Test
    fun `initial inbox contains preview items with no source decisions`() {
        val state = InboxUiState.initial()

        assertEquals(InboxLoadState.STORAGE_DEFERRED, state.loadState)
        assertEquals(3, state.previewItems.size)
        assertTrue(state.decisionsBySourceId.isEmpty())
        assertEquals(0, state.decisionCount(SourceDecisionValue.KEEP))
        assertEquals(3, state.undecidedCount)
        assertTrue(!state.hasDraft)
        assertTrue(!state.isDraftComplete)
        assertEquals(InboxDraftStatus.NOT_STARTED, state.draftStatus)
    }

    @Test
    fun `source decisions are editable only for known preview items`() {
        val sourceId = InboxUiState.initial().previewItems.first().sourceId
        val decided = InboxReducer.reduce(
            InboxUiState.initial(),
            InboxEvent.SetDecision(sourceId, SourceDecisionValue.REVIEW),
        )
        val ignored = InboxReducer.reduce(
            decided,
            InboxEvent.SetDecision("unknown", SourceDecisionValue.TRASH),
        )

        assertEquals(SourceDecisionValue.REVIEW, ignored.decisionsBySourceId[sourceId])
        assertEquals(1, ignored.decisionsBySourceId.size)
        assertEquals(InboxLoadState.STORAGE_DEFERRED, ignored.loadState)
        assertEquals(1, ignored.decisionCount(SourceDecisionValue.REVIEW))
        assertEquals(2, ignored.undecidedCount)
        assertTrue(ignored.hasDraft)
        assertTrue(!ignored.isDraftComplete)
        assertEquals(InboxDraftStatus.IN_PROGRESS, ignored.draftStatus)
    }

    @Test
    fun `preview draft decisions can be replaced and cleared without creating a plan`() {
        val sourceId = InboxUiState.initial().previewItems.first().sourceId
        val keep = InboxReducer.reduce(
            InboxUiState.initial(),
            InboxEvent.SetDecision(sourceId, SourceDecisionValue.KEEP),
        )
        val trash = InboxReducer.reduce(
            keep,
            InboxEvent.SetDecision(sourceId, SourceDecisionValue.TRASH),
        )
        val clearedItem = InboxReducer.reduce(trash, InboxEvent.ClearDecision(sourceId))
        val clearedDraft = InboxReducer.reduce(keep, InboxEvent.ClearDraft)

        assertEquals(SourceDecisionValue.TRASH, trash.decisionsBySourceId[sourceId])
        assertTrue(clearedItem.decisionsBySourceId.isEmpty())
        assertTrue(clearedDraft.decisionsBySourceId.isEmpty())
        assertEquals(InboxLoadState.STORAGE_DEFERRED, clearedDraft.loadState)
        assertEquals(InboxDraftStatus.NOT_STARTED, clearedDraft.draftStatus)
    }

    @Test
    fun `decision summary counts known preview sources without creating a plan`() {
        val initial = InboxUiState.initial()
        val first = initial.previewItems[0].sourceId
        val second = initial.previewItems[1].sourceId
        val decided = InboxReducer.reduce(
            InboxReducer.reduce(
                initial,
                InboxEvent.SetDecision(first, SourceDecisionValue.KEEP),
            ),
            InboxEvent.SetDecision(second, SourceDecisionValue.REVIEW),
        )

        assertEquals(1, decided.decisionCount(SourceDecisionValue.KEEP))
        assertEquals(1, decided.decisionCount(SourceDecisionValue.REVIEW))
        assertEquals(0, decided.decisionCount(SourceDecisionValue.TRASH))
        assertEquals(1, decided.undecidedCount)
        assertTrue(decided.hasDraft)
        assertTrue(!decided.isDraftComplete)
        assertEquals(InboxDraftStatus.IN_PROGRESS, decided.draftStatus)
    }

    @Test
    fun `draft is complete only when every known preview source has a decision`() {
        val initial = InboxUiState.initial()
        val completed = initial.previewItems.fold(initial) { state, item ->
            InboxReducer.reduce(
                state,
                InboxEvent.SetDecision(item.sourceId, SourceDecisionValue.KEEP),
            )
        }

        assertEquals(0, completed.undecidedCount)
        assertTrue(completed.isDraftComplete)
        assertEquals(3, completed.decisionCount(SourceDecisionValue.KEEP))
        assertEquals(InboxDraftStatus.COMPLETE, completed.draftStatus)
    }
}
