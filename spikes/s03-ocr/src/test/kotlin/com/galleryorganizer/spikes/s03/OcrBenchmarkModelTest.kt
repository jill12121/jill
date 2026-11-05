package com.galleryorganizer.spikes.s03

import kotlin.test.Test
import kotlin.test.assertEquals

class OcrBenchmarkModelTest {
    private val budget = OcrBenchmarkBudget(
        minimumSampleCount = 100,
        minimumPrecisionPermille = 950,
        minimumRecallPermille = 900,
        maximumP95Millis = 250,
        maximumFailedSamples = 0,
    )

    @Test
    fun `complete bundled offline run inside every budget passes`() {
        val result = OcrBenchmarkClassifier.classify(metrics(), budget)

        assertEquals(S03Decision.PASS, result.decision)
        assertEquals(950, result.precisionPermille)
        assertEquals(950, result.recallPermille)
        assertEquals(emptyList(), result.violations)
    }

    @Test
    fun `non bundled or network capable run fails closed before completion`() {
        val result = OcrBenchmarkClassifier.classify(
            metrics(
                runCompleted = false,
                modelDelivery = OcrModelDelivery.REMOTE,
                internetPermissionDeclared = true,
            ),
            budget,
        )

        assertEquals(S03Decision.FAIL, result.decision)
        assertEquals(
            listOf(
                S03Violation.MODEL_NOT_BUNDLED,
                S03Violation.INTERNET_PERMISSION_DECLARED,
            ),
            result.violations,
        )
    }

    @Test
    fun `incomplete or undersampled run stays pending`() {
        val incomplete = OcrBenchmarkClassifier.classify(
            metrics(sampleCount = 99, runCompleted = false),
            budget,
        )
        val empty = OcrBenchmarkClassifier.classify(
            metrics(
                sampleCount = 0,
                expectedTokenCount = 0,
                recognizedTokenCount = 0,
                matchedTokenCount = 0,
                runCompleted = false,
            ),
            budget,
        )

        assertEquals(S03Decision.PENDING, incomplete.decision)
        assertEquals(
            listOf(
                S03Violation.RUN_INCOMPLETE,
                S03Violation.INSUFFICIENT_SAMPLES,
            ),
            incomplete.violations,
        )
        assertEquals(S03Decision.PENDING, empty.decision)
    }

    @Test
    fun `every exceeded quality budget is reported deterministically`() {
        val result = OcrBenchmarkClassifier.classify(
            metrics(
                expectedTokenCount = 100,
                recognizedTokenCount = 100,
                matchedTokenCount = 80,
                failedSampleCount = 1,
                p95Millis = 251,
            ),
            budget,
        )

        assertEquals(S03Decision.FAIL, result.decision)
        assertEquals(
            listOf(
                S03Violation.SAMPLE_FAILURE_BUDGET,
                S03Violation.PRECISION_BUDGET,
                S03Violation.RECALL_BUDGET,
                S03Violation.P95_BUDGET,
            ),
            result.violations,
        )
    }

    @Test
    fun `zero recognized tokens is a measured failure rather than invalid`() {
        val result = OcrBenchmarkClassifier.classify(
            metrics(recognizedTokenCount = 0, matchedTokenCount = 0),
            budget,
        )

        assertEquals(S03Decision.FAIL, result.decision)
        assertEquals(0, result.precisionPermille)
        assertEquals(0, result.recallPermille)
    }

    @Test
    fun `impossible counts are invalid rather than passing`() {
        listOf(
            metrics(sampleCount = -1),
            metrics(expectedTokenCount = 0),
            metrics(recognizedTokenCount = 90, matchedTokenCount = 95),
            metrics(expectedTokenCount = 90, matchedTokenCount = 95),
            metrics(sampleCount = 1, failedSampleCount = 2),
            metrics(p95Millis = -1),
        ).forEach { invalid ->
            val result = OcrBenchmarkClassifier.classify(invalid, budget)
            assertEquals(S03Decision.INVALID, result.decision)
            assertEquals(listOf(S03Violation.INVALID_METRICS), result.violations)
        }
    }

    private fun metrics(
        sampleCount: Int = 100,
        expectedTokenCount: Int = 100,
        recognizedTokenCount: Int = 100,
        matchedTokenCount: Int = 95,
        failedSampleCount: Int = 0,
        p95Millis: Long = 200,
        runCompleted: Boolean = true,
        modelDelivery: OcrModelDelivery = OcrModelDelivery.BUNDLED,
        internetPermissionDeclared: Boolean = false,
    ) = OcrBenchmarkMetrics(
        sampleCount,
        expectedTokenCount,
        recognizedTokenCount,
        matchedTokenCount,
        failedSampleCount,
        p95Millis,
        runCompleted,
        modelDelivery,
        internetPermissionDeclared,
    )
}
