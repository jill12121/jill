package com.galleryorganizer.data.mediastore

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.galleryorganizer.contract.ThumbnailFailure
import com.galleryorganizer.contract.ThumbnailRequest
import com.galleryorganizer.contract.ThumbnailResult
import com.galleryorganizer.contract.ThumbnailSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.math.min

private const val MEDIASTORE_IMAGE_PREFIX = "mediastore:image:"
private const val MAX_CACHE_BYTES = 64L * 1024L * 1024L

internal fun interface ThumbnailBitmapLoader {
    fun load(mediaId: Long, edgePx: Int): Bitmap
}

fun createMediaStoreThumbnailSource(context: Context): ThumbnailSource<Bitmap> =
    MediaStoreThumbnailSource(
        loader = ContentResolverThumbnailBitmapLoader(context.contentResolver),
        maxMemoryBytes = Runtime.getRuntime().maxMemory(),
    )

internal fun thumbnailCacheByteBudget(maxMemoryBytes: Long): Long {
    require(maxMemoryBytes > 0)
    return min(maxMemoryBytes / 20L, MAX_CACHE_BYTES).coerceAtLeast(1L)
}

internal class MediaStoreThumbnailSource(
    private val loader: ThumbnailBitmapLoader,
    maxMemoryBytes: Long,
) : ThumbnailSource<Bitmap> {
    private val cache = object : LruCache<String, Bitmap>(
        thumbnailCacheByteBudget(maxMemoryBytes).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    override suspend fun load(request: ThumbnailRequest): ThumbnailResult<Bitmap> =
        withContext(Dispatchers.IO) {
            val mediaId = request.ref.stableKey
                .takeIf { it.startsWith(MEDIASTORE_IMAGE_PREFIX) }
                ?.removePrefix(MEDIASTORE_IMAGE_PREFIX)
                ?.toLongOrNull()
                ?: return@withContext ThumbnailResult.Unavailable(
                    ThumbnailFailure.INVALID_REFERENCE,
                )
            val cacheKey = "$mediaId:${request.edgePx}"
            cache.get(cacheKey)?.let {
                return@withContext ThumbnailResult.Loaded(it, fromMemoryCache = true)
            }
            try {
                val bitmap = loader.load(mediaId, request.edgePx)
                cache.put(cacheKey, bitmap)
                ThumbnailResult.Loaded(bitmap, fromMemoryCache = false)
            } catch (_: SecurityException) {
                ThumbnailResult.Unavailable(ThumbnailFailure.ACCESS_DENIED)
            } catch (_: FileNotFoundException) {
                ThumbnailResult.Unavailable(ThumbnailFailure.NOT_FOUND)
            } catch (_: IOException) {
                ThumbnailResult.Unavailable(ThumbnailFailure.PROVIDER_FAILURE)
            } catch (_: RuntimeException) {
                ThumbnailResult.Unavailable(ThumbnailFailure.PROVIDER_FAILURE)
            }
        }
}

private class ContentResolverThumbnailBitmapLoader(
    private val resolver: ContentResolver,
) : ThumbnailBitmapLoader {
    override fun load(mediaId: Long, edgePx: Int): Bitmap {
        val uri = Uri.withAppendedPath(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            mediaId.toString(),
        )
        return resolver.loadThumbnail(
            uri,
            Size(edgePx, edgePx),
            CancellationSignal(),
        )
    }
}
