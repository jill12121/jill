package com.galleryorganizer.presentation

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
        assertTrue(cleared.selectedAlbumIds.isEmpty())
        assertEquals(0, cleared.selectionCount)
        assertFalse(cleared.visibleAlbums.isEmpty())
    }
}
