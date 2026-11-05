package com.galleryorganizer.domain

import com.galleryorganizer.contract.SourceFileRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceMetadataTest {
    @Test
    fun `source key binds location size and normalized modification time`() {
        val item = item()

        assertEquals(
            "DOWNLOAD:MEDIASTORE:Download/set/image.jpg:100:2000",
            item.sourceKey(),
        )
        assertFalse(item.sourceKey() == item.copy(sizeBytes = 101).sourceKey())
        assertFalse(item.sourceKey() == item.copy(modifiedAtEpochMillis = 3000).sourceKey())
    }

    @Test
    fun `only durable import states count as imported`() {
        assertTrue(ImportRecordState.COMMITTED in IMPORTED_STATES)
        assertTrue(ImportRecordState.CLEANUP_DENIED in IMPORTED_STATES)
        assertFalse(ImportRecordState.PENDING_COPY in IMPORTED_STATES)
        assertFalse(ImportRecordState.COPIED in IMPORTED_STATES)
        assertFalse(ImportRecordState.SOURCE_CHANGED in IMPORTED_STATES)
        assertFalse(ImportRecordState.FAILED in IMPORTED_STATES)
    }

    @Test
    fun `source page request enforces bounded keyset input`() {
        assertFailsWith<IllegalArgumentException> {
            SourcePageRequest(batchKey = "", afterFileId = null, limit = 20)
        }
        assertFailsWith<IllegalArgumentException> {
            SourcePageRequest(batchKey = "batch", afterFileId = "", limit = 20)
        }
        assertFailsWith<IllegalArgumentException> {
            SourcePageRequest(batchKey = "batch", afterFileId = null, limit = 101)
        }
        assertEquals(
            100,
            SourcePageRequest("batch", afterFileId = "file-1", limit = 100).limit,
        )
    }

    @Test
    fun `source page cursor points to last emitted item only when more remain`() {
        val first = persistedItem("mediastore:image:1")
        val second = persistedItem("mediastore:image:2")

        val continued = PersistedSourcePage.from(listOf(first, second), requestedLimit = 1)
        val complete = PersistedSourcePage.from(listOf(first), requestedLimit = 1)

        assertEquals(listOf(first), continued.items)
        assertEquals(SourcePageCursor(first.fileId), continued.nextCursor)
        assertEquals(listOf(first), complete.items)
        assertNull(complete.nextCursor)
    }

    private fun item() = DiscoveredSourceItem(
        mediaId = 1,
        ref = SourceFileRef("mediastore:image:1"),
        relativePath = "Download/set",
        displayName = "image.jpg",
        sizeBytes = 100,
        modifiedAtEpochMillis = 2000,
        mimeType = "image/jpeg",
        subgroupRelativePath = null,
    )

    private fun persistedItem(fileId: String) = PersistedSourceItem(
        batchKey = "batch",
        fileId = fileId,
        sourceKey = "source:$fileId",
        displayName = "$fileId.jpg",
        scanGeneration = 1,
        sizeBytes = 100,
        modifiedAtEpochMillis = 2000,
        mimeType = "image/jpeg",
        decision = null,
        imported = false,
    )
}
