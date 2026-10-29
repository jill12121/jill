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
    }
}
