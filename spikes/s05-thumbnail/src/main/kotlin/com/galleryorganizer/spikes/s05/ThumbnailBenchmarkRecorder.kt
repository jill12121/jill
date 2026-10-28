package com.galleryorganizer.spikes.s05

object NearestRankPercentile {
    fun p95(samples: List<Long>): Long {
        if (samples.isEmpty()) return 0
        require(samples.all { it >= 0 })
        val rank = ((samples.size.toLong() * 95 + 99) / 100).toInt()
        return samples.sorted()[rank - 1]
    }
}

class ThumbnailBenchmarkRecorder {
    private val frameDurationsNanos = mutableListOf<Long>()
    private val fetchDurationsNanos = mutableListOf<Long>()
    private var failedFetchCount = 0
    private var peakHeapBytes = 0L
    private var oomObserved = false
    private var runCompleted = false

    @Synchronized
    fun recordFrame(durationNanos: Long, observedHeapBytes: Long) {
        requireSample(durationNanos, observedHeapBytes)
        ensureActive()
        frameDurationsNanos += durationNanos
        recordPeakHeap(observedHeapBytes)
    }

    @Synchronized
    fun recordFetch(
        durationNanos: Long,
        succeeded: Boolean,
        observedHeapBytes: Long,
    ) {
        requireSample(durationNanos, observedHeapBytes)
        ensureActive()
        fetchDurationsNanos += durationNanos
        if (!succeeded) failedFetchCount += 1
        recordPeakHeap(observedHeapBytes)
    }

    @Synchronized
    fun recordOom(observedHeapBytes: Long) {
        require(observedHeapBytes >= 0)
        ensureActive()
        oomObserved = true
        recordPeakHeap(observedHeapBytes)
    }

    @Synchronized
    fun complete() {
        runCompleted = true
    }

    @Synchronized
    fun snapshot(): ThumbnailBenchmarkMetrics = ThumbnailBenchmarkMetrics(
        frameSampleCount = frameDurationsNanos.size,
        fetchSampleCount = fetchDurationsNanos.size,
        failedFetchCount = failedFetchCount,
        peakHeapBytes = peakHeapBytes,
        frameP95Millis = nanosToRoundedUpMillis(
            NearestRankPercentile.p95(frameDurationsNanos),
        ),
        fetchP95Millis = nanosToRoundedUpMillis(
            NearestRankPercentile.p95(fetchDurationsNanos),
        ),
        oomObserved = oomObserved,
        runCompleted = runCompleted,
    )

    private fun requireSample(durationNanos: Long, observedHeapBytes: Long) {
        require(durationNanos >= 0)
        require(observedHeapBytes >= 0)
    }

    private fun ensureActive() {
        check(!runCompleted) { "Benchmark recording is already complete" }
    }

    private fun recordPeakHeap(observedHeapBytes: Long) {
        peakHeapBytes = maxOf(peakHeapBytes, observedHeapBytes)
    }

    private fun nanosToRoundedUpMillis(value: Long): Long =
        value / NANOS_PER_MILLI + if (value % NANOS_PER_MILLI == 0L) 0 else 1

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
