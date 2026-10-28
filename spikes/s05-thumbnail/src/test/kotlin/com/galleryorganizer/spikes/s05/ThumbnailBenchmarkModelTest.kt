package com.galleryorganizer.spikes.s05

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThumbnailBenchmarkModelTest {
    private val budget = ThumbnailBenchmarkBudget(
        minimumFrameSamples = 120,
        minimumFetchSamples = 200,
        maximumPeakHeapBytes = 300L * 1024 * 1024,
        maximumFrameP95Millis = 32,
        maximumFetchP95Millis = 50,
        maximumFailedFetches = 0,
    )

    @Test
    fun `complete run inside every budget passes`() {
        assertEquals(
            S05Decision.PASS,
            ThumbnailBenchmarkClassifier.classify(metrics(), budget).decision,
        )
    }

    @Test
    fun `oom always fails even when run is incomplete`() {
        val result = ThumbnailBenchmarkClassifier.classify(
            metrics(runCompleted = false, oomObserved = true),
            budget,
        )

        assertEquals(S05Decision.FAIL, result.decision)
        assertEquals(listOf(S05Violation.OUT_OF_MEMORY), result.violations)
    }

    @Test
    fun `incomplete or undersampled run stays pending`() {
        listOf(
            metrics(runCompleted = false),
            metrics(frameSampleCount = 119),
            metrics(fetchSampleCount = 199),
        ).forEach {
            assertEquals(
                S05Decision.PENDING,
                ThumbnailBenchmarkClassifier.classify(it, budget).decision,
            )
        }
    }

    @Test
    fun `every exceeded budget is reported deterministically`() {
        val result = ThumbnailBenchmarkClassifier.classify(
            metrics(
                failedFetchCount = 1,
                peakHeapBytes = budget.maximumPeakHeapBytes + 1,
                frameP95Millis = budget.maximumFrameP95Millis + 1,
                fetchP95Millis = budget.maximumFetchP95Millis + 1,
            ),
            budget,
        )

        assertEquals(S05Decision.FAIL, result.decision)
        assertEquals(
            listOf(
                S05Violation.FETCH_FAILURE_BUDGET,
                S05Violation.PEAK_HEAP_BUDGET,
                S05Violation.FRAME_P95_BUDGET,
                S05Violation.FETCH_P95_BUDGET,
            ),
            result.violations,
        )
    }

    @Test
    fun `malformed metrics are invalid rather than passing`() {
        listOf(
            metrics(frameSampleCount = -1),
            metrics(fetchSampleCount = 1, failedFetchCount = 2),
            metrics(peakHeapBytes = -1),
        ).forEach {
            assertEquals(
                S05Decision.INVALID,
                ThumbnailBenchmarkClassifier.classify(it, budget).decision,
            )
        }
    }

    @Test
    fun `fallback reduces prefetch then cache then cell edge`() {
        val limits = ThumbnailTuningLimits(
            minimumMemoryCacheBytes = 32L * 1024 * 1024,
            minimumCellEdgePx = 96,
        )
        val initial = ThumbnailTuning(
            prefetchDistance = 2,
            memoryCacheBytes = 128L * 1024 * 1024,
            cellEdgePx = 192,
        )
        val noPrefetch = initial.copy(prefetchDistance = 0)
        val minimumCache = noPrefetch.copy(memoryCacheBytes = limits.minimumMemoryCacheBytes)
        val minimumCell = minimumCache.copy(cellEdgePx = limits.minimumCellEdgePx)

        assertEquals(initial.copy(prefetchDistance = 1), ThumbnailFallbackPlanner.next(initial, limits))
        assertEquals(
            noPrefetch.copy(memoryCacheBytes = 64L * 1024 * 1024),
            ThumbnailFallbackPlanner.next(noPrefetch, limits),
        )
        assertEquals(
            minimumCache.copy(cellEdgePx = 144),
            ThumbnailFallbackPlanner.next(minimumCache, limits),
        )
        assertNull(ThumbnailFallbackPlanner.next(minimumCell, limits))
    }

    private fun metrics(
        frameSampleCount: Int = 120,
        fetchSampleCount: Int = 200,
        failedFetchCount: Int = 0,
        peakHeapBytes: Long = 256L * 1024 * 1024,
        frameP95Millis: Long = 24,
        fetchP95Millis: Long = 40,
        oomObserved: Boolean = false,
        runCompleted: Boolean = true,
    ) = ThumbnailBenchmarkMetrics(
        frameSampleCount,
        fetchSampleCount,
        failedFetchCount,
        peakHeapBytes,
        frameP95Millis,
        fetchP95Millis,
        oomObserved,
        runCompleted,
    )
}
