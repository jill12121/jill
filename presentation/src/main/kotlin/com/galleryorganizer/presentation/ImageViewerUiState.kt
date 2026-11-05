package com.galleryorganizer.presentation

enum class ImageViewerLoadState {
    IDLE,
    LOADING,
    READY,
    UNAVAILABLE,
}

enum class ImageViewerZoom {
    FIT,
    DETAIL,
}

data class ImageViewerUiState(
    val items: List<InboxPreviewItem> = emptyList(),
    val selectedIndex: Int = -1,
    val loadState: ImageViewerLoadState = ImageViewerLoadState.IDLE,
    val zoom: ImageViewerZoom = ImageViewerZoom.FIT,
) {
    val isOpen: Boolean
        get() = currentItem != null

    val currentItem: InboxPreviewItem?
        get() = items.getOrNull(selectedIndex)

    val positionLabel: String
        get() = if (isOpen) "${selectedIndex + 1} / ${items.size}" else ""

    val canGoPrevious: Boolean
        get() = selectedIndex > 0

    val canGoNext: Boolean
        get() = selectedIndex >= 0 && selectedIndex < items.lastIndex
}

sealed interface ImageViewerEvent {
    data class Open(
        val items: List<InboxPreviewItem>,
        val sourceId: String,
    ) : ImageViewerEvent

    data class PreviewLoaded(val sourceId: String) : ImageViewerEvent
    data class PreviewUnavailable(val sourceId: String) : ImageViewerEvent
    data object Previous : ImageViewerEvent
    data object Next : ImageViewerEvent
    data object ToggleZoom : ImageViewerEvent
    data object Close : ImageViewerEvent
}

object ImageViewerReducer {
    const val PREVIEW_EDGE_PX = 512

    fun reduce(
        state: ImageViewerUiState,
        event: ImageViewerEvent,
    ): ImageViewerUiState = when (event) {
        is ImageViewerEvent.Open -> {
            val items = event.items.distinctBy(InboxPreviewItem::sourceId)
            val selectedIndex = items.indexOfFirst { it.sourceId == event.sourceId }
            if (selectedIndex < 0) {
                state
            } else {
                ImageViewerUiState(
                    items = items,
                    selectedIndex = selectedIndex,
                    loadState = ImageViewerLoadState.LOADING,
                )
            }
        }
        is ImageViewerEvent.PreviewLoaded ->
            if (state.currentItem?.sourceId == event.sourceId) {
                state.copy(loadState = ImageViewerLoadState.READY)
            } else {
                state
            }
        is ImageViewerEvent.PreviewUnavailable ->
            if (state.currentItem?.sourceId == event.sourceId) {
                state.copy(loadState = ImageViewerLoadState.UNAVAILABLE)
            } else {
                state
            }
        ImageViewerEvent.Previous -> state.moveTo(state.selectedIndex - 1)
        ImageViewerEvent.Next -> state.moveTo(state.selectedIndex + 1)
        ImageViewerEvent.ToggleZoom ->
            if (state.loadState != ImageViewerLoadState.READY) {
                state
            } else {
                state.copy(
                    zoom = if (state.zoom == ImageViewerZoom.FIT) {
                        ImageViewerZoom.DETAIL
                    } else {
                        ImageViewerZoom.FIT
                    },
                )
            }
        ImageViewerEvent.Close -> ImageViewerUiState()
    }

    private fun ImageViewerUiState.moveTo(index: Int): ImageViewerUiState =
        if (index !in items.indices || index == selectedIndex) {
            this
        } else {
            copy(
                selectedIndex = index,
                loadState = ImageViewerLoadState.LOADING,
                zoom = ImageViewerZoom.FIT,
            )
        }
}
