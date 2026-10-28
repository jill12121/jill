package com.galleryorganizer.spikes.s05.harness

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import com.galleryorganizer.spikes.s05.ThumbnailBenchmarkRecorder
import org.json.JSONObject
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

const val S05_HARNESS_SCHEMA = 2

data class DeviceProvenance(
    val manufacturer: String,
    val model: String,
    val apiLevel: Int,
    val release: String,
    val securityPatch: String,
    val fingerprintSha256: String,
) {
    init {
        require(manufacturer.isNotBlank())
        require(model.isNotBlank())
        require(apiLevel > 0)
        require(release.isNotBlank())
        require(securityPatch.isNotBlank())
        require(fingerprintSha256.matches(FINGERPRINT_HASH))
    }

    private companion object {
        val FINGERPRINT_HASH = Regex("[0-9a-f]{64}")
    }
}

fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

fun deviceProvenance(
    manufacturer: String,
    model: String,
    apiLevel: Int,
    release: String,
    securityPatch: String,
    buildFingerprint: String,
): DeviceProvenance = DeviceProvenance(
    manufacturer = manufacturer,
    model = model,
    apiLevel = apiLevel,
    release = release,
    securityPatch = securityPatch,
    fingerprintSha256 = sha256Hex(buildFingerprint),
)

fun readDeviceProvenance(): DeviceProvenance = deviceProvenance(
    manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "unknown" },
    model = Build.MODEL.orEmpty().ifBlank { "unknown" },
    apiLevel = Build.VERSION.SDK_INT,
    release = Build.VERSION.RELEASE.orEmpty().ifBlank { "unknown" },
    securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty().ifBlank { "unknown" },
    buildFingerprint = Build.FINGERPRINT.orEmpty(),
)

data class BenchmarkEvidence(
    val device: DeviceProvenance,
    val fixture: FixtureProvenance,
    val frameSamples: Int,
    val fetchSamples: Int,
    val failedFetches: Int,
    val peakHeapBytes: Long,
    val frameP95Millis: Long,
    val fetchP95Millis: Long,
    val oomObserved: Boolean,
    val decision: String,
    val violations: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", S05_HARNESS_SCHEMA)
        .put("deviceManufacturer", device.manufacturer)
        .put("deviceModel", device.model)
        .put("deviceApiLevel", device.apiLevel)
        .put("deviceRelease", device.release)
        .put("deviceSecurityPatch", device.securityPatch)
        .put("deviceFingerprintSha256", device.fingerprintSha256)
        .put("storage", fixture.storage)
        .put("fixtureCount", fixture.count)
        .put("fixtureEdgePx", fixture.edgePx)
        .put("fixtureSeed", fixture.seed)
        .put("fixtureBytes", fixture.totalBytes)
        .put("fixtureSha256", fixture.sha256)
        .put("frameSamples", frameSamples)
        .put("fetchSamples", fetchSamples)
        .put("failedFetches", failedFetches)
        .put("peakHeapBytes", peakHeapBytes)
        .put("frameP95Millis", frameP95Millis)
        .put("fetchP95Millis", fetchP95Millis)
        .put("oomObserved", oomObserved)
        .put("decision", decision)
        .put("violations", violations.joinToString(","))
}

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
    private val active: () -> Boolean = { true },
) {
    fun fetch(file: File): Bitmap? {
        if (!active()) return null
        val started = nanoTime()
        val bitmap = try {
            file.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }
        } catch (_: OutOfMemoryError) {
            if (active()) recorder.recordOom(heapBytes())
            return null
        } catch (_: Exception) {
            null
        }
        if (!active()) {
            bitmap?.recycle()
            return null
        }
        recorder.recordFetch(nanoTime() - started, bitmap != null, heapBytes())
        return bitmap
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
