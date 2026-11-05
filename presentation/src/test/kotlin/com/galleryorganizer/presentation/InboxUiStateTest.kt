package com.galleryorganizer.presentation

import com.galleryorganizer.domain.SourceDecisionValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InboxUiStateTest {
    @Test
    fun `production inbox starts empty and storage idle`() {
        val state = InboxUiState.production()

        assertEquals(InboxLoadState.IDLE, state.loadState)
        assertTrue(state.batches.isEmpty())
        assertTrue(state.previewItems.isEmpty())
        assertNull(state.selectedBatchKey)
        assertFalse(state.hasNextPage)
    }

    @Test
    fun `selecting a known batch resets prior page and starts loading`() {
        val batches = listOf(InboxBatchItem("batch-a", "Batch A", 2))
        val available = InboxReducer.reduce(
            InboxUiState.production(),
            InboxEvent.BatchesLoaded(batches),
        )
        val selected = InboxReducer.reduce(available, InboxEvent.SelectBatch("batch-a"))
        val ignored = InboxReducer.reduce(selected, InboxEvent.SelectBatch("missing"))

        assertEquals("batch-a", selected.selectedBatchKey)
        assertEquals(InboxLoadState.LOADING, selected.loadState)
        assertTrue(selected.previewItems.isEmpty())
        assertEquals(selected, ignored)
    }

    @Test
    fun `paged sources append without duplicates and preserve persisted decisions`() {
        val selected = InboxReducer.reduce(
            InboxReducer.reduce(
                InboxUiState.production(),
                InboxEvent.BatchesLoaded(listOf(InboxBatchItem("batch-a", "Batch A", 3))),
            ),
            InboxEvent.SelectBatch("batch-a"),
        )
        val first = InboxReducer.reduce(
            selected,
            InboxEvent.PageLoaded(
                batchKey = "batch-a",
                items = listOf(source("1", SourceDecisionValue.REVIEW), source("2")),
                nextCursorFileId = "2",
                append = false,
            ),
        )
        val edited = InboxReducer.reduce(
            first,
            InboxEvent.SetDecision("1", SourceDecisionValue.KEEP),
        )
        val appended = InboxReducer.reduce(
            edited,
            InboxEvent.PageLoaded(
                batchKey = "batch-a",
                items = listOf(source("2"), source("3")),
                nextCursorFileId = null,
                append = true,
            ),
        )

        assertEquals(listOf("1", "2", "3"), appended.previewItems.map { it.sourceId })
        assertEquals(SourceDecisionValue.KEEP, appended.decisionsBySourceId["1"])
        assertEquals(InboxLoadState.READY, appended.loadState)
        assertFalse(appended.hasNextPage)
    }

    @Test
    fun `paged sources retain only the most recent bounded window`() {
        val selected = InboxReducer.reduce(
            InboxReducer.reduce(
                InboxUiState.production(),
                InboxEvent.BatchesLoaded(listOf(InboxBatchItem("batch-a", "Batch A", 120))),
            ),
            InboxEvent.SelectBatch("batch-a"),
        )
        val first = InboxReducer.reduce(
            selected,
            InboxEvent.PageLoaded(
                batchKey = "batch-a",
                items = (1..72).map { source(it.toString()) },
                nextCursorFileId = "72",
                append = false,
            ),
        )
        val decided = InboxReducer.reduce(
            first,
            InboxEvent.SetDecision("1", SourceDecisionValue.KEEP),
        )
        val appended = InboxReducer.reduce(
            decided,
            InboxEvent.PageLoaded(
                batchKey = "batch-a",
                items = (73..120).map { source(it.toString()) },
                nextCursorFileId = null,
                append = true,
            ),
        )

        assertEquals(InboxReducer.MAX_LOADED_SOURCE_ITEMS, appended.previewItems.size)
        assertEquals("25", appended.previewItems.first().sourceId)
        assertEquals("120", appended.previewItems.last().sourceId)
        assertFalse("1" in appended.decisionsBySourceId)
    }

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
        assertEquals(
            InboxDecisionSummary(
                keepCount = 0,
                reviewCount = 0,
                trashCount = 0,
                undecidedCount = 3,
                status = InboxDraftStatus.NOT_STARTED,
            ),
            state.decisionSummary,
        )
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
        assertEquals(1, ignored.decisionSummary.reviewCount)
        assertEquals(2, ignored.decisionSummary.undecidedCount)
        assertEquals(InboxDraftStatus.IN_PROGRESS, ignored.decisionSummary.status)
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
        assertEquals(
            InboxDecisionSummary(
                keepCount = 1,
                reviewCount = 1,
                trashCount = 0,
                undecidedCount = 1,
                status = InboxDraftStatus.IN_PROGRESS,
            ),
            decided.decisionSummary,
        )
        assertEquals(
            listOf(first, second),
            decided.decisionReviewItems.map { it.item.sourceId },
        )
        assertEquals(
            listOf(
                SourceDecisionValue.KEEP,
                SourceDecisionValue.REVIEW,
            ),
            decided.decisionReviewItems.map { it.decision },
        )
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
        assertEquals(3, completed.decisionSummary.decidedCount)
        assertEquals(InboxDraftStatus.COMPLETE, completed.decisionSummary.status)
        assertEquals(3, completed.decisionReviewItems.size)
        assertTrue(
            InboxReducer.reduce(
                completed,
                InboxEvent.ClearDraft,
            ).decisionReviewItems.isEmpty(),
        )
    }

    private fun source(
        id: String,
        decision: SourceDecisionValue? = null,
    ) = InboxPreviewItem(
        sourceId = id,
        sourceKey = "source:$id",
        label = "$id.jpg",
        detail = "100 bytes",
        persistedDecision = decision,
    )
}
