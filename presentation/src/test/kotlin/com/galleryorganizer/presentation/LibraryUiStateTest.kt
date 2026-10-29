package com.galleryorganizer.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryUiStateTest {
    @Test
    fun `initial library is an empty local workspace`() {
        val state = LibraryUiState.initial()

        assertEquals("", state.searchQuery)
        assertEquals(LibraryFilter.ALL, state.filter)
        assertEquals(LibrarySort.NEWEST_FIRST, state.sort)
        assertEquals(LibraryLoadState.STORAGE_DEFERRED, state.loadState)
        assertTrue(state.selectedAlbumIds.isEmpty())
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
}
