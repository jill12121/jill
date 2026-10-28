package com.galleryorganizer.spikes.s05.harness

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.galleryorganizer.spikes.s05.ThumbnailBenchmarkRecorder
import java.io.File
import java.security.MessageDigest

data class FixtureSpec(
    val count: Int,
    val edgePx: Int,
    val seed: Int,
) {
    init {
        require(count in 1..512)
        require(edgePx in 16..1024)
    }
}

data class FixtureProvenance(
    val storage: String,
    val count: Int,
    val edgePx: Int,
    val seed: Int,
    val totalBytes: Long,
    val sha256: String,
)

class AppPrivateFixtureStore(context: Context) {
    private val root = File(context.filesDir, FIXTURE_DIRECTORY)
    var files: List<File> = emptyList()
        private set

    fun prepare(spec: FixtureSpec): FixtureProvenance {
        check(root.deleteRecursively() || !root.exists())
        check(root.mkdirs())
        files = (0 until spec.count).map { index ->
            File(root, "fixture-${index.toString().padStart(4, '0')}.png").also { file ->
                val bitmap = Bitmap.createBitmap(spec.edgePx, spec.edgePx, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(fixtureColor(spec.seed, index))
                file.outputStream().buffered().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                bitmap.recycle()
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        files.forEach { file -> file.inputStream().buffered().use { it.copyTo(digest.asOutputStream()) } }
        return FixtureProvenance(
            storage = "filesDir/$FIXTURE_DIRECTORY",
            count = files.size,
            edgePx = spec.edgePx,
            seed = spec.seed,
            totalBytes = files.sumOf(File::length),
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
        )
    }

    private fun fixtureColor(seed: Int, index: Int): Int {
        val value = seed * 1103515245 + index * 12345
        return Color.rgb(value ushr 16 and 0xff, value ushr 8 and 0xff, value and 0xff)
    }

    private fun MessageDigest.asOutputStream() = object : java.io.OutputStream() {
        override fun write(value: Int) = update(value.toByte())
        override fun write(bytes: ByteArray, offset: Int, length: Int) = update(bytes, offset, length)
    }

    private companion object {
        const val FIXTURE_DIRECTORY = "s05-thumbnail-fixtures"
    }
}

class AppPrivateThumbnailFetcher(
    private val recorder: ThumbnailBenchmarkRecorder,
    private val nanoTime: () -> Long = System::nanoTime,
    private val heapBytes: () -> Long = ::usedHeapBytes,
) {
    fun fetch(file: File): Bitmap? {
        val started = nanoTime()
        return try {
            val bitmap = file.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }
            bitmap.also {
                recorder.recordFetch(nanoTime() - started, bitmap != null, heapBytes())
            }
        } catch (_: OutOfMemoryError) {
            recorder.recordOom(heapBytes())
            null
        } catch (_: Exception) {
            recorder.recordFetch(nanoTime() - started, false, heapBytes())
            null
        }
    }
}

class FrameIntervalAdapter(
    private val recorder: ThumbnailBenchmarkRecorder,
    private val maximumSamples: Int,
    private val heapBytes: () -> Long = ::usedHeapBytes,
) {
    private var previousFrameNanos: Long? = null
    private var sampleCount = 0

    init {
        require(maximumSamples > 0)
    }

    fun onFrame(frameTimeNanos: Long): Boolean {
        if (sampleCount >= maximumSamples) return false
        previousFrameNanos?.let {
            recorder.recordFrame(frameTimeNanos - it, heapBytes())
            sampleCount += 1
        }
        previousFrameNanos = frameTimeNanos
        return sampleCount < maximumSamples
    }
}

fun usedHeapBytes(): Long {
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
}
