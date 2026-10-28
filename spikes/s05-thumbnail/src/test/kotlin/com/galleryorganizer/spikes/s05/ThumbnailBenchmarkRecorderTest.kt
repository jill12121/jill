package com.galleryorganizer.spikes.s05

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThumbnailBenchmarkRecorderTest {
    @Test
    fun `nearest-rank p95 is stable for unsorted samples`() {
        val samples = (1L..20L).toList().reversed()

        assertEquals(19L, NearestRankPercentile.p95(samples))
        assertEquals(7L, NearestRankPercentile.p95(listOf(7L)))
        assertEquals(0L, NearestRankPercentile.p95(emptyList()))
    }

    @Test
    fun `snapshot aggregates samples failures peak heap and rounded p95`() {
        val recorder = ThumbnailBenchmarkRecorder()
        recorder.recordFrame(durationNanos = 16_000_001, observedHeapBytes = 100)
        recorder.recordFrame(durationNanos = 31_000_000, observedHeapBytes = 120)
        recorder.recordFetch(
            durationNanos = 2_000_001,
            succeeded = true,
            observedHeapBytes = 110,
        )
        recorder.recordFetch(
            durationNanos = 8_000_000,
            succeeded = false,
            observedHeapBytes = 130,
        )
        recorder.complete()

        assertEquals(
            ThumbnailBenchmarkMetrics(
                frameSampleCount = 2,
                fetchSampleCount = 2,
                failedFetchCount = 1,
                peakHeapBytes = 130,
                frameP95Millis = 31,
                fetchP95Millis = 8,
                oomObserved = false,
                runCompleted = true,
            ),
            recorder.snapshot(),
        )
    }

    @Test
    fun `oom is sticky and pending snapshot remains incomplete`() {
        val recorder = ThumbnailBenchmarkRecorder()
        recorder.recordOom(observedHeapBytes = 256)

        val pending = recorder.snapshot()
        assertTrue(pending.oomObserved)
        assertFalse(pending.runCompleted)
        assertEquals(256, pending.peakHeapBytes)

        recorder.complete()
        assertTrue(recorder.snapshot().runCompleted)
    }

    @Test
    fun `negative samples and recording after completion are rejected`() {
        val recorder = ThumbnailBenchmarkRecorder()

        assertFailsWith<IllegalArgumentException> {
            recorder.recordFrame(-1, observedHeapBytes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            recorder.recordFetch(0, succeeded = true, observedHeapBytes = -1)
        }

        recorder.complete()
        assertFailsWith<IllegalStateException> {
            recorder.recordOom(observedHeapBytes = 1)
        }
    }
}
