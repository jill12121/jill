package com.galleryorganizer.presentation

import com.galleryorganizer.domain.SourceDecisionValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryUiStateTest {
    @Test
    fun `initial library is a bounded memory preview with deferred storage`() {
        val state = LibraryUiState.initial()

        assertEquals("", state.searchQuery)
        assertEquals(LibraryFilter.ALL, state.filter)
        assertEquals(LibrarySort.NEWEST_FIRST, state.sort)
        assertEquals(LibraryLoadState.STORAGE_DEFERRED, state.loadState)
        assertTrue(state.selectedAlbumIds.isEmpty())
        assertEquals(3, state.previewAlbums.size)
        assertEquals(3, state.visibleAlbums.size)
        assertEquals(0, state.selectionCount)
        assertTrue(state.selectedPreviewAlbums.isEmpty())
        assertEquals(
            LibrarySelectionSummary(selectedAlbums = emptyList()),
            state.selectionSummary,
        )
        assertFalse(state.selectionSummary.hasSelection)
    }

    @Test
    fun `search filter and sort edits stay in memory`() {
        val searched = LibraryReducer.reduce(
            LibraryUiState.initial(),
            LibraryEvent.SearchChanged("autumn"),
        )
        val filtered = LibraryReducer.reduce(
            searched,
            LibraryEvent.FilterChanged(LibraryFilter.NEEDS_REVIEW),
        )
        val sorted = LibraryReducer.reduce(
            filtered,
            LibraryEvent.SortChanged(LibrarySort.TITLE),
        )

        assertEquals("autumn", sorted.searchQuery)
        assertEquals(LibraryFilter.NEEDS_REVIEW, sorted.filter)
        assertEquals(LibrarySort.TITLE, sorted.sort)
        assertEquals(LibraryLoadState.STORAGE_DEFERRED, sorted.loadState)
        assertEquals(1, sorted.visibleAlbums.size)
        assertEquals("Autumn references", sorted.visibleAlbums.single().title)
    }

    @Test
    fun `search is bounded and can be cleared`() {
        val longQuery = "a".repeat(120)
        val searched = LibraryReducer.reduce(
            LibraryUiState.initial(),
            LibraryEvent.SearchChanged(longQuery),
        )
        val cleared = LibraryReducer.reduce(searched, LibraryEvent.ClearSearch)

        assertEquals(80, searched.searchQuery.length)
        assertEquals("", cleared.searchQuery)
    }

    @Test
    fun `album selection accepts known preview ids and can be cleared`() {
        val initial = LibraryUiState.initial()
        val selected = LibraryReducer.reduce(
            initial,
            LibraryEvent.ToggleAlbumSelection(initial.previewAlbums.first().albumId),
        )
        val ignored = LibraryReducer.reduce(
            selected,
            LibraryEvent.ToggleAlbumSelection("unknown"),
        )
        val cleared = LibraryReducer.reduce(ignored, LibraryEvent.ClearSelection)

        assertEquals(setOf(initial.previewAlbums.first().albumId), selected.selectedAlbumIds)
        assertEquals(selected.selectedAlbumIds, ignored.selectedAlbumIds)
        assertEquals(1, selected.selectionCount)
        assertEquals(
            initial.previewAlbums.first().title,
            selected.selectedPreviewAlbums.single().title,
        )
        assertEquals(
            LibrarySelectionSummary(
                selectedAlbums = listOf(initial.previewAlbums.first()),
            ),
            selected.selectionSummary,
        )
        assertTrue(selected.selectionSummary.hasSelection)
        assertEquals(1, selected.selectionSummary.selectionCount)
        assertEquals(
            selected.selectionSummary,
            ignored.selectionSummary,
        )
        assertTrue(cleared.selectedAlbumIds.isEmpty())
        assertEquals(0, cleared.selectionCount)
        assertFalse(cleared.selectionSummary.hasSelection)
        assertFalse(cleared.visibleAlbums.isEmpty())
    }

    @Test
    fun `workspace review snapshot starts empty without creating a plan`() {
        val snapshot = WorkspaceReviewSnapshot.from(
            libraryState = LibraryUiState.initial(),
            inboxState = InboxUiState.initial(),
        )

        assertEquals(0, snapshot.selectedAlbumCount)
        assertEquals(0, snapshot.decidedSourceCount)
        assertEquals(3, snapshot.undecidedSourceCount)
        assertEquals(InboxDraftStatus.NOT_STARTED, snapshot.inboxDraftStatus)
        assertFalse(snapshot.hasReviewActivity)
        assertEquals(
            LibrarySelectionSummary(selectedAlbums = emptyList()),
            snapshot.librarySelectionSummary,
        )
        assertEquals(
            InboxDecisionSummary(
                keepCount = 0,
                reviewCount = 0,
                trashCount = 0,
                undecidedCount = 3,
                status = InboxDraftStatus.NOT_STARTED,
            ),
            snapshot.inboxDecisionSummary,
        )
    }

    @Test
    fun `workspace review snapshot combines selection and inbox decisions`() {
        val libraryInitial = LibraryUiState.initial()
        val inboxInitial = InboxUiState.initial()
        val library = LibraryReducer.reduce(
            libraryInitial,
            LibraryEvent.ToggleAlbumSelection(libraryInitial.previewAlbums.first().albumId),
        )
        val inbox = inboxInitial.previewItems.take(2).fold(inboxInitial) { state, item ->
            InboxReducer.reduce(
                state,
                InboxEvent.SetDecision(item.sourceId, SourceDecisionValue.KEEP),
            )
        }

        val snapshot = WorkspaceReviewSnapshot.from(library, inbox)

        assertEquals(1, snapshot.selectedAlbumCount)
        assertEquals(2, snapshot.decidedSourceCount)
        assertEquals(1, snapshot.undecidedSourceCount)
        assertEquals(InboxDraftStatus.IN_PROGRESS, snapshot.inboxDraftStatus)
        assertTrue(snapshot.hasReviewActivity)
        assertEquals(
            listOf(libraryInitial.previewAlbums.first()),
            snapshot.librarySelectionSummary.selectedAlbums,
        )
    }

    @Test
    fun `workspace review snapshot clears back to empty after both previews are reset`() {
        val libraryInitial = LibraryUiState.initial()
        val inboxInitial = InboxUiState.initial()
        val library = LibraryReducer.reduce(
            libraryInitial,
            LibraryEvent.ToggleAlbumSelection(libraryInitial.previewAlbums.first().albumId),
        )
        val inbox = InboxReducer.reduce(
            inboxInitial,
            InboxEvent.SetDecision(
                inboxInitial.previewItems.first().sourceId,
                SourceDecisionValue.TRASH,
            ),
        )

        val libraryCleared = LibraryReducer.reduce(library, LibraryEvent.ClearSelection)
        val inboxCleared = InboxReducer.reduce(inbox, InboxEvent.ClearDraft)
        val afterLibraryClear = WorkspaceReviewSnapshot.from(libraryCleared, inbox)
        val cleared = WorkspaceReviewSnapshot.from(libraryCleared, inboxCleared)

        assertEquals(0, afterLibraryClear.selectedAlbumCount)
        assertEquals(1, afterLibraryClear.decidedSourceCount)
        assertTrue(afterLibraryClear.hasReviewActivity)
        assertEquals(0, cleared.selectedAlbumCount)
        assertEquals(0, cleared.decidedSourceCount)
        assertEquals(3, cleared.undecidedSourceCount)
        assertEquals(InboxDraftStatus.NOT_STARTED, cleared.inboxDraftStatus)
        assertFalse(cleared.hasReviewActivity)
    }

    @Test
    fun `workspace review snapshot is complete only when every inbox source is decided`() {
        val inboxInitial = InboxUiState.initial()
        val inbox = inboxInitial.previewItems.fold(inboxInitial) { state, item ->
            InboxReducer.reduce(
                state,
                InboxEvent.SetDecision(item.sourceId, SourceDecisionValue.REVIEW),
            )
        }

        val snapshot = WorkspaceReviewSnapshot.from(LibraryUiState.initial(), inbox)

        assertEquals(0, snapshot.selectedAlbumCount)
        assertEquals(3, snapshot.decidedSourceCount)
        assertEquals(0, snapshot.undecidedSourceCount)
        assertEquals(InboxDraftStatus.COMPLETE, snapshot.inboxDraftStatus)
        assertTrue(snapshot.hasReviewActivity)
    }
}
