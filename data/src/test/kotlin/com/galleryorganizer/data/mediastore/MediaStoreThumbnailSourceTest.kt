package com.galleryorganizer.data.mediastore

import android.graphics.Bitmap
import com.galleryorganizer.contract.SourceFileRef
import com.galleryorganizer.contract.ThumbnailFailure
import com.galleryorganizer.contract.ThumbnailRequest
import com.galleryorganizer.contract.ThumbnailResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class MediaStoreThumbnailSourceTest {
    @Test
    fun `cache budget uses five percent with a 64 MiB ceiling`() {
        assertEquals(5L * MIB, thumbnailCacheByteBudget(100L * MIB))
        assertEquals(64L * MIB, thumbnailCacheByteBudget(4L * 1024L * MIB))
    }

    @Test
    fun `successful thumbnail is cached by media id and edge`() = runBlocking {
        var loads = 0
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val source = MediaStoreThumbnailSource(
            loader = ThumbnailBitmapLoader { _, _ ->
                loads += 1
                bitmap
            },
            maxMemoryBytes = MIB,
        )

        val first = source.load(request("mediastore:image:42"))
        val second = source.load(request("mediastore:image:42"))

        assertIs<ThumbnailResult.Loaded<*>>(first)
        assertIs<ThumbnailResult.Loaded<*>>(second)
        assertEquals(false, first.fromMemoryCache)
        assertEquals(true, second.fromMemoryCache)
        assertEquals(1, loads)
    }

    @Test
    fun `invalid ids and loader failures fail closed without caching`() = runBlocking {
        var loads = 0
        val source = MediaStoreThumbnailSource(
            loader = ThumbnailBitmapLoader { _, _ ->
                loads += 1
                throw FileNotFoundException()
            },
            maxMemoryBytes = MIB,
        )

        val invalid = source.load(request("workspace:file:1"))
        val missingFirst = source.load(request("mediastore:image:1"))
        val missingSecond = source.load(request("mediastore:image:1"))

        assertEquals(
            ThumbnailResult.Unavailable(ThumbnailFailure.INVALID_REFERENCE),
            invalid,
        )
        assertEquals(
            ThumbnailResult.Unavailable(ThumbnailFailure.NOT_FOUND),
            missingFirst,
        )
        assertEquals(missingFirst, missingSecond)
        assertEquals(2, loads)
    }

    @Test
    fun `security and provider failures have stable mappings`() = runBlocking {
        val denied = MediaStoreThumbnailSource(
            loader = ThumbnailBitmapLoader { _, _ -> throw SecurityException() },
            maxMemoryBytes = MIB,
        )
        val provider = MediaStoreThumbnailSource(
            loader = ThumbnailBitmapLoader { _, _ -> error("provider") },
            maxMemoryBytes = MIB,
        )

        assertEquals(
            ThumbnailResult.Unavailable(ThumbnailFailure.ACCESS_DENIED),
            denied.load(request("mediastore:image:1")),
        )
        assertEquals(
            ThumbnailResult.Unavailable(ThumbnailFailure.PROVIDER_FAILURE),
            provider.load(request("mediastore:image:1")),
        )
    }

    private fun request(fileId: String) = ThumbnailRequest(SourceFileRef(fileId))

    private companion object {
        const val MIB = 1024L * 1024L
    }
}
