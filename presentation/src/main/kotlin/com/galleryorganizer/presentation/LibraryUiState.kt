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

data class LibrarySelectionSummary(
    val selectedAlbums: List<PreviewAlbum>,
) {
    val selectionCount: Int
        get() = selectedAlbums.size

    val hasSelection: Boolean
        get() = selectedAlbums.isNotEmpty()
}

data class LibraryUiState(
    val searchQuery: String,
    val filter: LibraryFilter,
    val sort: LibrarySort,
    val selectedAlbumIds: Set<String>,
    val loadState: LibraryLoadState,
    val previewAlbums: List<PreviewAlbum>,
) {
    val selectionSummary: LibrarySelectionSummary
        get() = LibrarySelectionSummary(
            selectedAlbums = previewAlbums.filter {
                it.albumId in selectedAlbumIds
            },
        )

    val selectedPreviewAlbums: List<PreviewAlbum>
        get() = selectionSummary.selectedAlbums

    val selectionCount: Int
        get() = selectionSummary.selectionCount

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

/**
 * Memory-only, read-only combination of the Library selection preview and the
 * Inbox decision preview. It is derived on demand for display, creates no Plan,
 * and never touches storage.
 */
data class WorkspaceReviewSnapshot(
    val librarySelectionSummary: LibrarySelectionSummary,
    val inboxDecisionSummary: InboxDecisionSummary,
) {
    val selectedAlbumCount: Int
        get() = librarySelectionSummary.selectionCount

    val decidedSourceCount: Int
        get() = inboxDecisionSummary.decidedCount

    val undecidedSourceCount: Int
        get() = inboxDecisionSummary.undecidedCount

    val inboxDraftStatus: InboxDraftStatus
        get() = inboxDecisionSummary.status

    val hasReviewActivity: Boolean
        get() = librarySelectionSummary.hasSelection || decidedSourceCount > 0

    companion object {
        fun from(
            libraryState: LibraryUiState,
            inboxState: InboxUiState,
        ): WorkspaceReviewSnapshot = WorkspaceReviewSnapshot(
            librarySelectionSummary = libraryState.selectionSummary,
            inboxDecisionSummary = inboxState.decisionSummary,
        )
    }
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
