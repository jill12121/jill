package com.galleryorganizer.data.mediastore

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import com.galleryorganizer.contract.FsResult
import com.galleryorganizer.contract.SourceDiscoveryGateway
import com.galleryorganizer.contract.SourceDiscoveryPage
import com.galleryorganizer.contract.SourceFileRef
import com.galleryorganizer.contract.SourceMediaMetadata
import com.galleryorganizer.contract.StorageFailure
import com.galleryorganizer.domain.DefaultScanner
import com.galleryorganizer.domain.Scanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun createMediaStoreSourceDiscoveryGateway(context: Context): SourceDiscoveryGateway =
    MediaStoreSourceDiscoveryGateway(context.applicationContext.contentResolver)

fun createMediaStoreScanner(context: Context): Scanner =
    DefaultScanner(createMediaStoreSourceDiscoveryGateway(context))

class MediaStoreSourceDiscoveryGateway(
    private val resolver: ContentResolver,
) : SourceDiscoveryGateway {
    override suspend fun discoverPage(
        rootRelativePath: String,
        afterId: Long?,
        limit: Int,
    ): FsResult<SourceDiscoveryPage> = withContext(Dispatchers.IO) {
        val root = rootRelativePath.replace('\\', '/').trim().trim('/')
        if (root.isBlank() || limit !in 1..MAX_PAGE_SIZE || (afterId ?: 0) < 0) {
            return@withContext FsResult.Failure(StorageFailure.Conflict)
        }
        try {
            val startId = afterId ?: 0L
            val args = Bundle().apply {
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.Images.Media._ID} > ? AND " +
                        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? ESCAPE '\\'",
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    arrayOf(startId.toString(), "${escapeLike(root)}/%"),
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Images.Media._ID),
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_ASCENDING,
                )
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            }
            val cursor = resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                PROJECTION,
                args,
                null,
            ) ?: return@withContext FsResult.Failure(StorageFailure.ProviderUnavailable())
            val rows = cursor.use {
                val result = mutableListOf<SourceMediaMetadata>()
                var previousId = startId
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    check(id > previousId && result.size < limit)
                    val relativePath = it.getString(1)
                    val displayName = it.getString(2)
                    check(!relativePath.isNullOrBlank() && !displayName.isNullOrBlank())
                    val normalizedPath = relativePath.replace('\\', '/').trim('/')
                    check(normalizedPath.equals(root, ignoreCase = true) ||
                        normalizedPath.startsWith("$root/", ignoreCase = true))
                    result += SourceMediaMetadata(
                        mediaId = id,
                        ref = SourceFileRef("mediastore:image:$id"),
                        relativePath = relativePath,
                        displayName = displayName,
                        sizeBytes = it.getLong(3),
                        modifiedAtEpochMillis = it.getLong(4) * 1000,
                        mimeType = it.getString(5),
                    )
                    previousId = id
                }
                result
            }
            FsResult.Success(
                SourceDiscoveryPage(
                    media = rows,
                    nextAfterId = rows.lastOrNull()?.mediaId?.takeIf { rows.size == limit },
                ),
            )
        } catch (_: SecurityException) {
            FsResult.Failure(StorageFailure.AccessDenied)
        } catch (_: IllegalArgumentException) {
            FsResult.Failure(StorageFailure.ProviderUnavailable(retryable = false))
        } catch (_: IllegalStateException) {
            FsResult.Failure(StorageFailure.ProviderUnavailable(retryable = false))
        } catch (_: RuntimeException) {
            FsResult.Failure(StorageFailure.ProviderUnavailable())
        }
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        const val MAX_PAGE_SIZE = 500
        private val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE,
        )
    }
}
