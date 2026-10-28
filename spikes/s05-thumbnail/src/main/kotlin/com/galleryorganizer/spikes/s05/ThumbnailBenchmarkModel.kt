package com.galleryorganizer.spikes.s05

data class ThumbnailBenchmarkBudget(
    val minimumFrameSamples: Int,
    val minimumFetchSamples: Int,
    val maximumPeakHeapBytes: Long,
    val maximumFrameP95Millis: Long,
    val maximumFetchP95Millis: Long,
    val maximumFailedFetches: Int,
) {
    init {
        require(minimumFrameSamples > 0)
        require(minimumFetchSamples > 0)
        require(maximumPeakHeapBytes > 0)
        require(maximumFrameP95Millis > 0)
        require(maximumFetchP95Millis > 0)
        require(maximumFailedFetches >= 0)
    }
}

data class ThumbnailBenchmarkMetrics(
    val frameSampleCount: Int,
    val fetchSampleCount: Int,
    val failedFetchCount: Int,
    val peakHeapBytes: Long,
    val frameP95Millis: Long,
    val fetchP95Millis: Long,
    val oomObserved: Boolean,
    val runCompleted: Boolean,
)

enum class S05Decision { PASS, FAIL, PENDING, INVALID }

enum class S05Violation {
    INVALID_METRICS,
    OUT_OF_MEMORY,
    RUN_INCOMPLETE,
    INSUFFICIENT_FRAME_SAMPLES,
    INSUFFICIENT_FETCH_SAMPLES,
    FETCH_FAILURE_BUDGET,
    PEAK_HEAP_BUDGET,
    FRAME_P95_BUDGET,
    FETCH_P95_BUDGET,
}

data class ThumbnailBenchmarkResult(
    val decision: S05Decision,
    val violations: List<S05Violation>,
)

object ThumbnailBenchmarkClassifier {
    fun classify(
        metrics: ThumbnailBenchmarkMetrics,
        budget: ThumbnailBenchmarkBudget,
    ): ThumbnailBenchmarkResult {
        if (!metrics.isValid()) {
            return ThumbnailBenchmarkResult(
                S05Decision.INVALID,
                listOf(S05Violation.INVALID_METRICS),
            )
        }
        if (metrics.oomObserved) {
            return ThumbnailBenchmarkResult(
                S05Decision.FAIL,
                listOf(S05Violation.OUT_OF_MEMORY),
            )
        }

        val pending = buildList {
            if (!metrics.runCompleted) add(S05Violation.RUN_INCOMPLETE)
            if (metrics.frameSampleCount < budget.minimumFrameSamples) {
                add(S05Violation.INSUFFICIENT_FRAME_SAMPLES)
            }
            if (metrics.fetchSampleCount < budget.minimumFetchSamples) {
                add(S05Violation.INSUFFICIENT_FETCH_SAMPLES)
            }
        }
        if (pending.isNotEmpty()) {
            return ThumbnailBenchmarkResult(S05Decision.PENDING, pending)
        }

        val exceeded = buildList {
            if (metrics.failedFetchCount > budget.maximumFailedFetches) {
                add(S05Violation.FETCH_FAILURE_BUDGET)
            }
            if (metrics.peakHeapBytes > budget.maximumPeakHeapBytes) {
                add(S05Violation.PEAK_HEAP_BUDGET)
            }
            if (metrics.frameP95Millis > budget.maximumFrameP95Millis) {
                add(S05Violation.FRAME_P95_BUDGET)
            }
            if (metrics.fetchP95Millis > budget.maximumFetchP95Millis) {
                add(S05Violation.FETCH_P95_BUDGET)
            }
        }
        return ThumbnailBenchmarkResult(
            if (exceeded.isEmpty()) S05Decision.PASS else S05Decision.FAIL,
            exceeded,
        )
    }

    private fun ThumbnailBenchmarkMetrics.isValid(): Boolean =
        frameSampleCount >= 0 &&
            fetchSampleCount >= 0 &&
            failedFetchCount in 0..fetchSampleCount &&
            peakHeapBytes >= 0 &&
            frameP95Millis >= 0 &&
            fetchP95Millis >= 0
}

data class ThumbnailTuning(
    val prefetchDistance: Int,
    val memoryCacheBytes: Long,
    val cellEdgePx: Int,
) {
    init {
        require(prefetchDistance >= 0)
        require(memoryCacheBytes > 0)
        require(cellEdgePx > 0)
    }
}

data class ThumbnailTuningLimits(
    val minimumMemoryCacheBytes: Long,
    val minimumCellEdgePx: Int,
) {
    init {
        require(minimumMemoryCacheBytes > 0)
        require(minimumCellEdgePx > 0)
    }
}

object ThumbnailFallbackPlanner {
    fun next(
        current: ThumbnailTuning,
        limits: ThumbnailTuningLimits,
    ): ThumbnailTuning? = when {
        current.prefetchDistance > 0 ->
            current.copy(prefetchDistance = current.prefetchDistance - 1)
        current.memoryCacheBytes > limits.minimumMemoryCacheBytes ->
            current.copy(
                memoryCacheBytes = maxOf(
                    limits.minimumMemoryCacheBytes,
                    current.memoryCacheBytes / 2,
                ),
            )
        current.cellEdgePx > limits.minimumCellEdgePx ->
            current.copy(
                cellEdgePx = maxOf(
                    limits.minimumCellEdgePx,
                    current.cellEdgePx * 3 / 4,
                ),
            )
        else -> null
    }
}
