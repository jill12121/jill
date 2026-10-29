package com.galleryorganizer.spikes.s05.harness

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.galleryorganizer.spikes.s05.ThumbnailBenchmarkRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThumbnailHarnessCoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `fixture store creates bounded deterministic app-private png corpus`() {
        val store = AppPrivateFixtureStore(context)
        val spec = FixtureSpec(count = 4, edgePx = 48, seed = 7)

        val first = store.prepare(spec)
        val second = store.prepare(spec)

        assertEquals("filesDir/s05-thumbnail-fixtures", second.storage)
        assertEquals(4, second.count)
        assertEquals(48, second.edgePx)
        assertEquals(first.sha256, second.sha256)
        assertTrue(second.totalBytes > 0)
        assertTrue(store.files.all { it.canonicalPath.startsWith(context.filesDir.canonicalPath) })
        assertTrue(store.files.all { it.extension == "png" && it.isFile })
    }

    @Test
    fun `fetch adapter records successful and failed decodes`() {
        val store = AppPrivateFixtureStore(context)
        store.prepare(FixtureSpec(count = 1, edgePx = 32, seed = 11))
        val recorder = ThumbnailBenchmarkRecorder()
        val fetcher = AppPrivateThumbnailFetcher(recorder)

        assertNotNull(fetcher.fetch(store.files.single()))
        assertEquals(null, fetcher.fetch(store.files.single().resolveSibling("missing.png")))

        val metrics = recorder.snapshot()
        assertEquals(2, metrics.fetchSampleCount)
        assertEquals(1, metrics.failedFetchCount)
        assertFalse(metrics.oomObserved)
    }

    @Test
    fun `late fetch after run stops is ignored without touching completed recorder`() {
        val store = AppPrivateFixtureStore(context)
        store.prepare(FixtureSpec(count = 1, edgePx = 32, seed = 13))
        val recorder = ThumbnailBenchmarkRecorder()
        var active = true
        val fetcher = AppPrivateThumbnailFetcher(recorder, active = { active })

        assertNotNull(fetcher.fetch(store.files.single()))
        active = false
        recorder.complete()

        assertEquals(null, fetcher.fetch(store.files.single()))
        val metrics = recorder.snapshot()
        assertEquals(1, metrics.fetchSampleCount)
        assertTrue(metrics.runCompleted)
    }

    @Test
    fun `frame adapter ignores origin and stops at configured sample bound`() {
        val recorder = ThumbnailBenchmarkRecorder()
        val adapter = FrameIntervalAdapter(
            recorder = recorder,
            maximumSamples = 2,
            heapBytes = { 64L },
        )

        assertTrue(adapter.onFrame(1_000_000))
        assertTrue(adapter.onFrame(17_000_000))
        assertFalse(adapter.onFrame(34_000_000))
        assertFalse(adapter.onFrame(51_000_000))

        val metrics = recorder.snapshot()
        assertEquals(2, metrics.frameSampleCount)
        assertEquals(64, metrics.peakHeapBytes)
    }

    @Test
    fun `evidence json binds device provenance and never leaks raw fingerprint`() {
        val rawFingerprint = "brand/model/device:14/UQ1A/1234567:user/release-keys"
        val store = AppPrivateFixtureStore(context)
        val fixture = store.prepare(FixtureSpec(count = 2, edgePx = 32, seed = 3))
        val device = deviceProvenance(
            manufacturer = "iQOO",
            model = "V2304A",
            apiLevel = 34,
            release = "14",
            securityPatch = "2024-06-01",
            buildFingerprint = rawFingerprint,
        )

        val json = BenchmarkEvidence(
            device = device,
            fixture = fixture,
            frameSamples = 360,
            fetchSamples = 637,
            failedFetches = 0,
            peakHeapBytes = 4096L,
            frameP95Millis = 16L,
            fetchP95Millis = 6L,
            oomObserved = false,
            decision = "PASS",
            violations = emptyList(),
        ).toJson()
        val text = json.toString()

        assertEquals(S05_HARNESS_SCHEMA, json.getInt("schema"))
        assertEquals("iQOO", json.getString("deviceManufacturer"))
        assertEquals("V2304A", json.getString("deviceModel"))
        assertEquals(34, json.getInt("deviceApiLevel"))
        assertEquals("14", json.getString("deviceRelease"))
        assertEquals("2024-06-01", json.getString("deviceSecurityPatch"))
        assertEquals(sha256Hex(rawFingerprint), json.getString("deviceFingerprintSha256"))
        assertEquals(64, json.getString("deviceFingerprintSha256").length)
        assertEquals(360, json.getInt("frameSamples"))
        assertEquals("PASS", json.getString("decision"))
        assertFalse(text.contains(rawFingerprint))
        assertFalse(text.contains("release-keys"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `device provenance rejects a fingerprint hash that is not sha256 hex`() {
        DeviceProvenance(
            manufacturer = "iQOO",
            model = "V2304A",
            apiLevel = 34,
            release = "14",
            securityPatch = "2024-06-01",
            fingerprintSha256 = "raw-fingerprint",
        )
    }

    @Test
    fun `result clipboard carries the exported json without storage access`() {
        val json = """{"schema":2,"decision":"PASS"}"""

        assertTrue(ResultClipboard(context).copy(json))

        val manager = context.getSystemService(ClipboardManager::class.java)
        assertEquals(json, manager?.primaryClip?.getItemAt(0)?.text?.toString())
    }
}
