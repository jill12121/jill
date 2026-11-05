package com.galleryorganizer.domain

import com.galleryorganizer.contract.SourceFileRef
import com.galleryorganizer.contract.SourceMediaMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceDiscoveryPolicyTest {
    @Test
    fun `first level directories become batches and retain nested subgroup paths`() {
        val result = SourceDiscoveryPolicy.build(
            media = listOf(
                media(1, "Download/Trip/", "cover.jpg"),
                media(2, "Download/Trip/Day 1/", "one.jpg"),
                media(3, "Download/Trip/Day 1/Night/", "two.png"),
                media(4, "Download/Receipts/", "receipt.webp"),
            ),
            config = config(selected = setOf("Trip")),
        )

        assertEquals(listOf("Receipts", "Trip"), result.batches.map { it.displayName })
        val trip = result.batches.single { it.displayName == "Trip" }
        assertEquals(3, trip.imageCount)
        assertTrue(trip.isMonitored)
        assertEquals(
            listOf(null, "Day 1", "Day 1/Night"),
            trip.items.map { it.subgroupRelativePath },
        )
    }

    @Test
    fun `download root images form a virtual batch disabled by default`() {
        val defaultResult = SourceDiscoveryPolicy.build(
            media = listOf(media(1, "Download/", "loose.jpg")),
            config = config(),
        )

        val loose = defaultResult.batches.single()
        assertEquals(SourceBatchKind.ROOT_LOOSE_IMAGES, loose.kind)
        assertEquals("Loose images", loose.displayName)
        assertFalse(loose.isMonitored)

        val optedIn = SourceDiscoveryPolicy.build(
            media = listOf(media(1, "Download/", "loose.jpg")),
            config = config(includeRootLooseImages = true),
        )
        assertTrue(optedIn.batches.single().isMonitored)
    }

    @Test
    fun `organizer root and protected prefixes are excluded on component boundaries`() {
        val result = SourceDiscoveryPolicy.build(
            media = listOf(
                media(1, "Download/GalleryOrganizer/", "archive.jpg"),
                media(2, "Download/GalleryOrganizer/Albums/A/", "nested.jpg"),
                media(3, "Download/Private/", "secret.jpg"),
                media(4, "Download/Private/Sub/", "deeper.jpg"),
                media(5, "Download/PrivateNotes/", "keep.jpg"),
                media(6, "Pictures/Elsewhere/", "outside.jpg"),
            ),
            config = config(protected = setOf("Download/Private")),
        )

        assertEquals(listOf("PrivateNotes"), result.batches.map { it.displayName })
        assertEquals(listOf(5L), result.batches.single().items.map { it.mediaId })
    }

    @Test
    fun `partial coverage keeps metadata visible but disables whole batch organization`() {
        val partial = SourceDiscoveryPolicy.build(
            media = listOf(media(1, "Download/Trip/", "one.jpg")),
            config = config(coverage = ScanCoverage.PARTIAL_USER_SELECTED),
        )
        val full = SourceDiscoveryPolicy.build(
            media = listOf(media(1, "Download/Trip/", "one.jpg")),
            config = config(coverage = ScanCoverage.FULL),
        )

        assertEquals(1, partial.batches.single().imageCount)
        assertFalse(partial.canOrganizeWholeBatch)
        assertFalse(partial.batches.single().canOrganizeWholeBatch)
        assertTrue(partial.canImportExplicitSelection)
        assertTrue(full.canOrganizeWholeBatch)
        assertTrue(full.batches.single().canOrganizeWholeBatch)
    }

    @Test
    fun `non image and malformed metadata is ignored without inventing batches`() {
        val result = SourceDiscoveryPolicy.build(
            media = listOf(
                media(1, "Download/Trip/", "note.txt", mimeType = "text/plain"),
                media(2, "", "missing.jpg"),
                media(3, "Download/Trip/", "photo.jpg", mimeType = "image/jpeg"),
            ),
            config = config(),
        )

        assertEquals(1, result.batches.single().imageCount)
        assertEquals(3L, result.batches.single().items.single().mediaId)
    }

    private fun config(
        selected: Set<String> = emptySet(),
        protected: Set<String> = emptySet(),
        includeRootLooseImages: Boolean = false,
        coverage: ScanCoverage = ScanCoverage.FULL,
    ) = SourceDiscoveryConfig(
        organizerRootRelativePath = "Download/GalleryOrganizer",
        protectedDirectoryPaths = protected,
        monitoredFirstLevelDirectories = selected,
        includeRootLooseImages = includeRootLooseImages,
        coverage = coverage,
        completed = true,
    )

    private fun media(
        id: Long,
        relativePath: String,
        displayName: String,
        mimeType: String = "image/jpeg",
    ) = SourceMediaMetadata(
        mediaId = id,
        ref = SourceFileRef("media:$id"),
        relativePath = relativePath,
        displayName = displayName,
        sizeBytes = 100,
        modifiedAtEpochMillis = 200,
        mimeType = mimeType,
    )
}
