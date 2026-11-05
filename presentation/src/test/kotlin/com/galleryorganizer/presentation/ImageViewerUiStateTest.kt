package com.galleryorganizer.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageViewerUiStateTest {
    @Test
    fun `viewer opens only a known source in the bounded item snapshot`() {
        val items = listOf(source("1"), source("2"), source("2"))
        val opened = ImageViewerReducer.reduce(
            ImageViewerUiState(),
            ImageViewerEvent.Open(items, "2"),
        )
        val ignored = ImageViewerReducer.reduce(
            opened,
            ImageViewerEvent.Open(items, "missing"),
        )

        assertTrue(opened.isOpen)
        assertEquals(2, opened.items.size)
        assertEquals("2", opened.currentItem?.sourceId)
        assertEquals("2 / 2", opened.positionLabel)
        assertEquals(ImageViewerLoadState.LOADING, opened.loadState)
        assertEquals(opened, ignored)
    }

    @Test
    fun `navigation is bounded and resets preview and zoom state`() {
        val opened = ImageViewerReducer.reduce(
            ImageViewerUiState(),
            ImageViewerEvent.Open(listOf(source("1"), source("2")), "1"),
        )
        val loaded = ImageViewerReducer.reduce(
            opened,
            ImageViewerEvent.PreviewLoaded("1"),
        )
        val zoomed = ImageViewerReducer.reduce(loaded, ImageViewerEvent.ToggleZoom)
        val next = ImageViewerReducer.reduce(zoomed, ImageViewerEvent.Next)
        val bounded = ImageViewerReducer.reduce(next, ImageViewerEvent.Next)

        assertEquals(ImageViewerZoom.DETAIL, zoomed.zoom)
        assertEquals("2", next.currentItem?.sourceId)
        assertEquals(ImageViewerZoom.FIT, next.zoom)
        assertEquals(ImageViewerLoadState.LOADING, next.loadState)
        assertFalse(next.canGoNext)
        assertTrue(next.canGoPrevious)
        assertEquals(next, bounded)
    }

    @Test
    fun `late preview results cannot replace the current source state`() {
        val opened = ImageViewerReducer.reduce(
            ImageViewerUiState(),
            ImageViewerEvent.Open(listOf(source("1"), source("2")), "1"),
        )
        val next = ImageViewerReducer.reduce(opened, ImageViewerEvent.Next)
        val stale = ImageViewerReducer.reduce(
            next,
            ImageViewerEvent.PreviewLoaded("1"),
        )
        val failed = ImageViewerReducer.reduce(
            stale,
            ImageViewerEvent.PreviewUnavailable("2"),
        )

        assertEquals(ImageViewerLoadState.LOADING, stale.loadState)
        assertEquals(ImageViewerLoadState.UNAVAILABLE, failed.loadState)
    }

    @Test
    fun `closing clears the viewer snapshot`() {
        val opened = ImageViewerReducer.reduce(
            ImageViewerUiState(),
            ImageViewerEvent.Open(listOf(source("1")), "1"),
        )
        val closed = ImageViewerReducer.reduce(opened, ImageViewerEvent.Close)

        assertFalse(closed.isOpen)
        assertTrue(closed.items.isEmpty())
        assertEquals(ImageViewerLoadState.IDLE, closed.loadState)
    }

    private fun source(id: String) = InboxPreviewItem(
        sourceId = id,
        sourceKey = "source:$id",
        label = "$id.jpg",
        detail = "100 bytes",
    )
}
