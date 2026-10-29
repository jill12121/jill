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

data class PreviewAlbum(
    val albumId: String,
    val title: String,
    val dateLabel: String,
    val needsReview: Boolean,
)

data class LibraryUiState(
    val searchQuery: String,
    val filter: LibraryFilter,
    val sort: LibrarySort,
    val selectedAlbumIds: Set<String>,
    val loadState: LibraryLoadState,
    val previewAlbums: List<PreviewAlbum>,
) {
    val selectedPreviewAlbums: List<PreviewAlbum>
        get() = previewAlbums.filter { it.albumId in selectedAlbumIds }

    val selectionCount: Int
        get() = selectedPreviewAlbums.size

    val visibleAlbums: List<PreviewAlbum>
        get() {
            val query = searchQuery.trim().lowercase()
            val filtered = previewAlbums
                .filter { album ->
                    (filter == LibraryFilter.ALL || album.needsReview) &&
                        (query.isEmpty() || album.title.lowercase().contains(query))
                }
            return when (sort) {
                LibrarySort.NEWEST_FIRST -> filtered.sortedByDescending { it.dateLabel }
                LibrarySort.OLDEST_FIRST -> filtered.sortedBy { it.dateLabel }
                LibrarySort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            }
        }

    companion object {
        fun initial(): LibraryUiState = LibraryUiState(
            searchQuery = "",
            filter = LibraryFilter.ALL,
            sort = LibrarySort.NEWEST_FIRST,
            selectedAlbumIds = emptySet(),
            loadState = LibraryLoadState.STORAGE_DEFERRED,
            previewAlbums = listOf(
                PreviewAlbum(
                    albumId = "preview-autumn",
                    title = "Autumn references",
                    dateLabel = "2026-10-16",
                    needsReview = true,
                ),
                PreviewAlbum(
                    albumId = "preview-studio",
                    title = "Studio archive",
                    dateLabel = "2026-10-12",
                    needsReview = false,
                ),
                PreviewAlbum(
                    albumId = "preview-summer",
                    title = "Summer notes",
                    dateLabel = "2026-09-30",
                    needsReview = false,
                ),
            ),
        )
    }
}

sealed interface LibraryEvent {
    data class SearchChanged(val query: String) : LibraryEvent
    data class FilterChanged(val filter: LibraryFilter) : LibraryEvent
    data class SortChanged(val sort: LibrarySort) : LibraryEvent
    data class ToggleAlbumSelection(val albumId: String) : LibraryEvent
    data object ClearSearch : LibraryEvent
    data object ClearSelection : LibraryEvent
}

object LibraryReducer {
    private const val MAX_SEARCH_LENGTH = 80

    fun reduce(state: LibraryUiState, event: LibraryEvent): LibraryUiState = when (event) {
        is LibraryEvent.SearchChanged ->
            state.copy(searchQuery = event.query.take(MAX_SEARCH_LENGTH))
        is LibraryEvent.FilterChanged -> state.copy(filter = event.filter)
        is LibraryEvent.SortChanged -> state.copy(sort = event.sort)
        is LibraryEvent.ToggleAlbumSelection -> {
            if (state.previewAlbums.none { it.albumId == event.albumId }) {
                state
            } else {
                state.copy(selectedAlbumIds = state.selectedAlbumIds.toggle(event.albumId))
            }
        }
        LibraryEvent.ClearSearch -> state.copy(searchQuery = "")
        LibraryEvent.ClearSelection -> state.copy(selectedAlbumIds = emptySet())
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value
}
