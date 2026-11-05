package com.galleryorganizer.spikes.s03

private const val RATE_SCALE = 1_000

data class OcrBenchmarkBudget(
    val minimumSampleCount: Int,
    val minimumPrecisionPermille: Int,
    val minimumRecallPermille: Int,
    val maximumP95Millis: Long,
    val maximumFailedSamples: Int,
) {
    init {
        require(minimumSampleCount > 0)
        require(minimumPrecisionPermille in 0..RATE_SCALE)
        require(minimumRecallPermille in 0..RATE_SCALE)
        require(maximumP95Millis > 0)
        require(maximumFailedSamples >= 0)
    }
}

enum class OcrModelDelivery {
    BUNDLED,
    REMOTE,
    UNKNOWN,
}

data class OcrBenchmarkMetrics(
    val sampleCount: Int,
    val expectedTokenCount: Int,
    val recognizedTokenCount: Int,
    val matchedTokenCount: Int,
    val failedSampleCount: Int,
    val p95Millis: Long,
    val runCompleted: Boolean,
    val modelDelivery: OcrModelDelivery,
    val internetPermissionDeclared: Boolean,
) {
    val precisionPermille: Int
        get() = rate(matchedTokenCount, recognizedTokenCount)

    val recallPermille: Int
        get() = rate(matchedTokenCount, expectedTokenCount)

    private fun rate(numerator: Int, denominator: Int): Int =
        if (denominator == 0) {
            0
        } else {
            (numerator.toLong() * RATE_SCALE / denominator).toInt()
        }
}

enum class S03Decision {
    PASS,
    FAIL,
    PENDING,
    INVALID,
}

enum class S03Violation {
    INVALID_METRICS,
    MODEL_NOT_BUNDLED,
    INTERNET_PERMISSION_DECLARED,
    RUN_INCOMPLETE,
    INSUFFICIENT_SAMPLES,
    SAMPLE_FAILURE_BUDGET,
    PRECISION_BUDGET,
    RECALL_BUDGET,
    P95_BUDGET,
}

data class OcrBenchmarkResult(
    val decision: S03Decision,
    val precisionPermille: Int?,
    val recallPermille: Int?,
    val violations: List<S03Violation>,
)

object OcrBenchmarkClassifier {
    fun classify(
        metrics: OcrBenchmarkMetrics,
        budget: OcrBenchmarkBudget,
    ): OcrBenchmarkResult {
        if (!metrics.isValid()) {
            return result(S03Decision.INVALID, null, null, S03Violation.INVALID_METRICS)
        }

        val offlineViolations = buildList {
            if (metrics.modelDelivery != OcrModelDelivery.BUNDLED) {
                add(S03Violation.MODEL_NOT_BUNDLED)
            }
            if (metrics.internetPermissionDeclared) {
                add(S03Violation.INTERNET_PERMISSION_DECLARED)
            }
        }
        if (offlineViolations.isNotEmpty()) {
            return OcrBenchmarkResult(
                S03Decision.FAIL,
                metrics.precisionPermille,
                metrics.recallPermille,
                offlineViolations,
            )
        }

        val pending = buildList {
            if (!metrics.runCompleted) add(S03Violation.RUN_INCOMPLETE)
            if (metrics.sampleCount < budget.minimumSampleCount) {
                add(S03Violation.INSUFFICIENT_SAMPLES)
            }
        }
        if (pending.isNotEmpty()) {
            return OcrBenchmarkResult(
                S03Decision.PENDING,
                metrics.precisionPermille,
                metrics.recallPermille,
                pending,
            )
        }

        val exceeded = buildList {
            if (metrics.failedSampleCount > budget.maximumFailedSamples) {
                add(S03Violation.SAMPLE_FAILURE_BUDGET)
            }
            if (metrics.precisionPermille < budget.minimumPrecisionPermille) {
                add(S03Violation.PRECISION_BUDGET)
            }
            if (metrics.recallPermille < budget.minimumRecallPermille) {
                add(S03Violation.RECALL_BUDGET)
            }
            if (metrics.p95Millis > budget.maximumP95Millis) {
                add(S03Violation.P95_BUDGET)
            }
        }
        return OcrBenchmarkResult(
            if (exceeded.isEmpty()) S03Decision.PASS else S03Decision.FAIL,
            metrics.precisionPermille,
            metrics.recallPermille,
            exceeded,
        )
    }

    private fun OcrBenchmarkMetrics.isValid(): Boolean =
        sampleCount >= 0 &&
            expectedTokenCount >= 0 &&
            recognizedTokenCount >= 0 &&
            matchedTokenCount in 0..expectedTokenCount &&
            matchedTokenCount <= recognizedTokenCount &&
            failedSampleCount in 0..sampleCount &&
            p95Millis >= 0 &&
            if (sampleCount == 0) {
                expectedTokenCount == 0 &&
                    recognizedTokenCount == 0 &&
                    matchedTokenCount == 0
            } else {
                expectedTokenCount > 0
            }

    private fun result(
        decision: S03Decision,
        precisionPermille: Int?,
        recallPermille: Int?,
        vararg violations: S03Violation,
    ) = OcrBenchmarkResult(decision, precisionPermille, recallPermille, violations.toList())
}
