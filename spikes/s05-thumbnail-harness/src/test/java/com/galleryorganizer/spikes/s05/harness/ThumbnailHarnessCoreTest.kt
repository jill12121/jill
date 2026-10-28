package com.galleryorganizer.spikes.s05.harness

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
}
