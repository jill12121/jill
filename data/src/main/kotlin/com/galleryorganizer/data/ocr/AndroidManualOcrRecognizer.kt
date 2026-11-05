package com.galleryorganizer.data.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.galleryorganizer.domain.ManualOcrRecognition
import com.galleryorganizer.domain.ManualOcrRecognizer
import com.galleryorganizer.domain.ManualOcrRuleHit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MEDIASTORE_IMAGE_PREFIX = "mediastore:image:"
private const val MAX_ENCODED_BYTES = 20L * 1024L * 1024L
private const val MAX_SOURCE_PIXELS = 40_000_000L
private const val MAX_DECODED_PIXELS = 4_000_000L
private const val MAX_DECODED_EDGE = 2_048

internal data class ManualOcrAdapterPolicy(
    val enabled: Boolean = false,
    val timeoutMillis: Long = 10_000,
) {
    init {
        require(timeoutMillis in 1..60_000)
    }
}

internal fun interface ManualOcrBitmapSource {
    suspend fun load(contentKey: String): Bitmap?
}

internal fun interface ManualOcrSourceAuthorization {
    suspend fun isAuthorized(contentKey: String): Boolean
}

internal fun interface ManualOcrTextEngineFactory {
    fun create(): ManualOcrTextEngine
}

internal interface ManualOcrTextEngine : AutoCloseable {
    suspend fun recognize(bitmap: Bitmap): String
}

internal fun interface ManualOcrRuleSet {
    fun classify(contentKey: String, recognizedText: String): List<ManualOcrRuleHit>
}

internal class AndroidManualOcrRecognizer(
    private val source: ManualOcrBitmapSource,
    private val engineFactory: ManualOcrTextEngineFactory,
    private val rules: ManualOcrRuleSet,
    private val policy: ManualOcrAdapterPolicy = ManualOcrAdapterPolicy(),
) : ManualOcrRecognizer {
    override suspend fun recognize(contentKey: String): ManualOcrRecognition {
        if (!policy.enabled) return ManualOcrRecognition.Failed
        return try {
            withTimeout(policy.timeoutMillis) {
                val bitmap = source.load(contentKey) ?: return@withTimeout ManualOcrRecognition.Failed
                try {
                    engineFactory.create().use { engine ->
                        val text = engine.recognize(bitmap)
                        val hits = rules.classify(contentKey, text)
                        if (hits.any { it.contentKey != contentKey }) {
                            ManualOcrRecognition.Failed
                        } else {
                            ManualOcrRecognition.Hits(hits)
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (_: TimeoutCancellationException) {
            ManualOcrRecognition.Failed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ManualOcrRecognition.Failed
        }
    }
}

internal class MediaStoreManualOcrBitmapSource(
    private val resolver: ContentResolver,
    private val authorization: ManualOcrSourceAuthorization,
) : ManualOcrBitmapSource {
    override suspend fun load(contentKey: String): Bitmap? = withContext(Dispatchers.IO) {
        val mediaId = contentKey
            .takeIf { it.startsWith(MEDIASTORE_IMAGE_PREFIX) }
            ?.removePrefix(MEDIASTORE_IMAGE_PREFIX)
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: return@withContext null
        if (!authorization.isAuthorized(contentKey)) return@withContext null
        val uri = Uri.withAppendedPath(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            mediaId.toString(),
        )
        decodeBounded(uri)
    }

    private fun decodeBounded(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openBounded(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        val sampleSize = sampleSize(bounds.outWidth, bounds.outHeight) ?: return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = openBounded(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        if (bitmap.width > MAX_DECODED_EDGE ||
            bitmap.height > MAX_DECODED_EDGE ||
            bitmap.width.toLong() * bitmap.height > MAX_DECODED_PIXELS
        ) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    private fun openBounded(uri: Uri): InputStream? {
        val descriptor = resolver.openAssetFileDescriptor(uri, "r") ?: return null
        if (descriptor.length > MAX_ENCODED_BYTES) {
            descriptor.close()
            return null
        }
        return try {
            LimitedInputStream(descriptor.createInputStream(), MAX_ENCODED_BYTES)
        } catch (failure: Exception) {
            descriptor.close()
            throw failure
        }
    }
}

internal fun sampleSize(width: Int, height: Int): Int? {
    if (width <= 0 || height <= 0) return null
    if (width.toLong() * height > MAX_SOURCE_PIXELS) return null
    var sample = 1
    while (
        width / sample > MAX_DECODED_EDGE ||
        height / sample > MAX_DECODED_EDGE ||
        (width / sample).toLong() * (height / sample) > MAX_DECODED_PIXELS
    ) {
        sample = Math.multiplyExact(sample, 2)
    }
    return sample
}

private class LimitedInputStream(
    delegate: InputStream,
    private val limit: Long,
) : FilterInputStream(delegate) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) record(1) }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        super.read(buffer, offset, length).also { if (it > 0) record(it) }

    private fun record(bytes: Int) {
        count += bytes
        if (count > limit) throw IOException("OCR source exceeds byte limit.")
    }
}

internal class MlKitManualOcrTextEngine(
    private val delegate: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
) : ManualOcrTextEngine {
    override suspend fun recognize(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            delegate.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(it.text)
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("OCR recognition failed."),
                        )
                    }
                }
                .addOnCanceledListener {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("OCR recognition cancelled."),
                        )
                    }
                }
        }

    override fun close() {
        delegate.close()
    }
}
