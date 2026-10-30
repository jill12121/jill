package com.galleryorganizer.presentation

import com.galleryorganizer.domain.ImportStrategy

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

data class WorkspaceSetupSummary(
    val selectedScopeCount: Int,
    val protectedDirectoryCount: Int,
    val trashRetention: TrashRetention,
    val importStrategy: ImportStrategy,
    val notificationPreference: NotificationPreference,
    val rootAdoptionState: RootAdoptionState,
    val rootCapability: RootCapability,
) {
    companion object {
        fun from(state: SetupUiState): WorkspaceSetupSummary = WorkspaceSetupSummary(
            selectedScopeCount = state.selectedScopeIds.size,
            protectedDirectoryCount = state.protectedDirectoryIds.size,
            trashRetention = state.trashRetention,
            importStrategy = state.importStrategy,
            notificationPreference = state.notificationPreference,
            rootAdoptionState = state.rootAdoptionState,
            rootCapability = state.rootCapability,
        )
    }
}

data class WorkspaceAttentionSummary(
    val selectedNeedsReviewAlbumCount: Int,
    val reviewSourceCount: Int,
    val trashSourceCount: Int,
) {
    val attentionItemCount: Int
        get() = selectedNeedsReviewAlbumCount + reviewSourceCount + trashSourceCount

    val hasAttentionItems: Boolean
        get() = attentionItemCount > 0

    val hasTrashPreview: Boolean
        get() = trashSourceCount > 0
}

/**
 * Memory-only, read-only combination of setup, Library selection, and Inbox
 * decision previews. It is derived on demand for display, creates no Plan,
 * and never touches storage.
 */
data class WorkspaceReviewSnapshot(
    val librarySelectionSummary: LibrarySelectionSummary,
    val inboxDecisionSummary: InboxDecisionSummary,
    val setupSummary: WorkspaceSetupSummary,
) {
    val selectedAlbumCount: Int
        get() = librarySelectionSummary.selectionCount

    val selectedNeedsReviewAlbumCount: Int
        get() = librarySelectionSummary.selectedAlbums.count { it.needsReview }

    val keepSourceCount: Int
        get() = inboxDecisionSummary.keepCount

    val reviewSourceCount: Int
        get() = inboxDecisionSummary.reviewCount

    val trashSourceCount: Int
        get() = inboxDecisionSummary.trashCount

    val decidedSourceCount: Int
        get() = inboxDecisionSummary.decidedCount

    val undecidedSourceCount: Int
        get() = inboxDecisionSummary.undecidedCount

    val inboxDraftStatus: InboxDraftStatus
        get() = inboxDecisionSummary.status

    val hasReviewActivity: Boolean
        get() = librarySelectionSummary.hasSelection || decidedSourceCount > 0

    val attentionSummary: WorkspaceAttentionSummary
        get() = WorkspaceAttentionSummary(
            selectedNeedsReviewAlbumCount = selectedNeedsReviewAlbumCount,
            reviewSourceCount = reviewSourceCount,
            trashSourceCount = trashSourceCount,
        )

    companion object {
        fun from(
            libraryState: LibraryUiState,
            inboxState: InboxUiState,
        ): WorkspaceReviewSnapshot = WorkspaceReviewSnapshot(
            librarySelectionSummary = libraryState.selectionSummary,
            inboxDecisionSummary = inboxState.decisionSummary,
            setupSummary = WorkspaceSetupSummary.from(SetupUiState.initial()),
        )

        fun from(
            setupState: SetupUiState,
            libraryState: LibraryUiState,
            inboxState: InboxUiState,
        ): WorkspaceReviewSnapshot = WorkspaceReviewSnapshot(
            librarySelectionSummary = libraryState.selectionSummary,
            inboxDecisionSummary = inboxState.decisionSummary,
            setupSummary = WorkspaceSetupSummary.from(setupState),
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
