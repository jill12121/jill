package com.galleryorganizer.presentation

enum class LibraryFilter(val label: String) {
    ALL("All"),
    NEEDS_REVIEW("Needs review"),
}

enum class LibrarySort(val label: String) {
    NEWEST_FIRST("Newest"),
    OLDEST_FIRST("Oldest"),
    TITLE("Title"),
}

enum class LibraryLoadState {
    STORAGE_DEFERRED,
}

data class LibraryUiState(
    val searchQuery: String,
    val filter: LibraryFilter,
    val sort: LibrarySort,
    val selectedAlbumIds: Set<String>,
    val loadState: LibraryLoadState,
) {
    companion object {
        fun initial(): LibraryUiState = LibraryUiState(
            searchQuery = "",
            filter = LibraryFilter.ALL,
            sort = LibrarySort.NEWEST_FIRST,
            selectedAlbumIds = emptySet(),
            loadState = LibraryLoadState.STORAGE_DEFERRED,
        )
    }
}

sealed interface LibraryEvent {
    data class SearchChanged(val query: String) : LibraryEvent
    data class FilterChanged(val filter: LibraryFilter) : LibraryEvent
    data class SortChanged(val sort: LibrarySort) : LibraryEvent
    data object ClearSearch : LibraryEvent
}

object LibraryReducer {
    private const val MAX_SEARCH_LENGTH = 80

    fun reduce(state: LibraryUiState, event: LibraryEvent): LibraryUiState = when (event) {
        is LibraryEvent.SearchChanged ->
            state.copy(searchQuery = event.query.take(MAX_SEARCH_LENGTH))
        is LibraryEvent.FilterChanged -> state.copy(filter = event.filter)
        is LibraryEvent.SortChanged -> state.copy(sort = event.sort)
        LibraryEvent.ClearSearch -> state.copy(searchQuery = "")
    }
}
